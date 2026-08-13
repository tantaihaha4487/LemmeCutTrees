package net.thanachot.lemmecuttrees;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.thanachot.lemmecuttrees.config.ConfigManager;
import net.thanachot.lemmecuttrees.config.ModConfig;
import net.thanachot.lemmecuttrees.core.CuttingTiming;
import net.thanachot.lemmecuttrees.core.DurabilityBudget;
import net.thanachot.lemmecuttrees.core.GridPos;
import net.thanachot.lemmecuttrees.core.TreeDetector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MinecraftTreeService {
    private final ConfigManager configs;
    private final TreeDetector detector = new TreeDetector();
    private final Map<UUID, PendingCapture> captures = new HashMap<>();
    private final Map<UUID, List<Operation>> operations = new HashMap<>();
    private final Set<UUID> internalBreaks = new HashSet<>();

    public MinecraftTreeService(ConfigManager configs) {
        this.configs = configs;
    }

    public void register() {
        PlayerBlockBreakEvents.BEFORE.register(this::beforeBreak);
        PlayerBlockBreakEvents.AFTER.register(this::afterBreak);
        PlayerBlockBreakEvents.CANCELED.register((level, player, position, state, blockEntity) ->
                captures.remove(player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    private boolean beforeBreak(Level level, Player player, BlockPos position, BlockState state,
                                net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) return true;
        UUID playerId = serverPlayer.getUUID();
        if (internalBreaks.contains(playerId)) return true;
        captures.remove(playerId);
        ModConfig config = configs.current();
        if ((config.requireShift() && !serverPlayer.isShiftKeyDown()) ||
                !isAllowedAxe(serverPlayer, config)) return true;

        GridPos origin = fromMinecraft(position);
        Optional<TreeDetector.DetectedTree> detected = detector.detect(pos -> node(serverLevel, pos), origin, config);
        detected.ifPresent(tree -> {
            if (hasDurabilityFor(playerId, serverPlayer, tree.logs().size())) {
                captures.put(playerId, new PendingCapture(serverLevel.dimension(), position.immutable(), state, tree));
            } else {
                int reserved = reservedLogs(playerId);
                int available = remainingDurability(serverPlayer.getMainHandItem());
                serverPlayer.sendSystemMessage(Component.literal("Not enough axe durability: this tree needs " +
                        tree.logs().size() + ", " + reserved + " already reserved, " + available + " remaining"), true);
            }
        });
        return true;
    }

    private void afterBreak(Level level, Player player, BlockPos position, BlockState state,
                            net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) return;
        PendingCapture capture = captures.remove(serverPlayer.getUUID());
        if (capture == null || !capture.dimension().equals(serverLevel.dimension()) ||
                !capture.origin().equals(position) || !capture.initialState().equals(state)) return;

        List<GridPos> remainingLogs = capture.tree().logs().stream()
                .filter(log -> !log.equals(fromMinecraft(position))).toList();
        Operation operation = new Operation(serverLevel.dimension(), capture.tree(), remainingLogs, 0, 0, false, 0);
        operations.computeIfAbsent(serverPlayer.getUUID(), ignored -> new ArrayList<>())
                .add(scheduleNext(serverPlayer, operation, serverLevel.getServer().getTickCount()));
    }

    private void tick(MinecraftServer server) {
        int tick = server.getTickCount();
        Iterator<Map.Entry<UUID, List<Operation>>> players = operations.entrySet().iterator();
        while (players.hasNext()) {
            Map.Entry<UUID, List<Operation>> entry = players.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ListIterator<Operation> iterator = entry.getValue().listIterator();
            while (iterator.hasNext()) {
                Operation operation = iterator.next();
                if (!validPlayer(player, operation, server)) {
                    iterator.remove();
                    continue;
                }
                if (tick < operation.dueTick()) continue;
                Operation advanced = advance(player, operation, tick);
                if (advanced == null) iterator.remove();
                else iterator.set(advanced);
            }
            if (entry.getValue().isEmpty()) players.remove();
        }
    }

    private Operation advance(ServerPlayer player, Operation operation, int tick) {
        ServerLevel level = player.level();
        if (!operation.leafPhase()) return breakNextLog(player, level, operation, tick);

        int leafIndex = operation.leafIndex();
        if (leafIndex >= operation.tree().leaves().size()) return null;
        GridPos leaf = operation.tree().leaves().get(leafIndex);
        if (matchesExpected(level, leaf, operation.tree()) && leafAllowed(level, leaf) &&
                level.mayInteract(player, toMinecraft(leaf))) {
            destroyQueuedBlock(player, toMinecraft(leaf), true);
        }
        if (leafIndex + 1 >= operation.tree().leaves().size()) return null;
        return new Operation(operation.dimension(), operation.tree(), operation.logs(), operation.logIndex(),
                operation.dueTick(), true, leafIndex + 1);
    }

    private Operation breakNextLog(ServerPlayer player, ServerLevel level, Operation operation, int tick) {
        if (operation.logIndex() >= operation.logs().size()) return beginLeaves(operation, tick);
        GridPos position = operation.logs().get(operation.logIndex());
        int nextIndex = operation.logIndex() + 1;
        BlockPos minecraftPosition = toMinecraft(position);
        if (matchesExpected(level, position, operation.tree()) && level.mayInteract(player, minecraftPosition)) {
            destroyQueuedBlock(player, minecraftPosition, false);
        }
        Operation advanced = new Operation(operation.dimension(), operation.tree(), operation.logs(), nextIndex,
                tick, false, 0);
        if (nextIndex >= operation.logs().size()) return beginLeaves(advanced, tick);
        return scheduleNext(player, advanced, tick);
    }

    private Operation scheduleNext(ServerPlayer player, Operation operation, int tick) {
        if (operation.logs().isEmpty() || operation.logIndex() >= operation.logs().size()) return beginLeaves(operation, tick);
        GridPos next = operation.logs().get(operation.logIndex());
        BlockPos position = toMinecraft(next);
        BlockState state = player.level().getBlockState(position);
        float progress = state.getDestroyProgress(player, player.level(), position);
        int delay = CuttingTiming.ticks(progress, configs.current().cuttingSpeed());
        return new Operation(operation.dimension(), operation.tree(), operation.logs(), operation.logIndex(),
                tick + delay, false, 0);
    }

    private Operation beginLeaves(Operation operation, int tick) {
        if (!configs.current().clearLeaves()) {
            return new Operation(operation.dimension(), operation.tree(), operation.logs(), operation.logs().size(),
                    tick, true, operation.tree().leaves().size());
        }
        return new Operation(operation.dimension(), operation.tree(), operation.logs(), operation.logs().size(),
                tick, true, 0);
    }

    private boolean validPlayer(ServerPlayer player, Operation operation, MinecraftServer server) {
        return player != null && player.isAlive() && server.getPlayerList().getPlayer(player.getUUID()) == player &&
                player.level().dimension().equals(operation.dimension()) && isAllowedAxe(player, configs.current());
    }

    private static boolean matchesExpected(ServerLevel level, GridPos position, TreeDetector.DetectedTree tree) {
        return TreeDetector.stillExpected(tree, position, blockId(level.getBlockState(toMinecraft(position))));
    }

    private boolean leafAllowed(ServerLevel level, GridPos position) {
        BlockState state = level.getBlockState(toMinecraft(position));
        return configs.current().detection().includePlayerPlacedLeaves() ||
                !state.hasProperty(LeavesBlock.PERSISTENT) || !state.getValue(LeavesBlock.PERSISTENT);
    }

    private static void breakLeafWithoutDurability(ServerPlayer player, BlockPos position) {
        ItemStack before = player.getMainHandItem().copy();
        int damage = player.getMainHandItem().getDamageValue();
        player.gameMode.destroyBlock(position);
        ItemStack after = player.getMainHandItem();
        if (after.isEmpty() || !ItemStack.isSameItemSameComponents(before, after)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, before);
        } else {
            after.setDamageValue(damage);
        }
    }

    private void destroyQueuedBlock(ServerPlayer player, BlockPos position, boolean preserveDurability) {
        UUID playerId = player.getUUID();
        internalBreaks.add(playerId);
        try {
            if (preserveDurability) breakLeafWithoutDurability(player, position);
            else player.gameMode.destroyBlock(position);
        } finally {
            internalBreaks.remove(playerId);
        }
    }

    private boolean hasDurabilityFor(UUID playerId, ServerPlayer player, int newTreeLogs) {
        if (player.isCreative()) return true;
        ItemStack axe = player.getMainHandItem();
        if (!axe.isDamageableItem()) return axe.getMaxDamage() > 0;
        return DurabilityBudget.canReserve(remainingDurability(axe), reservedLogs(playerId), newTreeLogs);
    }

    private int reservedLogs(UUID playerId) {
        return operations.getOrDefault(playerId, List.of()).stream().mapToInt(Operation::remainingLogs).sum();
    }

    private static int remainingDurability(ItemStack stack) {
        return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    private static boolean isAllowedAxe(ServerPlayer player, ModConfig config) {
        String id = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString();
        return config.allowedAxes().contains(id);
    }

    private static TreeDetector.Node node(ServerLevel level, GridPos position) {
        BlockState state = level.getBlockState(toMinecraft(position));
        TreeDetector.Node.Axis axis = TreeDetector.Node.Axis.NONE;
        if (state.hasProperty(RotatedPillarBlock.AXIS)) {
            Direction.Axis value = state.getValue(RotatedPillarBlock.AXIS);
            axis = switch (value) {
                case X -> TreeDetector.Node.Axis.X;
                case Y -> TreeDetector.Node.Axis.Y;
                case Z -> TreeDetector.Node.Axis.Z;
            };
        }
        boolean persistent = state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT);
        return new TreeDetector.Node(blockId(state), axis, persistent);
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static GridPos fromMinecraft(BlockPos position) {
        return new GridPos(position.getX(), position.getY(), position.getZ());
    }

    private static BlockPos toMinecraft(GridPos position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }

    private void clear() {
        captures.clear();
        operations.clear();
        internalBreaks.clear();
    }

    private record PendingCapture(ResourceKey<Level> dimension, BlockPos origin, BlockState initialState,
                                  TreeDetector.DetectedTree tree) {}

    private record Operation(ResourceKey<Level> dimension, TreeDetector.DetectedTree tree, List<GridPos> logs,
                             int logIndex, int dueTick, boolean leafPhase, int leafIndex) {
        int remainingLogs() {
            return leafPhase ? 0 : Math.max(0, logs.size() - logIndex);
        }
    }
}
