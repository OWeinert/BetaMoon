package betamoon.gui.style;

import betamoon.luaapi.client.ui.GuiPaintCompiler;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable style literals and named resource references, resolved only in host
 * preparation.
 */
public final class GuiStyleValues {
    public static final GuiStyleValues EMPTY = new GuiStyleValues(Collections.emptyMap());
    public final Map<String, Object> values;
    public GuiStyleValues(Map<String, Object> values) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (name.equals("font") || name.equals("backgroundDrawable") || name.equals("contentDrawable")) {
                if (!(value instanceof String) || ((String) value).isEmpty() || ((String) value).length() > 128) {
                    throw new IllegalArgumentException("Style resource references require names");
                }
            } else {
                new GuiControlStyle(Collections.singletonMap(name, value));
            }
            copy.put(name, value);
        }
        this.values = Collections.unmodifiableMap(copy);
    }

    public boolean affectsMeasurement() {
        return values.containsKey("font") || values.containsKey("padding");
    }

    public GuiControlStyle prepare(GuiPaintCompiler.Resources resources) {
        Map<String, Object> result = new LinkedHashMap<>(values);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getKey().equals("font")) {
                result.put(entry.getKey(), resources.font((String) entry.getValue()));
            } else if (entry.getKey().equals("backgroundDrawable") || entry.getKey().equals("contentDrawable")) {
                result.put(entry.getKey(), resources.image((String) entry.getValue()));
            }
        }
        return new GuiControlStyle(result);
    }
}
