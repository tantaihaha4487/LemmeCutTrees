package net.thanachot.lemmecuttrees.core;

import net.thanachot.lemmecuttrees.config.ModConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TreeDetectorTest {
    private static final TreeDetector.Node AIR = node("minecraft:air", TreeDetector.Node.Axis.NONE, false);

    @Test
    void traversalUsesARealVisitedSetAndStaysBounded() {
        Map<GridPos, TreeDetector.Node> blocks = new HashMap<>();
        blocks.put(new GridPos(0, 0, 0), log(TreeDetector.Node.Axis.Y));
        blocks.put(new GridPos(1, 0, 0), log(TreeDetector.Node.Axis.Y));
        blocks.put(new GridPos(0, 1, 0), log(TreeDetector.Node.Axis.Y));
        blocks.put(new GridPos(1, 1, 0), log(TreeDetector.Node.Axis.Y));

        Set<GridPos> result = TreeDetector.traverse(view(blocks), Set.of(new GridPos(0, 0, 0)),
                Set.of("minecraft:oak_log"), 256, 100, true, true);

        assertEquals(4, result.size());
        assertEquals(Set.copyOf(blocks.keySet()), result);
    }

    @Test
    void separatesSpeciesThatShareOakLogsByLeaves() {
        Map<GridPos, TreeDetector.Node> blocks = simpleTree("minecraft:azalea_leaves", false);
        ModConfig config = config(4, 4, 5, List.of(
                species("oak", "minecraft:oak_leaves"), species("azalea", "minecraft:azalea_leaves")));

        TreeDetector.DetectedTree tree = new TreeDetector().detect(view(blocks), new GridPos(0, 0, 0), config).orElseThrow();

        assertEquals("azalea", tree.species());
        assertTrue(tree.leaves().stream().allMatch(pos -> blocks.get(pos).blockId().equals("minecraft:azalea_leaves")));
    }

    @Test
    void ordersLogsByDistanceThenPosition() {
        Map<GridPos, TreeDetector.Node> blocks = simpleTree("minecraft:oak_leaves", false);
        GridPos origin = new GridPos(0, 0, 0);
        TreeDetector.DetectedTree tree = new TreeDetector().detect(view(blocks), origin,
                config(4, 4, 5, List.of(species("oak", "minecraft:oak_leaves")))).orElseThrow();

        assertEquals(List.of(new GridPos(0, 0, 0), new GridPos(0, 1, 0),
                new GridPos(0, 2, 0), new GridPos(0, 3, 0)), tree.logs());
    }

    @Test
    void rejectsCutsAboveConfiguredHeightFromTreeBase() {
        Map<GridPos, TreeDetector.Node> blocks = simpleTree("minecraft:oak_leaves", false);
        ModConfig config = config(4, 4, 3, List.of(species("oak", "minecraft:oak_leaves")));

        assertTrue(new TreeDetector().detect(view(blocks), new GridPos(0, 3, 0), config).isEmpty());
    }

    @Test
    void excludesPersistentLeavesUnlessExplicitlyEnabled() {
        Map<GridPos, TreeDetector.Node> blocks = simpleTree("minecraft:oak_leaves", true);
        ModConfig excluded = config(4, 1, 5, List.of(species("oak", "minecraft:oak_leaves")));
        ModConfig included = withPersistentLeaves(excluded);

        assertTrue(new TreeDetector().detect(view(blocks), new GridPos(0, 0, 0), excluded).isEmpty());
        assertEquals(4, new TreeDetector().detect(view(blocks), new GridPos(0, 0, 0), included)
                .orElseThrow().leaves().size());
    }

    @Test
    void capturedExpectedBlockPreventsChangedBlockOverwrite() {
        TreeDetector.DetectedTree tree = new TreeDetector().detect(view(simpleTree("minecraft:oak_leaves", false)),
                new GridPos(0, 0, 0), config(4, 4, 5, List.of(species("oak", "minecraft:oak_leaves"))))
                .orElseThrow();
        GridPos queued = new GridPos(0, 2, 0);

        assertTrue(TreeDetector.stillExpected(tree, queued, "minecraft:oak_log"));
        assertFalse(TreeDetector.stillExpected(tree, queued, "minecraft:stone"));
    }

    private static Map<GridPos, TreeDetector.Node> simpleTree(String leafId, boolean persistent) {
        Map<GridPos, TreeDetector.Node> blocks = new HashMap<>();
        for (int y = 0; y < 4; y++) blocks.put(new GridPos(0, y, 0), log(TreeDetector.Node.Axis.Y));
        blocks.put(new GridPos(1, 3, 0), node(leafId, TreeDetector.Node.Axis.NONE, persistent));
        blocks.put(new GridPos(2, 3, 0), node(leafId, TreeDetector.Node.Axis.NONE, persistent));
        blocks.put(new GridPos(1, 4, 0), node(leafId, TreeDetector.Node.Axis.NONE, persistent));
        blocks.put(new GridPos(1, 3, 1), node(leafId, TreeDetector.Node.Axis.NONE, persistent));
        return blocks;
    }

    private static TreeDetector.WorldView view(Map<GridPos, TreeDetector.Node> blocks) {
        return position -> blocks.getOrDefault(position, AIR);
    }

    private static TreeDetector.Node log(TreeDetector.Node.Axis axis) {
        return node("minecraft:oak_log", axis, false);
    }

    private static TreeDetector.Node node(String id, TreeDetector.Node.Axis axis, boolean persistent) {
        return new TreeDetector.Node(id, axis, persistent);
    }

    private static ModConfig.TreeSpecies species(String name, String leaves) {
        return new ModConfig.TreeSpecies(name, Set.of("minecraft:oak_log"), Set.of(leaves),
                false, null, null, null, null);
    }

    private static ModConfig config(int requiredLogs, int requiredLeaves, int maxCutHeight,
                                    List<ModConfig.TreeSpecies> species) {
        return new ModConfig(1, true, true, new ModConfig.CuttingSpeed(1.0, 1, 200),
                new ModConfig.Detection(256, requiredLogs, requiredLeaves, 250, maxCutHeight,
                        6, 6, 6, 0.5, false), Set.of("minecraft:iron_axe"), species);
    }

    private static ModConfig withPersistentLeaves(ModConfig config) {
        ModConfig.Detection old = config.detection();
        return new ModConfig(1, true, true, config.cuttingSpeed(),
                new ModConfig.Detection(old.scanDistance(), old.requiredLogs(), old.requiredLeaves(), old.maximumLogs(),
                        old.maximumCutHeight(), old.leafDetectRange(), old.leafBreakRange(),
                        old.maximumHorizontalLogRun(), old.minimumVerticalLogRatio(), true),
                config.allowedAxes(), config.trees());
    }
}
