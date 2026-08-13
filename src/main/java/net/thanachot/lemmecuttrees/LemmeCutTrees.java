package net.thanachot.lemmecuttrees;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permissions;
import net.thanachot.lemmecuttrees.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public final class LemmeCutTrees implements ModInitializer {
    public static final String MOD_ID = "lemmecuttrees";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private final ConfigManager configs = new ConfigManager();

    @Override
    public void onInitialize() {
        try {
            configs.initialize();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load LemmeCutTrees configuration", exception);
        }
        new MinecraftTreeService(configs).register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("lemmecuttrees")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("reload").executes(context -> {
                            configs.reload(context.getSource().getServer(), context.getSource());
                            return 1;
                        }))));
        LOGGER.info("LemmeCutTrees initialized with {} tree mappings", configs.current().trees().size());
    }
}
