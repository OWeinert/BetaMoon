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
    private final String input;
    private final String collectionKind;
    private final List<ScriptConfigChoice> choices;
    private final boolean expanded;
    private final boolean readOnly;
    private final String readOnlyReason;
    private final int maxItems;
    private final ScriptConfigField element;
    private final ScriptConfigField keyField;
    private final ScriptConfigField valueField;
    private final Map<String, ScriptConfigField> children;
    private final List<ScriptConfigLayoutEntry> layout;

    ScriptConfigField(String name, String label, String description, Object defaultValue, Double minimum,
            Double maximum, Double step, String input, String collectionKind, List<ScriptConfigChoice> choices,
            boolean expanded, boolean readOnly, String readOnlyReason, int maxItems, ScriptConfigField element,
            ScriptConfigField keyField, ScriptConfigField valueField, Map<String, ScriptConfigField> children,
            List<ScriptConfigLayoutEntry> layout) {
        this.name = name;
        this.label = label;
        this.description = description;
        this.defaultValue = immutable(defaultValue);
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.input = input;
        this.collectionKind = collectionKind;
        this.choices = Collections.unmodifiableList(new ArrayList<ScriptConfigChoice>(choices));
        this.expanded = expanded;
        this.readOnly = readOnly;
        this.readOnlyReason = readOnlyReason;
        this.maxItems = maxItems;
        this.element = element;
        this.keyField = keyField;
        this.valueField = valueField;
        this.children = Collections.unmodifiableMap(new LinkedHashMap<String, ScriptConfigField>(children));
        this.layout = Collections.unmodifiableList(new ArrayList<ScriptConfigLayoutEntry>(layout));
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

    public String input() {
        return input;
    }

    public String collectionKind() {
        return collectionKind;
    }

    public List<ScriptConfigChoice> choices() {
        return choices;
    }

    public boolean expanded() {
        return expanded;
    }

    public ScriptConfigChoice choice(Object value) {
        for (ScriptConfigChoice choice : choices) {
            if (choice.matches(value)) {
                return choice;
            }
        }
        return null;
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

    public ScriptConfigField keyField() {
        return keyField;
    }

    public ScriptConfigField valueField() {
        return valueField;
    }

    public Map<String, ScriptConfigField> children() {
        return children;
    }

    public List<ScriptConfigLayoutEntry> layout() {
        return layout;
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

    /**
     * Inferred editor shape for an existing array entry without element metadata.
     */
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
        String input = value instanceof Boolean
                ? "cycle"
                : value instanceof String || value instanceof Number ? "text" : null;
        return new ScriptConfigField(name, name, "", value, null, null, null, input, null,
                Collections.<ScriptConfigChoice>emptyList(), false, false, "", 64, item, null, null, nested,
                Collections.<ScriptConfigLayoutEntry>emptyList());
    }
}
