package net.thanachot.lemmecuttrees.config;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSourceStack;
import net.thanachot.lemmecuttrees.LemmeCutTrees;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public final class ConfigManager implements AutoCloseable {
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve(ConfigLoader.FILE_NAME);
    private final AtomicReference<ModConfig> current = new AtomicReference<>();
    private final ExecutorService loader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "lemmecuttrees-config-loader");
        thread.setDaemon(true);
        return thread;
    });

    public void initialize() throws IOException {
        current.set(ConfigLoader.loadOrCreate(path.getParent()));
    }

    public ModConfig current() {
        ModConfig result = current.get();
        if (result == null) throw new IllegalStateException("Configuration has not been initialized");
        return result;
    }

    public void reload(MinecraftServer server, CommandSourceStack source) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return new Candidate(ConfigLoader.load(path), null);
            } catch (IOException exception) {
                return new Candidate(null, exception.getMessage());
            }
        }, loader).thenAccept(candidate -> server.execute(() -> {
            if (candidate.config() != null) {
                current.set(candidate.config());
                source.sendSuccess(() -> Component.literal("LemmeCutTrees configuration reloaded"), true);
            } else {
                LemmeCutTrees.LOGGER.error("Configuration reload rejected: {}", candidate.error());
                source.sendFailure(Component.literal("Reload failed; previous configuration retained: " + candidate.error()));
            }
        }));
    }

    @Override
    public void close() {
        loader.shutdownNow();
    }

    private record Candidate(ModConfig config, String error) {}
}
