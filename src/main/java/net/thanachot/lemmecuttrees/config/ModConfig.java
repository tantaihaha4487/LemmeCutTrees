package net.thanachot.lemmecuttrees.config;

import java.util.List;
import java.util.Set;

public record ModConfig(
        int schemaVersion,
        boolean requireShift,
        boolean clearLeaves,
        CuttingSpeed cuttingSpeed,
        Detection detection,
        Set<String> allowedAxes,
        List<TreeSpecies> trees) {

    public record CuttingSpeed(double multiplier, int minimumTicksPerLog, int maximumTicksPerLog) {}

    public record Detection(
            int scanDistance,
            int requiredLogs,
            int requiredLeaves,
            int maximumLogs,
            int maximumCutHeight,
            int leafDetectRange,
            int leafBreakRange,
            int maximumHorizontalLogRun,
            double minimumVerticalLogRatio,
            boolean includePlayerPlacedLeaves) {}

    public record TreeSpecies(
            String name,
            Set<String> logs,
            Set<String> leaves,
            boolean diagonalLeaves,
            Integer requiredLogs,
            Integer leafDetectRange,
            Integer leafBreakRange,
            Integer maximumHorizontalLogRun) {}
}
