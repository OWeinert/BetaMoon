package betamoon.luaapi.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** UI-neutral description of one declared config value. */
public final class ScriptConfigField {
    private final String name;
    private final String label;
    private final String description;
    private final Object defaultValue;
    private final Double minimum;
    private final Double maximum;
    private final Double step;
    private final List<String> choices;
    private final boolean readOnly;
    private final String readOnlyReason;
    private final int maxItems;
    private final ScriptConfigField element;
    private final Map<String, ScriptConfigField> children;

    ScriptConfigField(String name, String label, String description, Object defaultValue, Double minimum,
            Double maximum, Double step, List<String> choices, boolean readOnly, String readOnlyReason,
            int maxItems, ScriptConfigField element, Map<String, ScriptConfigField> children) {
        this.name = name;
        this.label = label;
        this.description = description;
        this.defaultValue = immutable(defaultValue);
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.choices = Collections.unmodifiableList(new ArrayList<String>(choices));
        this.readOnly = readOnly;
        this.readOnlyReason = readOnlyReason;
        this.maxItems = maxItems;
        this.element = element;
        this.children = Collections.unmodifiableMap(new LinkedHashMap<String, ScriptConfigField>(children));
    }

    public String name() {
        return name;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public Object defaultValue() {
        return defaultValue;
    }

    public Double minimum() {
        return minimum;
    }

    public Double maximum() {
        return maximum;
    }

    public Double step() {
        return step;
    }

    public List<String> choices() {
        return choices;
    }

    public boolean readOnly() {
        return readOnly;
    }

    public String readOnlyReason() {
        return readOnlyReason;
    }

    public int maxItems() {
        return maxItems;
    }

    public ScriptConfigField element() {
        return element;
    }

    public Map<String, ScriptConfigField> children() {
        return children;
    }

    private static Object immutable(Object value) {
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) value;
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                copy.put(entry.getKey(), immutable(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<Object>();
            for (Object entry : (List<?>) value) {
                copy.add(immutable(entry));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    /** Inferred editor shape for an existing array entry without element metadata. */
    public static ScriptConfigField inferred(String name, Object value) {
        Map<String, ScriptConfigField> nested = new LinkedHashMap<String, ScriptConfigField>();
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) value;
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                nested.put(entry.getKey(), inferred(entry.getKey(), entry.getValue()));
            }
        }
        ScriptConfigField item = null;
        if (value instanceof List && !((List<?>) value).isEmpty()) {
            item = inferred("item", ((List<?>) value).get(0));
        }
        return new ScriptConfigField(name, name, "", value, null, null, null, Collections.<String>emptyList(),
                false, "", 64, item, nested);
    }
}
