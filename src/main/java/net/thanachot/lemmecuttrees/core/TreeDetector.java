package net.thanachot.lemmecuttrees.core;

import net.thanachot.lemmecuttrees.config.ModConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

public final class TreeDetector {
    public interface WorldView {
        Node node(GridPos position);
    }

    public record Node(String blockId, Axis axis, boolean persistentLeaf) {
        public enum Axis { X, Y, Z, NONE }
    }

    public record DetectedTree(String species, List<GridPos> logs, List<GridPos> leaves, Map<GridPos, String> expectedBlocks) {}

    public static boolean stillExpected(DetectedTree tree, GridPos position, String currentBlockId) {
        return currentBlockId.equals(tree.expectedBlocks().get(position));
    }

    public Optional<DetectedTree> detect(WorldView world, GridPos origin, ModConfig config) {
        String initialId = world.node(origin).blockId();
        for (ModConfig.TreeSpecies species : config.trees()) {
            if (!species.logs().contains(initialId)) continue;
            Optional<DetectedTree> result = detectSpecies(world, origin, config.detection(), species);
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }

    private Optional<DetectedTree> detectSpecies(WorldView world, GridPos origin, ModConfig.Detection options,
                                                 ModConfig.TreeSpecies species) {
        Set<GridPos> logs = traverse(world, Set.of(origin), species.logs(), options.scanDistance(),
                options.maximumLogs() + 1, true, true);
        int requiredLogs = species.requiredLogs() == null ? options.requiredLogs() : species.requiredLogs();
        if (logs.size() < requiredLogs || logs.size() > options.maximumLogs()) return Optional.empty();

        int minimumY = logs.stream().mapToInt(GridPos::y).min().orElse(origin.y());
        if (origin.y() - minimumY + 1 > options.maximumCutHeight()) return Optional.empty();
        int horizontalLimit = species.maximumHorizontalLogRun() == null
                ? options.maximumHorizontalLogRun() : species.maximumHorizontalLogRun();
        if (maximumHorizontalRun(logs) > horizontalLimit) return Optional.empty();

        long vertical = logs.stream().filter(pos -> world.node(pos).axis() == Node.Axis.Y).count();
        long horizontal = logs.stream().filter(pos -> {
            Node.Axis axis = world.node(pos).axis();
            return axis == Node.Axis.X || axis == Node.Axis.Z;
        }).count();
        double ratio = horizontal == 0 ? Double.POSITIVE_INFINITY : vertical / (double) horizontal;
        if (ratio < options.minimumVerticalLogRatio()) return Optional.empty();

        Set<GridPos> leafSeeds = new LinkedHashSet<>();
        for (GridPos log : logs) {
            for (GridPos neighbor : species.diagonalLeaves() ? allNeighbors(log) : faceNeighbors(log)) {
                Node node = world.node(neighbor);
                if (species.leaves().contains(node.blockId()) &&
                        (options.includePlayerPlacedLeaves() || !node.persistentLeaf())) leafSeeds.add(neighbor);
            }
        }
        int detectRange = species.leafDetectRange() == null ? options.leafDetectRange() : species.leafDetectRange();
        int breakRange = species.leafBreakRange() == null ? options.leafBreakRange() : species.leafBreakRange();
        Set<GridPos> detectedLeaves = traverseLeaves(world, leafSeeds, species.leaves(), detectRange,
                options.includePlayerPlacedLeaves(), species.diagonalLeaves());
        if (detectedLeaves.size() < options.requiredLeaves()) return Optional.empty();
        Set<GridPos> breakLeaves = traverseLeaves(world, leafSeeds, species.leaves(), breakRange,
                options.includePlayerPlacedLeaves(), species.diagonalLeaves());

        Map<GridPos, Integer> graphDistances = graphDistances(origin, logs);
        Comparator<GridPos> order = Comparator.comparingInt((GridPos pos) -> graphDistances.getOrDefault(pos, Integer.MAX_VALUE))
                .thenComparing(Comparator.naturalOrder());
        List<GridPos> orderedLogs = logs.stream().sorted(order).toList();
        List<GridPos> orderedLeaves = breakLeaves.stream().sorted(order).toList();
        Map<GridPos, String> expected = new HashMap<>();
        logs.forEach(pos -> expected.put(pos, world.node(pos).blockId()));
        breakLeaves.forEach(pos -> expected.put(pos, world.node(pos).blockId()));
        return Optional.of(new DetectedTree(species.name(), orderedLogs, orderedLeaves, Map.copyOf(expected)));
    }

    static Set<GridPos> traverse(WorldView world, Set<GridPos> seeds, Set<String> accepted, int maxDistance,
                                 int maximumResults, boolean diagonal, boolean includePersistent) {
        Set<GridPos> visited = new HashSet<>();
        Set<GridPos> results = new LinkedHashSet<>();
        Queue<Step> queue = new ArrayDeque<>();
        seeds.forEach(pos -> queue.add(new Step(pos, 0)));
        while (!queue.isEmpty() && results.size() < maximumResults) {
            Step step = queue.remove();
            if (!visited.add(step.position()) || step.distance() > maxDistance) continue;
            Node node = world.node(step.position());
            if (!accepted.contains(node.blockId()) || (!includePersistent && node.persistentLeaf())) continue;
            results.add(step.position());
            if (step.distance() == maxDistance) continue;
            for (GridPos neighbor : diagonal ? allNeighbors(step.position()) : faceNeighbors(step.position())) {
                if (!visited.contains(neighbor)) queue.add(new Step(neighbor, step.distance() + 1));
            }
        }
        return results;
    }

    private static Set<GridPos> traverseLeaves(WorldView world, Set<GridPos> seeds, Set<String> accepted,
                                               int maxDistance, boolean includePersistent, boolean diagonal) {
        return traverse(world, seeds, accepted, maxDistance, Integer.MAX_VALUE, diagonal, includePersistent);
    }

    private static Map<GridPos, Integer> graphDistances(GridPos origin, Set<GridPos> logs) {
        Map<GridPos, Integer> distances = new HashMap<>();
        Queue<GridPos> queue = new ArrayDeque<>();
        distances.put(origin, 0);
        queue.add(origin);
        while (!queue.isEmpty()) {
            GridPos current = queue.remove();
            int distance = distances.get(current);
            for (GridPos neighbor : allNeighbors(current)) {
                if (logs.contains(neighbor) && distances.putIfAbsent(neighbor, distance + 1) == null) queue.add(neighbor);
            }
        }
        return distances;
    }

    static int maximumHorizontalRun(Set<GridPos> logs) {
        int maximum = 0;
        for (GridPos position : logs) {
            int xRun = 1;
            while (logs.contains(position.offset(-xRun, 0, 0))) xRun++;
            int xPositive = 1;
            while (logs.contains(position.offset(xPositive, 0, 0))) xPositive++;
            maximum = Math.max(maximum, xRun + xPositive - 1);
            int zRun = 1;
            while (logs.contains(position.offset(0, 0, -zRun))) zRun++;
            int zPositive = 1;
            while (logs.contains(position.offset(0, 0, zPositive))) zPositive++;
            maximum = Math.max(maximum, zRun + zPositive - 1);
        }
        return maximum;
    }

    private static List<GridPos> faceNeighbors(GridPos position) {
        return List.of(position.offset(-1, 0, 0), position.offset(1, 0, 0),
                position.offset(0, -1, 0), position.offset(0, 1, 0),
                position.offset(0, 0, -1), position.offset(0, 0, 1));
    }

    private static List<GridPos> allNeighbors(GridPos position) {
        List<GridPos> result = new ArrayList<>(26);
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            if (x != 0 || y != 0 || z != 0) result.add(position.offset(x, y, z));
        }
        return result;
    }

    private record Step(GridPos position, int distance) {}
}
