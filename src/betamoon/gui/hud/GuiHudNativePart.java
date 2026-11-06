package betamoon.gui.hud;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

/**
 * Semantic native presentation parts; gameplay state and renderer effects are
 * independent.
 */
public enum GuiHudNativePart {
    CROSSHAIR("crosshair"), HOTBAR("hotbar"), HEALTH("health"), ARMOR("armor"), BREATH("breath"), CHAT("chat");

    public final String name;
    public static final Map<String, GuiHudNativePart> BY_NAME;

    static {
        Map<String, GuiHudNativePart> parts = new LinkedHashMap<>();
        for (GuiHudNativePart part : values()) {
            parts.put(part.name, part);
        }
        BY_NAME = Collections.unmodifiableMap(parts);
    }

    GuiHudNativePart(String name) {
        this.name = name;
    }

    /**
     * Classifies native atlas arguments, never a call ordinal or screen location.
     */
    public static GuiHudNativePart texture(int u, int v, int width, int height) {
        if (u == 0 && v == 0 && width == 16 && height == 16) {
            return CROSSHAIR;
        }
        if (u == 0 && (v == 0 && width == 182 || v == 22 && width == 24) && height == 22) {
            return HOTBAR;
        }
        if (width != 9 || height != 9) {
            return null;
        }
        if (v == 0 && (u == 16 || u == 25 || u == 52 || u == 61 || u == 70 || u == 79)) {
            return HEALTH;
        }
        if (v == 9 && (u == 16 || u == 25 || u == 34)) {
            return ARMOR;
        }
        if (v == 18 && (u == 16 || u == 25)) {
            return BREATH;
        }
        return null;
    }
}
