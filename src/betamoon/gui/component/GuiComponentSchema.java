package betamoon.gui.component;

import betamoon.gui.presentation.GuiPropertyDescriptor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Typed scalar/record/list component props, state and event payloads,
 * independent of host access.
 */
public final class GuiComponentSchema {
    public static final GuiComponentSchema EMPTY = new GuiComponentSchema(Collections.emptyMap());

    public static final class Field {
        public final GuiPropertyDescriptor descriptor;
        public final boolean required;

        public Field(GuiPropertyDescriptor descriptor, boolean required) {
            this.descriptor = Objects.requireNonNull(descriptor, "schema descriptor");
            this.required = required;
        }
    }

    private final Map<String, Field> fields;
    public final long metadataBytes;

    public GuiComponentSchema(Map<String, Field> fields) {
        if (fields == null || fields.size() > 64) {
            throw new IllegalArgumentException("Component schemas support at most 64 fields");
        }
        Map<String, Field> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Field> entry : fields.entrySet()) {
            Field field = entry.getValue();
            if (field == null || !field.descriptor.name.equals(entry.getKey())) {
                throw new IllegalArgumentException("Component field identity mismatch");
            }
            copy.put(entry.getKey(), field);
        }
        this.fields = Collections.unmodifiableMap(copy);
        GuiComponentLimits.Values budget = new GuiComponentLimits.Values(1024 * 1024, 16384);
        for (Map.Entry<String, Field> field : copy.entrySet()) {
            budget.text(field.getKey());
            budget.add(field.getValue().descriptor.defaultValue);
        }
        metadataBytes = budget.estimatedBytes() + copy.size() * 128L;
    }

    public Map<String, Field> fields() {
        return fields;
    }

    public Map<String, Object> normalize(Map<String, Object> values) {
        for (String name : values.keySet()) {
            if (!fields.containsKey(name)) {
                throw new IllegalArgumentException("Unknown component field: " + name);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Field> entry : fields.entrySet()) {
            Field field = entry.getValue();
            if (field.required && !values.containsKey(entry.getKey())) {
                throw new IllegalArgumentException("Required component field is missing: " + entry.getKey());
            }
            result.put(entry.getKey(), field.descriptor.normalize(
                    values.containsKey(entry.getKey()) ? values.get(entry.getKey()) : field.descriptor.defaultValue));
        }
        return Collections.unmodifiableMap(result);
    }

    public Map<String, Object> patch(Map<String, Object> previous, Map<String, Object> changes) {
        Map<String, Object> merged = new LinkedHashMap<>(previous);
        merged.putAll(changes);
        return normalize(merged);
    }
}
