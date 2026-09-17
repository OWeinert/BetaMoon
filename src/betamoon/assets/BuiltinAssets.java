package betamoon.assets;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable, headless metadata for assets supplied with BetaMoon. */
public final class BuiltinAssets {
    private static final Map<AssetId, AssetDefinition> MODELS = createModels();

    private BuiltinAssets() {
    }

    public static AssetDefinition find(AssetId id) {
        return MODELS.get(id);
    }

    public static Map<AssetId, AssetDefinition> models() {
        return MODELS;
    }

    private static Map<AssetId, AssetDefinition> createModels() {
        Map<AssetId, AssetDefinition> models = new LinkedHashMap<>();
        add(models, "stairs");
        alias(models, "stair", "stairs");
        String[] directions = {"north", "east", "south", "west"};
        for (int mask = 0; mask < 16; mask++) {
            String key = "fence";
            for (int bit = 0; bit < directions.length; bit++) {
                if ((mask & (1 << bit)) != 0) {
                    key += "_" + directions[bit];
                }
            }
            add(models, key);
        }
        alias(models, "fence_post", "fence");
        add(models, "trapdoor");
        add(models, "trapdoor_open");
        add(models, "ladder");
        add(models, "torch_wall");
        add(models, "button");
        add(models, "button_pressed");
        add(models, "torch");
        alias(models, "torch_floor", "torch");
        add(models, "lever");
        add(models, "lever_on");
        add(models, "lever_floor");
        add(models, "lever_floor_on");
        add(models, "rail_straight");
        add(models, "rail_ascending");
        add(models, "rail_corner");
        alias(models, "rail", "rail_straight");
        add(models, "slab");
        add(models, "slab_double");
        for (int layers = 1; layers <= 8; layers++) {
            add(models, "snow_" + layers);
        }
        alias(models, "snow", "snow_1");
        add(models, "pressure_plate");
        add(models, "pressure_plate_pressed");
        for (String state : new String[]{"", "_open"}) {
            add(models, "door" + state);
            add(models, "door_lower" + state);
            add(models, "door_upper" + state);
        }
        add(models, "bed");
        add(models, "bed_head");
        add(models, "bed_foot");
        return Collections.unmodifiableMap(models);
    }

    private static void add(Map<AssetId, AssetDefinition> models, String name) {
        AssetDefinition definition = AssetDefinition.builtinModel(AssetKey.parse("minecraft:block/" + name),
                AssetPath.parse("builtin/minecraft/models/block/" + name + ".json"));
        models.put(definition.getId(), definition);
    }

    private static void alias(Map<AssetId, AssetDefinition> models, String name, String source) {
        AssetId alias = new AssetId(AssetKind.MODEL, AssetKey.parse("minecraft:block/" + name));
        AssetId canonical = new AssetId(AssetKind.MODEL, AssetKey.parse("minecraft:block/" + source));
        AssetDefinition definition = models.get(canonical);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown built-in model alias source: " + source);
        }
        models.put(alias, definition);
    }
}
