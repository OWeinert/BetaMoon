package betamoon.gui.loading;

import betamoon.assets.AssetId;
import betamoon.assets.AssetKey;
import betamoon.assets.AssetKind;
import betamoon.gui.style.GuiArgbColor;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.worldgen.spatial.GenerationValues;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable cosmetic transfer metadata; no native/session/GPU resource is
 * acquired by parsing.
 */
public final class GuiLoadingAppearance {
    public enum Mode {
        TILE, COVER, STRETCH
    }

    public static final GuiLoadingAppearance MINECRAFT = new GuiLoadingAppearance(null, Collections.emptyMap());
    public final String owner;
    public final AssetId background;
    public final Mode mode;
    public final int tileSize;
    public final int tint;
    public final int titleColor;
    public final int textColor;
    public final int barColor;
    public final int barBackgroundColor;
    private final ScriptResourceTracker.OwnerGeneration generation;
    private final long token;
    private final Map<String, Object> declaration;

    public GuiLoadingAppearance(String owner, Map<String, Object> value) {
        String path = "loading";
        GenerationValues.fields(value, path, "background", "mode", "tileSize", "tint", "titleColor", "textColor",
                "barColor", "barBackgroundColor");
        this.owner = owner;
        background = value.containsKey("background")
                ? new AssetId(AssetKind.TEXTURE,
                        AssetKey.parse(GenerationValues.string(value.get("background"), path + ".background")))
                : null;
        mode = mode(GenerationValues.string(value, "mode", background == null ? "tile" : "cover", path));
        if (mode != Mode.TILE && value.containsKey("tileSize")) {
            throw GenerationValues.error(path + ".tileSize", "tileSize requires tile mode");
        }
        tileSize = value.containsKey("tileSize")
                ? GenerationValues.integer(value.get("tileSize"), path + ".tileSize", 8, 1024)
                : 32;
        tint = color(value, "tint", background == null ? 0xFF404040 : 0xFFFFFFFF);
        titleColor = color(value, "titleColor", 0xFFFFFFFF);
        textColor = color(value, "textColor", 0xFFFFFFFF);
        barColor = color(value, "barColor", 0xFF80FF80);
        barBackgroundColor = color(value, "barBackgroundColor", 0xFF808080);
        generation = owner == null ? null : ScriptResourceTracker.ownerToken(owner);
        token = generation == null ? 0 : generation.current();
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (background != null) {
            normalized.put("background", background.getKey().toString());
        }
        normalized.put("mode", mode.name().toLowerCase(Locale.ROOT));
        if (mode == Mode.TILE) {
            normalized.put("tileSize", tileSize);
        }
        normalized.put("tint", hex(tint));
        normalized.put("titleColor", hex(titleColor));
        normalized.put("textColor", hex(textColor));
        normalized.put("barColor", hex(barColor));
        normalized.put("barBackgroundColor", hex(barBackgroundColor));
        declaration = Collections.unmodifiableMap(normalized);
    }

    public boolean available() {
        return generation == null || generation.matches(token);
    }

    public Map<String, Object> declaration() {
        return declaration;
    }

    private static Mode mode(String value) {
        switch (value) {
            case "tile":
                return Mode.TILE;
            case "cover":
                return Mode.COVER;
            case "stretch":
                return Mode.STRETCH;
            default:
                throw GenerationValues.error("loading.mode", "expected tile, cover or stretch");
        }
    }

    private static int color(Map<String, Object> value, String key, int fallback) {
        try {
            return value.containsKey(key) ? GuiArgbColor.read(value.get(key)) : fallback;
        } catch (IllegalArgumentException invalid) {
            throw GenerationValues.error("loading." + key, invalid.getMessage());
        }
    }

    private static String hex(int color) {
        return String.format(Locale.ROOT, "#%08X", color);
    }
}
