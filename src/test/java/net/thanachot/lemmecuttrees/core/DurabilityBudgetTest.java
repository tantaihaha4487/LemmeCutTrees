package net.thanachot.lemmecuttrees.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurabilityBudgetTest {
    @Test
    void reservesOutstandingAndNewTreeLogsTogether() {
        assertTrue(DurabilityBudget.canReserve(12, 7, 5));
        assertFalse(DurabilityBudget.canReserve(11, 7, 5));
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> DurabilityBudget.canReserve(-1, 0, 1));
    }
}
