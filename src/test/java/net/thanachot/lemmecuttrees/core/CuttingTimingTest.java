package net.thanachot.lemmecuttrees.core;

import net.thanachot.lemmecuttrees.config.ModConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CuttingTimingTest {
    @Test
    void convertsVanillaProgressToTicks() {
        assertEquals(10, CuttingTiming.ticks(0.1f, speed(1.0, 1, 200)));
        assertEquals(20, CuttingTiming.ticks(0.1f, speed(2.0, 1, 200)));
    }

    @Test
    void clampsBoundsAndHandlesUnbreakableStates() {
        assertEquals(3, CuttingTiming.ticks(1.0f, speed(1.0, 3, 40)));
        assertEquals(40, CuttingTiming.ticks(0.0001f, speed(1.0, 3, 40)));
        assertEquals(40, CuttingTiming.ticks(0.0f, speed(1.0, 3, 40)));
    }

    private static ModConfig.CuttingSpeed speed(double multiplier, int minimum, int maximum) {
        return new ModConfig.CuttingSpeed(multiplier, minimum, maximum);
    }
}
