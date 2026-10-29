package betamoon.gui.style;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable explicit style values; absence differs from false/zero/transparent.
 */
public final class GuiControlStyle {
    private static final Map<String, Class<?>> TYPES;
    static {
        Map<String, Class<?>> types = new LinkedHashMap<>();
        for (String name : new String[]{"background", "foreground", "border", "accent", "focus", "selection"}) {
            types.put(name, Integer.class);
        }
        types.put("padding", Integer.class);
        types.put("borderWidth", Integer.class);
        types.put("shadow", Boolean.class);
        types.put("nativeButton", Boolean.class);
        types.put("font", GuiFontFace.class);
        types.put("backgroundDrawable", GuiDrawable.class);
        types.put("contentDrawable", GuiDrawable.class);
        TYPES = Collections.unmodifiableMap(types);
    }

    private final Map<String, Object> values;

    public GuiControlStyle(Map<String, Object> values) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Class<?> type = TYPES.get(entry.getKey());
            if (type == null || !type.isInstance(entry.getValue())) {
                throw new IllegalArgumentException("Unknown or invalid style property: " + entry.getKey());
            }
            if (("padding".equals(entry.getKey()) || "borderWidth".equals(entry.getKey()))
                    && ((Integer) entry.getValue() < 0 || (Integer) entry.getValue() > 256)) {
                throw new IllegalArgumentException(entry.getKey() + " must be in 0..256");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        this.values = Collections.unmodifiableMap(copy);
    }

    public GuiControlStyle merge(GuiControlStyle overrides) {
        Map<String, Object> merged = new LinkedHashMap<>(values);
        merged.putAll(overrides.values);
        return new GuiControlStyle(merged);
    }

    public int number(String name, int fallback) {
        Object value = values.get(name);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    public boolean flag(String name, boolean fallback) {
        Object value = values.get(name);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    public GuiFontFace font() {
        return (GuiFontFace) values.getOrDefault("font", GuiFontFace.NATIVE);
    }

    public GuiDrawable drawable(String name) {
        Object value = values.get(name);
        return value instanceof GuiDrawable ? (GuiDrawable) value : null;
    }

    public void requireActive() {
        font().requireActive();
        for (String name : new String[]{"backgroundDrawable", "contentDrawable"}) {
            GuiDrawable drawable = drawable(name);
            if (drawable != null) {
                drawable.requireActive();
            }
        }
    }

    public boolean affectsMeasurement() {
        return values.containsKey("font") || values.containsKey("padding");
    }

    public static GuiControlStyle of(Object... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("Style entries require key/value pairs");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            if (!(values[i] instanceof String) || map.put((String) values[i], values[i + 1]) != null) {
                throw new IllegalArgumentException("Invalid or duplicate style key");
            }
        }
        return new GuiControlStyle(map);
    }
}
