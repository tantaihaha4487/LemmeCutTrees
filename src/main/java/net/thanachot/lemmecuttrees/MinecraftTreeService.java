package net.thanachot.lemmecuttrees;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.core.Direction;
import net.thanachot.lemmecuttrees.config.ConfigManager;
import net.thanachot.lemmecuttrees.config.ModConfig;
import net.thanachot.lemmecuttrees.core.CuttingTiming;
import net.thanachot.lemmecuttrees.core.GridPos;
import net.thanachot.lemmecuttrees.core.TreeDetector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MinecraftTreeService {
    private final ConfigManager configs;
    private final TreeDetector detector = new TreeDetector();
    private final Map<UUID, PendingCapture> captures = new HashMap<>();
    private final Map<UUID, Operation> operations = new HashMap<>();

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
        captures.remove(playerId);
        ModConfig config = configs.current();
        if (operations.containsKey(playerId) || (config.requireShift() && !serverPlayer.isShiftKeyDown()) ||
                !isAllowedAxe(serverPlayer, config)) return true;

        GridPos origin = fromMinecraft(position);
        Optional<TreeDetector.DetectedTree> detected = detector.detect(pos -> node(serverLevel, pos), origin, config);
        detected.ifPresent(tree -> captures.put(playerId,
                new PendingCapture(serverLevel.dimension(), position.immutable(), state, tree)));
        return true;
    }

    private void afterBreak(Level level, Player player, BlockPos position, BlockState state,
                            net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) return;
        PendingCapture capture = captures.remove(serverPlayer.getUUID());
        if (capture == null || !capture.dimension().equals(serverLevel.dimension()) ||
                !capture.origin().equals(position) || !capture.initialState().equals(state) ||
                operations.containsKey(serverPlayer.getUUID())) return;

        List<GridPos> remainingLogs = capture.tree().logs().stream()
                .filter(log -> !log.equals(fromMinecraft(position))).toList();
        Operation operation = new Operation(serverLevel.dimension(), capture.tree(), remainingLogs, 0, 0, false, 0);
        operations.put(serverPlayer.getUUID(), scheduleNext(serverPlayer, operation, serverLevel.getServer().getTickCount()));
    }

    private void tick(MinecraftServer server) {
        int tick = server.getTickCount();
        operations.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Operation operation = entry.getValue();
            if (!validPlayer(player, operation, server)) return true;
            if (tick < operation.dueTick()) return false;
            ServerLevel level = player.level();

            if (!operation.leafPhase()) {
                Operation advanced = breakNextLog(player, level, operation, tick);
                if (advanced == null) return true;
                entry.setValue(advanced);
                return advanced.leafPhase() && advanced.leafIndex() >= advanced.tree().leaves().size();
            }

            int leafIndex = operation.leafIndex();
            if (leafIndex >= operation.tree().leaves().size()) return true;
            GridPos leaf = operation.tree().leaves().get(leafIndex);
            if (matchesExpected(level, leaf, operation.tree()) && leafAllowed(level, leaf) &&
                    level.mayInteract(player, toMinecraft(leaf))) breakLeafWithoutDurability(player, toMinecraft(leaf));
            entry.setValue(new Operation(operation.dimension(), operation.tree(), operation.logs(), operation.logIndex(),
                    operation.dueTick(), true, leafIndex + 1));
            return leafIndex + 1 >= operation.tree().leaves().size();
        });
    }

    private Operation breakNextLog(ServerPlayer player, ServerLevel level, Operation operation, int tick) {
        if (operation.logIndex() >= operation.logs().size()) return beginLeaves(operation, tick);
        GridPos position = operation.logs().get(operation.logIndex());
        int nextIndex = operation.logIndex() + 1;
        BlockPos minecraftPosition = toMinecraft(position);
        if (matchesExpected(level, position, operation.tree()) && level.mayInteract(player, minecraftPosition)) {
            player.gameMode.destroyBlock(minecraftPosition);
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
    }

    private record PendingCapture(ResourceKey<Level> dimension, BlockPos origin, BlockState initialState,
                                  TreeDetector.DetectedTree tree) {}

    private record Operation(ResourceKey<Level> dimension, TreeDetector.DetectedTree tree, List<GridPos> logs,
                             int logIndex, int dueTick, boolean leafPhase, int leafIndex) {}
}
