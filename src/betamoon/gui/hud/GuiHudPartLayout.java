package betamoon.gui.hud;

import betamoon.gui.framework.GuiGeometry.Rect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiChat;

/**
 * Audited Beta 1.7.3 native placement references, in logical viewport units.
 */
public final class GuiHudPartLayout {
    public static Rect body(GuiHudNativePart part, int width, int height, boolean chatOpen) {
        int center = width / 2;
        switch (part) {
            case CROSSHAIR:
                return Rect.fromPositionAndSize(center - 7, height / 2 - 7, 16, 16);
            case HOTBAR:
                return Rect.fromPositionAndSize(center - 91, height - 22, 182, 22);
            case HEALTH:
                return Rect.fromPositionAndSize(center - 91, height - 32, 81, 9);
            case ARMOR:
                return Rect.fromPositionAndSize(center + 10, height - 32, 81, 9);
            case BREATH:
                return Rect.fromPositionAndSize(center - 91, height - 41, 81, 9);
            case CHAT:
                int lines = chatOpen ? 20 : 10;
                return Rect.fromPositionAndSize(2, height - 49 - (lines - 1) * 9, 320, lines * 9);
            default:
                throw new IllegalArgumentException("Unknown HUD part");
        }
    }

    public static Rect ink(GuiHudNativePart part, Rect body) {
        if (part == GuiHudNativePart.HOTBAR) {
            return Rect.fromPositionAndSize(body.getLeft() - 1, body.getTop() - 6, body.getWidth() + 2,
                    body.getHeight() + 6);
        }
        return part == GuiHudNativePart.HEALTH
                ? Rect.fromPositionAndSize(body.getLeft(), body.getTop(), body.getWidth(), body.getHeight() + 1)
                : body;
    }

    static Map<String, Object> snapshot(Minecraft client, int width, int height, boolean available,
            Map<String, Object> stats, List<Rect> reservations) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewport", rectangle(Rect.fromPositionAndSize(0, 0, width, height)));
        Map<String, Object> parts = new LinkedHashMap<>();
        for (GuiHudNativePart part : GuiHudNativePart.values()) {
            Rect body = body(part, width, height, client.currentScreen instanceof GuiChat);
            Rect ink = ink(part, body);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("available", available);
            entry.put("visible", available && visible(part, stats));
            entry.put("source", "native");
            entry.put("body", rectangle(body));
            entry.put("ink", rectangle(ink));
            Map<String, Object> effective = new LinkedHashMap<>();
            effective.put("body", entry.get("body"));
            effective.put("ink", entry.get("ink"));
            entry.put("effective", Collections.unmodifiableMap(effective));
            parts.put(part.name, Collections.unmodifiableMap(entry));
        }
        result.put("parts", Collections.unmodifiableMap(parts));
        List<Map<String, Object>> reserved = new ArrayList<>();
        for (Rect rect : reservations) {
            reserved.add(rectangle(rect));
        }
        result.put("reservations", Collections.unmodifiableList(reserved));
        return result;
    }

    private static boolean visible(GuiHudNativePart part, Map<String, Object> stats) {
        if (part == GuiHudNativePart.HEALTH || part == GuiHudNativePart.ARMOR || part == GuiHudNativePart.BREATH) {
            if (!Boolean.TRUE.equals(stats.get("showSurvivalStats"))) {
                return false;
            }
        }
        if (part == GuiHudNativePart.ARMOR) {
            return ((Number) stats.get("armor")).intValue() > 0;
        }
        if (part == GuiHudNativePart.BREATH) {
            return Boolean.TRUE.equals(stats.get("showBreath"));
        }
        // Chat geometry is measurable without claiming that messages are present.
        return part != GuiHudNativePart.CHAT;
    }

    public static Map<String, Object> rectangle(Rect rectangle) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("x", rectangle.getLeft());
        value.put("y", rectangle.getTop());
        value.put("width", rectangle.getWidth());
        value.put("height", rectangle.getHeight());
        return Collections.unmodifiableMap(value);
    }
}
