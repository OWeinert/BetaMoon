package betamoon.assets;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable, headless metadata for assets supplied with BetaMoon. */
public final class BuiltinAssets {
    private static final Map<AssetId, AssetDefinition> ASSETS = createAssets();
    private static final Map<AssetId, AssetDefinition> MODELS = select(AssetKind.MODEL);
    private static final Map<AssetId, AssetDefinition> TEXTURES = select(AssetKind.TEXTURE);

    private BuiltinAssets() {
    }

    public static AssetDefinition find(AssetId id) {
        return ASSETS.get(id);
    }

    public static Map<AssetId, AssetDefinition> models() {
        return MODELS;
    }

    public static Map<AssetId, AssetDefinition> textures() {
        return TEXTURES;
    }

    private static Map<AssetId, AssetDefinition> createAssets() {
        Map<AssetId, AssetDefinition> assets = new LinkedHashMap<>();
        assets.putAll(createModels());
        addControlTextures(assets);
        return Collections.unmodifiableMap(assets);
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
        AssetDefinition definition = AssetDefinition.builtin(AssetKind.MODEL,
                AssetKey.parse("minecraft:block/" + name),
                AssetPath.parse("builtin/minecraft/models/block/" + name + ".json"), "json");
        models.put(definition.getId(), definition);
    }

    private static void addControlTextures(Map<AssetId, AssetDefinition> assets) {
        String[] interactionStates = {"normal", "hovered", "pressed", "focused", "disabled", "invalid"};
        for (String state : interactionStates) {
            addTexture(assets, "gui/controls/button/" + state);
            addTexture(assets, "gui/controls/toggle_button/off_" + state);
            addTexture(assets, "gui/controls/toggle_button/on_" + state);
            addTexture(assets, "gui/controls/text_box/" + state);
            addTexture(assets, "gui/controls/slider/track_" + state);
            addTexture(assets, "gui/controls/slider/handle_horizontal_" + state);
            addTexture(assets, "gui/controls/slider/handle_vertical_" + state);
        }
        for (String value : new String[]{"off", "on"}) {
            for (String state : interactionStates) {
                addTexture(assets, "gui/controls/checkbox/" + value + "_" + state);
                addTexture(assets, "gui/controls/radio/" + value + "_" + state);
            }
        }
        for (String direction : new String[]{"left", "right"}) {
            for (String state : interactionStates) {
                addTexture(assets, "gui/controls/choice/" + direction + "_" + state);
            }
        }
        for (String icon : new String[]{"close", "confirm", "plus", "minus"}) {
            addTexture(assets, "gui/controls/icon/" + icon);
        }
        for (String selection : new String[]{"unselected", "selected"}) {
            for (String state : interactionStates) {
                addTexture(assets, "gui/controls/tab/" + selection + "_" + state);
            }
        }
    }

    private static void addTexture(Map<AssetId, AssetDefinition> assets, String name) {
        AssetDefinition definition = AssetDefinition.builtin(AssetKind.TEXTURE,
                AssetKey.parse("betamoon:" + name),
                AssetPath.parse("builtin/betamoon/textures/" + name + ".png"), "png");
        assets.put(definition.getId(), definition);
    }

    private static Map<AssetId, AssetDefinition> select(AssetKind kind) {
        Map<AssetId, AssetDefinition> selected = new LinkedHashMap<>();
        for (Map.Entry<AssetId, AssetDefinition> entry : ASSETS.entrySet()) {
            if (entry.getKey().getKind() == kind) {
                selected.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(selected);
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
