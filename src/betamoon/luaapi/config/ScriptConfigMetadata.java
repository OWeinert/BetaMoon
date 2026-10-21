package betamoon.luaapi.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Parses optional presentation and edit rules separately from persisted values.
 */
final class ScriptConfigMetadata {
    private static final int MAX_ARRAY_ITEMS = 64;

    final String label;
    final String description;
    final Map<String, ScriptConfigField> fields;
    final LuaValue onChange;
    final int schemaVersion;
    final LuaValue migrate;

    private ScriptConfigMetadata(String label, String description, Map<String, ScriptConfigField> fields,
            LuaValue onChange, int schemaVersion, LuaValue migrate) {
        this.label = label;
        this.description = description;
        this.fields = Collections.unmodifiableMap(fields);
        this.onChange = onChange;
        this.schemaVersion = schemaVersion;
        this.migrate = migrate;
    }

    static ScriptConfigMetadata parse(String key, Map<String, Object> defaults, LuaValue argument) {
        LuaTable metadata = argument.isnil() ? new LuaTable() : argument.checktable();
        checkKeys(metadata, "config options", "label", "description", "fields", "onChange", "schemaVersion",
                "migrate");
        LuaValue callback = metadata.get("onChange");
        if (!callback.isnil() && !callback.isfunction()) {
            throw new LuaError("config options.onChange must be a function");
        }
        LuaValue versionValue = metadata.get("schemaVersion");
        int schemaVersion = 0;
        if (!versionValue.isnil()) {
            if (!versionValue.isint() || versionValue.toint() < 1) {
                throw new LuaError("config options.schemaVersion must be a positive integer");
            }
            schemaVersion = versionValue.toint();
        }
        LuaValue migrate = metadata.get("migrate");
        if (!migrate.isnil() && (!migrate.isfunction() || schemaVersion == 0)) {
            throw new LuaError("config options.migrate requires schemaVersion and must be a function");
        }
        if (schemaVersion > 0 && defaults.containsKey(ScriptConfigStore.VERSION_FIELD)) {
            throw new LuaError("config defaults reserve '" + ScriptConfigStore.VERSION_FIELD + "' for schemaVersion");
        }
        LuaTable fieldMetadata = optionalTable(metadata.get("fields"), "config options.fields");
        for (LuaValue field : fieldMetadata.keys()) {
            if (!field.isstring() || !defaults.containsKey(field.tojstring())) {
                throw new LuaError("config options.fields contains an undeclared field: " + field);
            }
        }
        Map<String, ScriptConfigField> fields = new LinkedHashMap<String, ScriptConfigField>();
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            fields.put(entry.getKey(),
                    field(entry.getKey(), entry.getValue(), fieldMetadata.get(entry.getKey()), 0, false));
        }
        return new ScriptConfigMetadata(string(metadata, "label", key), string(metadata, "description", ""), fields,
                callback, schemaVersion, migrate);
    }

    /**
     * Supplies array shape hints before empty Lua default tables are snapshotted.
     */
    static Map<String, Object> defaultHints(LuaTable defaults, LuaValue argument) {
        LuaTable options = argument.isnil() ? new LuaTable() : argument.checktable();
        return shape(defaults, optionalTable(options.get("fields"), "config options.fields"));
    }

    private static Map<String, Object> shape(LuaTable defaults, LuaTable fields) {
        Map<String, Object> hints = new LinkedHashMap<String, Object>();
        for (LuaValue key : defaults.keys()) {
            if (!key.isstring()) {
                continue;
            }
            LuaValue value = defaults.get(key);
            if (!value.istable()) {
                continue;
            }
            LuaTable options = optionalTable(fields.get(key), key.tojstring());
            if (!options.get("element").isnil()) {
                hints.put(key.tojstring(), new ArrayList<Object>());
            } else {
                hints.put(key.tojstring(),
                        shape(value.checktable(), optionalTable(options.get("fields"), key.tojstring() + ".fields")));
            }
        }
        return hints;
    }

    private static ScriptConfigField field(String name, Object fallback, LuaValue argument, int depth,
            boolean elementField) {
        if (depth > 32) {
            throw new LuaError("Config UI metadata is too deeply nested");
        }
        LuaTable metadata = optionalTable(argument, name);
        checkKeys(metadata, name, "label", "description", "min", "max", "step", "input", "choices", "readOnly",
                "readOnlyReason", "fields", "element", "maxItems", "default", "kind", "key", "value", "expanded");
        if (!elementField && !metadata.get("default").isnil()) {
            throw new LuaError(name + ": default belongs in the defaults table, not field metadata");
        }
        Double minimum = number(metadata, "min", name);
        Double maximum = number(metadata, "max", name);
        Double step = number(metadata, "step", name);
        if ((minimum != null || maximum != null || step != null) && !(fallback instanceof Number)) {
            throw new LuaError(name + ": numeric bounds require a number default");
        }
        if (minimum != null && maximum != null && minimum.doubleValue() > maximum.doubleValue()) {
            throw new LuaError(name + ": min exceeds max");
        }
        if (step != null && step.doubleValue() <= 0) {
            throw new LuaError(name + ": step must be positive");
        }
        if (fallback instanceof Number) {
            double value = ((Number) fallback).doubleValue();
            if (minimum != null && value < minimum.doubleValue() || maximum != null && value > maximum.doubleValue()) {
                throw new LuaError(name + ": default is outside its declared bounds");
            }
        }
        List<ScriptConfigChoice> choices = choices(metadata.get("choices"), name, fallback);
        if (!choices.isEmpty() && !containsChoice(choices, fallback)) {
            throw new LuaError(name + ": choices must include the default");
        }
        String input = input(metadata.get("input"), fallback, choices, name);
        LuaValue expandedOption = metadata.get("expanded");
        boolean expanded = !expandedOption.isnil() && expandedOption.checkboolean();
        if (!expandedOption.isnil()
                && (!(fallback instanceof Map || fallback instanceof List) || "text".equals(input))) {
            throw new LuaError(name + ": expanded requires a nested section or collection control");
        }
        String collectionKind = null;
        if (("collection".equals(input) || "text".equals(input))
                && (fallback instanceof Map || fallback instanceof List)) {
            LuaValue kind = metadata.get("kind");
            if (kind.isnil()) {
                throw new LuaError(name + ": table input requires kind='list' or kind='dictionary'");
            }
            collectionKind = kind.checkjstring();
            if (fallback instanceof List && !"list".equals(collectionKind)
                    || fallback instanceof Map && !"dictionary".equals(collectionKind)) {
                throw new LuaError(name + ": kind must match its list or dictionary default");
            }
        } else if (!metadata.get("kind").isnil() || !metadata.get("key").isnil() || !metadata.get("value").isnil()) {
            throw new LuaError(name + ": kind, key, and value require a table with collection or text input");
        }
        if ("slider".equals(input)) {
            if (minimum == null || maximum == null || step == null || minimum.doubleValue() >= maximum.doubleValue()) {
                throw new LuaError(name + ": slider input requires min < max and a positive step");
            }
            double steps = (maximum.doubleValue() - minimum.doubleValue()) / step.doubleValue();
            if (!Double.isFinite(steps) || steps > 1000000 || !wholeSteps(steps)) {
                throw new LuaError(name + ": slider range must contain at most 1000000 whole steps");
            }
            if (!matchesStep(((Number) fallback).doubleValue(), minimum, step)) {
                throw new LuaError(name + ": slider default must match its declared step");
            }
        }
        for (ScriptConfigChoice choice : choices) {
            if (choice.value() instanceof Number) {
                double value = ((Number) choice.value()).doubleValue();
                if (minimum != null && value < minimum.doubleValue() || maximum != null && value > maximum.doubleValue()
                        || step != null && !matchesStep(value, minimum, step)) {
                    throw new LuaError(name + ": choice " + choice.label() + " violates numeric bounds or step");
                }
            }
        }
        boolean readOnly = metadata.get("readOnly").isnil() ? false : metadata.get("readOnly").checkboolean();
        String reason = string(metadata, "readOnlyReason", "");
        if (!reason.isEmpty() && !readOnly) {
            throw new LuaError(name + ": readOnlyReason requires readOnly=true");
        }

        Map<String, ScriptConfigField> children = new LinkedHashMap<String, ScriptConfigField>();
        ScriptConfigField element = null;
        ScriptConfigField keyField = null;
        ScriptConfigField valueField = null;
        int maxItems = MAX_ARRAY_ITEMS;
        if (fallback instanceof Map) {
            if (!metadata.get("element").isnil()
                    || !"dictionary".equals(collectionKind) && !metadata.get("maxItems").isnil()) {
                throw new LuaError(name + ": array metadata requires an array default");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) fallback;
            if ("dictionary".equals(collectionKind)) {
                if (!metadata.get("fields").isnil()) {
                    throw new LuaError(name + ": dictionary cannot declare fixed fields");
                }
                if (object.size() > MAX_ARRAY_ITEMS) {
                    throw new LuaError(name + ": collection defaults cannot exceed 64 entries");
                }
                if (metadata.get("key").isnil() || metadata.get("value").isnil()) {
                    throw new LuaError(name + ": dictionary collection requires key and value metadata");
                }
                LuaTable keyMetadata = metadata.get("key").checktable();
                checkKeys(keyMetadata, name + ".key", "input");
                keyField = field("key", "", keyMetadata, depth + 1, true);
                if (!"text".equals(keyField.input())) {
                    throw new LuaError(name + ".key: dictionary keys need text input");
                }
                LuaTable valueMetadata = metadata.get("value").checktable();
                LuaValue valueDefault = valueMetadata.get("default");
                if (valueDefault.isnil()) {
                    throw new LuaError(name + ".value: default is required for additions");
                }
                Object entryDefault = ScriptConfigValues.snapshotValue(valueDefault);
                if (!(entryDefault instanceof String || entryDefault instanceof Number
                        || entryDefault instanceof Boolean)) {
                    throw new LuaError(name + ".value: use a string, number, or boolean default");
                }
                valueField = field("value", entryDefault, valueMetadata, depth + 1, true);
                for (Map.Entry<String, Object> entry : object.entrySet()) {
                    if (!sameType(entryDefault, entry.getValue())) {
                        throw new LuaError(name + "." + entry.getKey() + ": value differs from value.default");
                    }
                    ScriptConfigEdits.validateField(valueField, entry.getValue(), entry.getValue(),
                            name + "." + entry.getKey());
                }
                maxItems = maxItems(metadata, name, object.size());
            } else {
                LuaTable nested = optionalTable(metadata.get("fields"), name + ".fields");
                for (LuaValue key : nested.keys()) {
                    if (!key.isstring() || !object.containsKey(key.tojstring())) {
                        throw new LuaError(name + ".fields contains an undeclared field: " + key);
                    }
                }
                for (Map.Entry<String, Object> entry : object.entrySet()) {
                    children.put(entry.getKey(),
                            field(entry.getKey(), entry.getValue(), nested.get(entry.getKey()), depth + 1, false));
                }
            }
        } else if (fallback instanceof List) {
            if (!metadata.get("fields").isnil()) {
                throw new LuaError(name + ": object fields require an object default");
            }
            if (!metadata.get("key").isnil() || !metadata.get("value").isnil()) {
                throw new LuaError(name + ": list collections use element metadata");
            }
            @SuppressWarnings("unchecked")
            List<Object> array = (List<Object>) fallback;
            if ("list".equals(collectionKind) && array.size() > MAX_ARRAY_ITEMS) {
                throw new LuaError(name + ": collection defaults cannot exceed 64 entries");
            }
            maxItems = maxItems(metadata, name, array.size());
            LuaValue elementValue = metadata.get("element");
            if ("list".equals(collectionKind) && elementValue.isnil()) {
                throw new LuaError(name + ": list collection requires element metadata");
            }
            if (!elementValue.isnil()) {
                LuaTable elementMetadata = elementValue.checktable();
                LuaValue elementDefault = elementMetadata.get("default");
                Object value = elementDefault.isnil()
                        ? (array.isEmpty() ? null : array.get(0))
                        : ScriptConfigValues.snapshotValue(elementDefault);
                if (value == null) {
                    throw new LuaError(name + ": an empty array needs element.default to allow additions");
                }
                element = field("item", value, elementValue, depth + 1, true);
                if ("text".equals(input)
                        && !(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                    throw new LuaError(name + ": text lists require string, number, or boolean elements");
                }
                if ("list".equals(collectionKind)) {
                    for (int index = 0; index < array.size(); index++) {
                        ScriptConfigEdits.validateField(element, array.get(index), array.get(index),
                                name + "[" + (index + 1) + "]");
                    }
                }
            } else if (!array.isEmpty()) {
                element = field("item", array.get(0), LuaValue.NIL, depth + 1, true);
            }
        } else if (!metadata.get("fields").isnil() || !metadata.get("element").isnil()
                || !metadata.get("maxItems").isnil()) {
            throw new LuaError(name + ": nested metadata requires an object or array default");
        }
        return new ScriptConfigField(name, string(metadata, "label", name), string(metadata, "description", ""),
                fallback, minimum, maximum, step, input, collectionKind, choices, expanded, readOnly, reason, maxItems,
                element, keyField, valueField, children);
    }

    private static int maxItems(LuaTable metadata, String name, int defaults) {
        LuaValue max = metadata.get("maxItems");
        if (max.isnil()) {
            return Math.max(MAX_ARRAY_ITEMS, defaults);
        }
        int result = max.checkint();
        if (result < defaults || result < 1 || result > MAX_ARRAY_ITEMS) {
            throw new LuaError(name + ": maxItems must hold defaults and be between 1 and 64");
        }
        return result;
    }

    private static boolean sameType(Object left, Object right) {
        return left instanceof String && right instanceof String || left instanceof Number && right instanceof Number
                || left instanceof Boolean && right instanceof Boolean;
    }

    private static LuaTable optionalTable(LuaValue value, String path) {
        return value.isnil() ? new LuaTable() : value.checktable();
    }

    private static String string(LuaTable table, String key, String fallback) {
        LuaValue value = table.get(key);
        return value.isnil() ? fallback : value.checkjstring();
    }

    private static Double number(LuaTable table, String key, String path) {
        LuaValue value = table.get(key);
        if (value.isnil()) {
            return null;
        }
        double number = value.checkdouble();
        if (!Double.isFinite(number)) {
            throw new LuaError(path + "." + key + " must be finite");
        }
        return Double.valueOf(number);
    }

    private static List<ScriptConfigChoice> choices(LuaValue value, String path, Object fallback) {
        List<ScriptConfigChoice> result = new ArrayList<ScriptConfigChoice>();
        if (value.isnil()) {
            return result;
        }
        if (!(fallback instanceof String) && !(fallback instanceof Number)) {
            throw new LuaError(path + ": choices require a string or number default");
        }
        LuaTable table = value.checktable();
        if (table.keys().length != table.length() || table.length() == 0) {
            throw new LuaError(path + ".choices must be a nonempty dense array");
        }
        for (int index = 1; index <= table.length(); index++) {
            LuaValue entry = table.get(index);
            String label = null;
            LuaValue selected = entry;
            if (entry.istable()) {
                LuaTable option = entry.checktable();
                checkKeys(option, path + ".choices[" + index + "]", "value", "label");
                selected = option.get("value");
                if (selected.isnil()) {
                    throw new LuaError(path + ".choices[" + index + "] needs a value");
                }
                label = string(option, "label", null);
                if (label != null && label.isEmpty()) {
                    throw new LuaError(path + ".choices[" + index + "].label must not be empty");
                }
            }
            Object choiceValue;
            if (fallback instanceof Number && selected.isnumber()) {
                double number = selected.todouble();
                if (!Double.isFinite(number)) {
                    throw new LuaError(path + ".choices[" + index + "] must be finite");
                }
                choiceValue = Double.valueOf(number);
            } else if (fallback instanceof String && selected.isstring()) {
                choiceValue = selected.tojstring();
            } else {
                throw new LuaError(path + ".choices[" + index + "] must match the default's type");
            }
            if (containsChoice(result, choiceValue)) {
                throw new LuaError(path + ".choices has a duplicate: " + choiceValue);
            }
            String display = label == null
                    ? choiceValue instanceof Number
                            ? BigDecimal.valueOf(((Number) choiceValue).doubleValue()).stripTrailingZeros()
                                    .toPlainString()
                            : (String) choiceValue
                    : label;
            result.add(new ScriptConfigChoice(choiceValue, display));
        }
        return result;
    }

    private static String input(LuaValue argument, Object fallback, List<ScriptConfigChoice> choices, String path) {
        if (argument.isnil()) {
            return fallback instanceof Boolean
                    ? "cycle"
                    : fallback instanceof String || fallback instanceof Number ? "text" : null;
        }
        String method = argument.checkjstring();
        if (fallback instanceof Number
                && !("text".equals(method) || "slider".equals(method) || "choice".equals(method)
                        || "cycle".equals(method))
                || fallback instanceof String
                        && !("text".equals(method) || "choice".equals(method) || "cycle".equals(method))
                || fallback instanceof Boolean && !"cycle".equals(method)
                || (fallback instanceof Map || fallback instanceof List)
                        && !("collection".equals(method) || "text".equals(method))) {
            throw new LuaError(path + ": unsupported input method '" + method + "' for this value type");
        }
        if (("choice".equals(method) || "cycle".equals(method)) && !(fallback instanceof Boolean)
                && choices.isEmpty()) {
            throw new LuaError(path + ": " + method + " input requires choices");
        }
        return method;
    }

    private static boolean containsChoice(List<ScriptConfigChoice> choices, Object value) {
        for (ScriptConfigChoice choice : choices) {
            if (choice.matches(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesStep(double value, Double minimum, Double step) {
        double origin = minimum == null ? 0.0D : minimum.doubleValue();
        return wholeSteps((value - origin) / step.doubleValue());
    }

    private static boolean wholeSteps(double steps) {
        return Double.isFinite(steps) && Math.abs(steps - Math.rint(steps)) <= 0.000001D;
    }

    private static void checkKeys(LuaTable table, String path, String... allowed) {
        for (LuaValue key : table.keys()) {
            if (!key.isstring()) {
                throw new LuaError(path + " must have string keys");
            }
            boolean found = false;
            for (String candidate : allowed) {
                found |= candidate.equals(key.tojstring());
            }
            if (!found) {
                throw new LuaError(path + " has unknown option '" + key.tojstring() + "'");
            }
        }
    }
}
