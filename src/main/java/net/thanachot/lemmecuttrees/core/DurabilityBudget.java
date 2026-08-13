package net.thanachot.lemmecuttrees.core;

public final class DurabilityBudget {
    private DurabilityBudget() {}

    public static boolean canReserve(int remainingDurability, int alreadyReservedLogs, int newTreeLogs) {
        if (remainingDurability < 0 || alreadyReservedLogs < 0 || newTreeLogs < 0) {
            throw new IllegalArgumentException("Durability budget values must be non-negative");
        }
        return (long) alreadyReservedLogs + newTreeLogs <= remainingDurability;
    }
}
