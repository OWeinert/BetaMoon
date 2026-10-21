package betamoon.luaapi.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validates staged GUI edits and reports concrete changed paths. */
final class ScriptConfigEdits {
    private ScriptConfigEdits() {
    }

    static void validate(Map<String, ScriptConfigField> fields, Map<String, Object> before, Map<String, Object> draft) {
        for (Map.Entry<String, ScriptConfigField> entry : fields.entrySet()) {
            String name = entry.getKey();
            validateField(entry.getValue(), before.get(name), draft.get(name), name);
        }
    }

    static void validateField(ScriptConfigField field, Object before, Object value, String path) {
        if (field.readOnly() && before != null && !Objects.equals(before, value)) {
            throw new ScriptConfigValidationException(path,
                    "is read-only" + (field.readOnlyReason().isEmpty() ? "" : ": " + field.readOnlyReason()));
        }
        Object fallback = field.defaultValue();
        if (fallback instanceof Map) {
            if (!(value instanceof Map)) {
                throw new ScriptConfigValidationException(path, "must be an object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> current = (Map<String, Object>) value;
            @SuppressWarnings("unchecked")
            Map<String, Object> previous = before instanceof Map
                    ? (Map<String, Object>) before
                    : Collections.<String, Object>emptyMap();
            if ("dictionary".equals(field.collectionKind())) {
                if (current.size() > field.maxItems()) {
                    throw new ScriptConfigValidationException(path,
                            "cannot contain more than " + field.maxItems() + " entries");
                }
                for (Map.Entry<String, Object> entry : current.entrySet()) {
                    if (entry.getKey().isEmpty()) {
                        throw new ScriptConfigValidationException(path, "cannot contain an empty key");
                    }
                    validateField(field.valueField(), previous.get(entry.getKey()), entry.getValue(),
                            path + "." + entry.getKey());
                }
                return;
            }
            for (Map.Entry<String, ScriptConfigField> entry : field.children().entrySet()) {
                validateField(entry.getValue(), previous.get(entry.getKey()), current.get(entry.getKey()),
                        path + "." + entry.getKey());
            }
        } else if (fallback instanceof List) {
            if (!(value instanceof List)) {
                throw new ScriptConfigValidationException(path, "must be an array");
            }
            List<?> values = (List<?>) value;
            if ("multiSelect".equals(field.input())) {
                validateMultiSelect(field, values, path);
                return;
            }
            List<?> previous = before instanceof List ? (List<?>) before : Collections.emptyList();
            if (values.size() > field.maxItems()) {
                throw new ScriptConfigValidationException(path,
                        "cannot contain more than " + field.maxItems() + " entries");
            }
            if (field.element() == null && values.size() > previous.size()) {
                throw new ScriptConfigValidationException(path, "needs element metadata before entries can be added");
            }
            for (int index = 0; index < values.size(); index++) {
                Object old = index < previous.size() ? previous.get(index) : null;
                if ("list".equals(field.collectionKind())) {
                    validateField(field.element(), old, values.get(index), path + "[" + (index + 1) + "]");
                    continue;
                }
                Object expected = index < ((List<?>) fallback).size()
                        ? ((List<?>) fallback).get(index)
                        : field.element() == null ? null : field.element().defaultValue();
                if (expected == null) {
                    expected = old;
                }
                if (expected == null) {
                    throw new ScriptConfigValidationException(path + "[" + (index + 1) + "]",
                            "has no declared element type");
                }
                ScriptConfigField descriptor = field.element();
                if (descriptor != null && sameType(expected, descriptor.defaultValue())) {
                    validateField(descriptor, old, values.get(index), path + "[" + (index + 1) + "]");
                } else if (!sameType(expected, values.get(index))) {
                    throw new ScriptConfigValidationException(path + "[" + (index + 1) + "]",
                            "has a different type");
                }
            }
        } else if (!sameType(fallback, value)) {
            throw new ScriptConfigValidationException(path, "has a different type from its default");
        } else if (fallback instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number) || field.minimum() != null && number < field.minimum().doubleValue()
                    || field.maximum() != null && number > field.maximum().doubleValue()) {
                throw new ScriptConfigValidationException(path, "is outside its declared bounds");
            }
            if (field.step() != null) {
                double origin = field.minimum() == null ? 0 : field.minimum().doubleValue();
                double steps = (number - origin) / field.step().doubleValue();
                if (Math.abs(steps - Math.rint(steps)) > 0.000001) {
                    throw new ScriptConfigValidationException(path, "does not match its declared step");
                }
            }
            if (!field.choices().isEmpty() && field.choice(value) == null) {
                throw new ScriptConfigValidationException(path, "must be one of its declared choices");
            }
        } else if (fallback instanceof String && !field.choices().isEmpty() && field.choice(value) == null) {
            throw new ScriptConfigValidationException(path, "must be one of its declared choices");
        }
    }

    static void validateMultiSelect(ScriptConfigField field, List<?> values, String path) {
        if (values.size() > field.choices().size()) {
            throw new ScriptConfigValidationException(path, "has more entries than declared choices");
        }
        List<Object> seen = new ArrayList<Object>();
        for (int index = 0; index < values.size(); index++) {
            Object selected = values.get(index);
            String itemPath = path + "[" + (index + 1) + "]";
            ScriptConfigChoice choice = field.choice(selected);
            if (choice == null) {
                throw new ScriptConfigValidationException(itemPath, "must be a declared choice");
            }
            if (seen.contains(choice.value())) {
                throw new ScriptConfigValidationException(itemPath, "duplicates an earlier choice");
            }
            seen.add(choice.value());
        }
    }

    private static boolean sameType(Object left, Object right) {
        return left instanceof Number && right instanceof Number || left instanceof String && right instanceof String
                || left instanceof Boolean && right instanceof Boolean || left instanceof Map && right instanceof Map
                || left instanceof List && right instanceof List;
    }

    static List<String> changedPaths(Object before, Object after) {
        List<String> paths = new ArrayList<String>();
        collect(before, after, "", paths);
        return Collections.unmodifiableList(paths);
    }

    private static void collect(Object before, Object after, String path, List<String> paths) {
        if (Objects.equals(before, after)) {
            return;
        }
        if (before instanceof Map && after instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> oldValues = (Map<String, Object>) before;
            @SuppressWarnings("unchecked")
            Map<String, Object> newValues = (Map<String, Object>) after;
            for (String key : oldValues.keySet()) {
                collect(oldValues.get(key), newValues.get(key), child(path, key), paths);
            }
            for (String key : newValues.keySet()) {
                if (!oldValues.containsKey(key)) {
                    collect(null, newValues.get(key), child(path, key), paths);
                }
            }
        } else if (before instanceof List && after instanceof List) {
            List<?> oldValues = (List<?>) before;
            List<?> newValues = (List<?>) after;
            for (int index = 0; index < Math.max(oldValues.size(), newValues.size()); index++) {
                collect(index < oldValues.size() ? oldValues.get(index) : null,
                        index < newValues.size() ? newValues.get(index) : null, path + "[" + (index + 1) + "]", paths);
            }
        } else {
            paths.add(path);
        }
    }

    private static String child(String parent, String name) {
        return parent.isEmpty() ? name : parent + "." + name;
    }
}
