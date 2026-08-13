package net.thanachot.lemmecuttrees.core;

import net.thanachot.lemmecuttrees.config.ModConfig;

public final class CuttingTiming {
    private CuttingTiming() {}

    public static int ticks(float destroyProgress, ModConfig.CuttingSpeed speed) {
        if (!(destroyProgress > 0.0f) || !Float.isFinite(destroyProgress)) return speed.maximumTicksPerLog();
        long raw = (long) Math.ceil((1.0 / destroyProgress) * speed.multiplier());
        return (int) Math.max(speed.minimumTicksPerLog(), Math.min(speed.maximumTicksPerLog(), raw));
    }
}
