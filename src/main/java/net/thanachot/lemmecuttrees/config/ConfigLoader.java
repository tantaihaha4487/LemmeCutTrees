package net.thanachot.lemmecuttrees.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.AxeItem;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConfigLoader {
    public static final String FILE_NAME = "lemmecuttrees.yml";

    private ConfigLoader() {}

    public static ModConfig loadOrCreate(Path configDirectory) throws IOException {
        Files.createDirectories(configDirectory);
        Path path = configDirectory.resolve(FILE_NAME);
        if (Files.notExists(path)) {
            try (InputStream input = ConfigLoader.class.getResourceAsStream("/" + FILE_NAME)) {
                if (input == null) throw new IOException("Missing bundled default " + FILE_NAME);
                Files.copy(input, path);
            }
        }
        return load(path);
    }

    public static ModConfig load(Path path) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(20);
        Object loaded;
        try (InputStream input = Files.newInputStream(path)) {
            loaded = new Yaml(new SafeConstructor(options)).load(input);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid YAML: " + exception.getMessage(), exception);
        }
        Map<String, Object> root = map(loaded, "root");
        int schema = integer(root, "schema-version");
        if (schema != 1) throw new IOException("Unsupported schema-version: " + schema);

        Map<String, Object> speed = map(root.get("cutting-speed"), "cutting-speed");
        ModConfig.CuttingSpeed cuttingSpeed = new ModConfig.CuttingSpeed(
                decimal(speed, "multiplier"), integer(speed, "minimum-ticks-per-log"),
                integer(speed, "maximum-ticks-per-log"));
        if (!(cuttingSpeed.multiplier() > 0.0) || !Double.isFinite(cuttingSpeed.multiplier())) {
            throw new IOException("cutting-speed.multiplier must be finite and greater than zero");
        }
        positive(cuttingSpeed.minimumTicksPerLog(), "cutting-speed.minimum-ticks-per-log");
        if (cuttingSpeed.maximumTicksPerLog() < cuttingSpeed.minimumTicksPerLog()) {
            throw new IOException("maximum-ticks-per-log must be at least minimum-ticks-per-log");
        }

        Map<String, Object> detectionMap = map(root.get("detection"), "detection");
        ModConfig.Detection detection = new ModConfig.Detection(
                integer(detectionMap, "scan-distance"), integer(detectionMap, "required-logs"),
                integer(detectionMap, "required-leaves"), integer(detectionMap, "maximum-logs"),
                integer(detectionMap, "maximum-cut-height"), integer(detectionMap, "leaf-detect-range"),
                integer(detectionMap, "leaf-break-range"), integer(detectionMap, "maximum-horizontal-log-run"),
                decimal(detectionMap, "minimum-vertical-log-ratio"),
                bool(detectionMap, "include-player-placed-leaves"));
        positive(detection.scanDistance(), "detection.scan-distance");
        positive(detection.requiredLogs(), "detection.required-logs");
        nonNegative(detection.requiredLeaves(), "detection.required-leaves");
        if (detection.maximumLogs() < detection.requiredLogs()) {
            throw new IOException("detection.maximum-logs must be at least required-logs");
        }
        positive(detection.maximumCutHeight(), "detection.maximum-cut-height");
        positive(detection.leafDetectRange(), "detection.leaf-detect-range");
        positive(detection.leafBreakRange(), "detection.leaf-break-range");
        positive(detection.maximumHorizontalLogRun(), "detection.maximum-horizontal-log-run");
        if (detection.minimumVerticalLogRatio() < 0 || !Double.isFinite(detection.minimumVerticalLogRatio())) {
            throw new IOException("detection.minimum-vertical-log-ratio must be finite and non-negative");
        }

        Set<String> axes = identifiers(list(root, "allowed-axes"), "allowed-axes", true);
        for (String id : axes) {
            Identifier identifier = Identifier.parse(id);
            if (!BuiltInRegistries.ITEM.containsKey(identifier)) throw new IOException("Unknown item in allowed-axes: " + id);
            if (!(BuiltInRegistries.ITEM.getValue(identifier) instanceof AxeItem)) {
                throw new IOException("Item in allowed-axes is not an axe: " + id);
            }
        }

        List<Object> rawTrees = list(root, "trees");
        if (rawTrees.isEmpty()) throw new IOException("trees must not be empty");
        List<ModConfig.TreeSpecies> trees = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<String> signatures = new HashSet<>();
        for (int index = 0; index < rawTrees.size(); index++) {
            Map<String, Object> tree = map(rawTrees.get(index), "trees[" + index + "]");
            String name = string(tree, "name");
            if (!names.add(name)) throw new IOException("Duplicate tree name: " + name);
            Set<String> logs = identifiers(list(tree, "logs"), "trees[" + index + "].logs", true);
            Set<String> leaves = identifiers(list(tree, "leaves"), "trees[" + index + "].leaves", true);
            String signature = logs.stream().sorted().toList() + " -> " + leaves.stream().sorted().toList();
            if (!signatures.add(signature)) throw new IOException("Duplicate tree mapping: " + name);
            for (String id : union(logs, leaves)) {
                Identifier identifier = Identifier.parse(id);
                if (!BuiltInRegistries.BLOCK.containsKey(identifier)) throw new IOException("Unknown block in trees: " + id);
            }
            trees.add(new ModConfig.TreeSpecies(name, logs, leaves,
                    optionalBoolean(tree, "diagonal-leaves", false), optionalInteger(tree, "required-logs"),
                    optionalInteger(tree, "leaf-detect-range"), optionalInteger(tree, "leaf-break-range"),
                    optionalInteger(tree, "maximum-horizontal-log-run")));
        }
        return new ModConfig(schema, bool(root, "require-shift"), bool(root, "clear-leaves"),
                cuttingSpeed, detection, Set.copyOf(axes), List.copyOf(trees));
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> result = new HashSet<>(first);
        result.addAll(second);
        return result;
    }

    private static Set<String> identifiers(List<Object> values, String path, boolean nonEmpty) throws IOException {
        if (nonEmpty && values.isEmpty()) throw new IOException(path + " must not be empty");
        Set<String> result = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof String id)) throw new IOException(path + " entries must be strings");
            try {
                id = Identifier.parse(id).toString();
            } catch (RuntimeException exception) {
                throw new IOException("Invalid identifier in " + path + ": " + id, exception);
            }
            if (!result.add(id)) throw new IOException("Duplicate identifier in " + path + ": " + id);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value, String path) throws IOException {
        if (!(value instanceof Map<?, ?> raw)) throw new IOException(path + " must be a mapping");
        for (Object key : raw.keySet()) if (!(key instanceof String)) throw new IOException(path + " keys must be strings");
        return (Map<String, Object>) raw;
    }

    private static List<Object> list(Map<String, Object> map, String key) throws IOException {
        Object value = required(map, key);
        if (!(value instanceof List<?> list)) throw new IOException(key + " must be a list");
        return new ArrayList<>(list);
    }

    private static Object required(Map<String, Object> map, String key) throws IOException {
        if (!map.containsKey(key)) throw new IOException("Missing required key: " + key);
        return map.get(key);
    }

    private static String string(Map<String, Object> map, String key) throws IOException {
        Object value = required(map, key);
        if (!(value instanceof String text) || text.isBlank()) throw new IOException(key + " must be a non-empty string");
        return text;
    }

    private static boolean bool(Map<String, Object> map, String key) throws IOException {
        Object value = required(map, key);
        if (!(value instanceof Boolean result)) throw new IOException(key + " must be true or false");
        return result;
    }

    private static int integer(Map<String, Object> map, String key) throws IOException {
        Object value = required(map, key);
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue()) throw new IOException(key + " must be an integer");
        return number.intValue();
    }

    private static double decimal(Map<String, Object> map, String key) throws IOException {
        Object value = required(map, key);
        if (!(value instanceof Number number)) throw new IOException(key + " must be a number");
        return number.doubleValue();
    }

    private static boolean optionalBoolean(Map<String, Object> map, String key, boolean fallback) throws IOException {
        if (!map.containsKey(key)) return fallback;
        return bool(map, key);
    }

    private static Integer optionalInteger(Map<String, Object> map, String key) throws IOException {
        if (!map.containsKey(key)) return null;
        int value = integer(map, key);
        positive(value, key);
        return value;
    }

    private static void positive(int value, String path) throws IOException {
        if (value <= 0) throw new IOException(path + " must be greater than zero");
    }

    private static void nonNegative(int value, String path) throws IOException {
        if (value < 0) throw new IOException(path + " must be non-negative");
    }
}
