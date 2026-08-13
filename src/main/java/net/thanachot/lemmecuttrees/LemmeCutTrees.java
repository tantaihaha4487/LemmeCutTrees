package net.thanachot.lemmecuttrees;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LemmeCutTrees implements ModInitializer {
    public static final String MOD_ID = "lemmecuttrees";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("LemmeCutTrees initialized");
    }
}
