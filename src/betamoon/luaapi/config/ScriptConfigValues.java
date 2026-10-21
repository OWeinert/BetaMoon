package betamoon.luaapi.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Converts JSON-compatible config values without evaluating file contents. */
final class ScriptConfigValues {
    private static final int MAX_DEPTH = 32;
    private static final int MAX_VALUES = 10000;

    private final IdentityHashMap<LuaTable, Boolean> visiting = new IdentityHashMap<LuaTable, Boolean>();
    private int count;

    private ScriptConfigValues() {
    }

    static Map<String, Object> snapshot(LuaTable table, Map<String, Object> defaults) {
        ScriptConfigValues converter = new ScriptConfigValues();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) converter.fromLua(table, "config", 0, defaults, true);
        reserved(result);
        return result;
    }

    static Object snapshotValue(LuaValue value) {
        LuaTable wrapper = new LuaTable();
        wrapper.set("value", value);
        return snapshot(wrapper, null).get("value");
    }

    static Map<String, Object> copy(Map<String, Object> source) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) copyValue(source);
        return result;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) value;
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                result.put(entry.getKey(), copyValue(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<Object>();
            for (Object entry : (List<?>) value) {
                result.add(copyValue(entry));
            }
            return result;
        }
        return value;
    }

    private Object fromLua(LuaValue value, String path, int depth, Object expected, boolean root) {
        if (depth > MAX_DEPTH || ++count > MAX_VALUES) {
            throw new LuaError(path + ": config nesting or value count exceeds limits");
        }
        if (value.isboolean()) {
            return Boolean.valueOf(value.toboolean());
        }
        if (value.isnumber()) {
            double number = value.todouble();
            if (!Double.isFinite(number)) {
                throw new LuaError(path + ": number must be finite");
            }
            return Double.valueOf(number);
        }
        if (value.isstring()) {
            return value.tojstring();
        }
        if (!value.istable()) {
            throw new LuaError(path + ": expected a boolean, number, string, or table");
        }
        LuaTable table = value.checktable();
        if (visiting.put(table, Boolean.TRUE) != null) {
            throw new LuaError(path + ": cyclic config tables are unsupported");
        }
        try {
            LuaValue[] keys = table.keys();
            if (keys.length == 0) {
                return !root && expected instanceof List
                        ? new ArrayList<Object>()
                        : new LinkedHashMap<String, Object>();
            }
            boolean numeric = keys[0].isint() && keys[0].toint() >= 1;
            if (numeric && !root) {
                int maximum = 0;
                for (LuaValue key : keys) {
                    if (!key.isint() || key.toint() < 1) {
                        throw new LuaError(path + ": arrays must have only positive integer keys");
                    }
                    maximum = Math.max(maximum, key.toint());
                }
                if (maximum != keys.length) {
                    throw new LuaError(path + ": arrays must be dense and start at 1");
                }
                List<Object> result = new ArrayList<Object>();
                for (int index = 1; index <= maximum; index++) {
                    result.add(fromLua(table.rawget(index), path + "[" + index + "]", depth + 1, null, false));
                }
                return result;
            }
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            Map<String, LuaValue> ordered = new TreeMap<String, LuaValue>();
            for (LuaValue key : keys) {
                if (!key.isstring()) {
                    throw new LuaError(path + ": object fields must have string keys");
                }
                String field = key.checkjstring();
                if (field.isEmpty()) {
                    throw new LuaError(path + ": field names cannot be empty");
                }
                ordered.put(field, table.rawget(key));
            }
            for (Map.Entry<String, LuaValue> entry : ordered.entrySet()) {
                Object expectedField = expected instanceof Map ? ((Map<?, ?>) expected).get(entry.getKey()) : null;
                result.put(entry.getKey(),
                        fromLua(entry.getValue(), path + "." + entry.getKey(), depth + 1, expectedField, false));
            }
            return result;
        } finally {
            visiting.remove(table);
        }
    }

    static Map<String, Object> merge(Map<String, Object> defaults, Map<String, Object> loaded,
            Map<String, ScriptConfigField> fields) {
        reserved(loaded);
        checkJson(loaded, "config", 0, new int[]{0});
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) mergeValue(defaults, loaded, "config", null, fields);
        return result;
    }

    private static void checkJson(Object value, String path, int depth, int[] count) {
        if (depth > MAX_DEPTH || ++count[0] > MAX_VALUES) {
            throw new LuaError(path + ": config nesting or value count exceeds limits");
        }
        if (value == null) {
            throw new LuaError(path + ": JSON null is unsupported; use a default value or remove the field");
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) value;
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                checkJson(entry.getValue(), path + "." + entry.getKey(), depth + 1, count);
            }
        } else if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> array = (List<Object>) value;
            for (int index = 0; index < array.size(); index++) {
                checkJson(array.get(index), path + "[" + (index + 1) + "]", depth + 1, count);
            }
        }
    }

    private static Object mergeValue(Object fallback, Object value, String path, ScriptConfigField field,
            Map<String, ScriptConfigField> children) {
        if (fallback instanceof Map) {
            if (!(value instanceof Map)) {
                throw new LuaError(path + ": expected an object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> declared = (Map<String, Object>) fallback;
            @SuppressWarnings("unchecked")
            Map<String, Object> actual = (Map<String, Object>) value;
            Map<String, Object> merged = new LinkedHashMap<String, Object>();
            if (field != null && "dictionary".equals(field.collectionKind())) {
                for (Map.Entry<String, Object> entry : actual.entrySet()) {
                    if (entry.getKey().isEmpty()) {
                        throw new LuaError(path + ": dictionary keys cannot be empty");
                    }
                    ScriptConfigField descriptor = field.valueField();
                    merged.put(entry.getKey(), mergeValue(descriptor.defaultValue(), entry.getValue(),
                            path + "." + entry.getKey(), descriptor, descriptor.children()));
                }
                return merged;
            }
            for (Map.Entry<String, Object> entry : actual.entrySet()) {
                Object defaultValue = declared.get(entry.getKey());
                ScriptConfigField child = children == null ? null : children.get(entry.getKey());
                merged.put(entry.getKey(),
                        defaultValue == null
                                ? entry.getValue()
                                : mergeValue(defaultValue, entry.getValue(), path + "." + entry.getKey(), child,
                                        child == null ? null : child.children()));
            }
            for (Map.Entry<String, Object> entry : declared.entrySet()) {
                if (!actual.containsKey(entry.getKey())) {
                    merged.put(entry.getKey(), entry.getValue());
                }
            }
            return merged;
        }
        if (fallback instanceof List && value instanceof List && field != null
                && "list".equals(field.collectionKind())) {
            List<Object> merged = new ArrayList<Object>();
            ScriptConfigField descriptor = field.element();
            for (Object item : (List<?>) value) {
                merged.add(mergeValue(descriptor.defaultValue(), item, path + "[" + (merged.size() + 1) + "]",
                        descriptor, descriptor.children()));
            }
            return merged;
        }
        if (fallback instanceof List && value instanceof List || fallback instanceof String && value instanceof String
                || fallback instanceof Boolean && value instanceof Boolean
                || fallback instanceof Number && value instanceof Number) {
            return value;
        }
        throw new LuaError(path + ": value has a different type from its default");
    }

    private static void reserved(Map<String, Object> object) {
        for (String name : Arrays.asList("save", "reload")) {
            if (object.containsKey(name)) {
                throw new LuaError("Config field '" + name + "' is reserved for a config method");
            }
        }
    }

    static LuaValue toLua(Object value) {
        if (value instanceof Map) {
            LuaTable result = new LuaTable();
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) value;
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                result.rawset(LuaValue.valueOf(entry.getKey()), toLua(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List) {
            LuaTable result = new LuaTable();
            @SuppressWarnings("unchecked")
            List<Object> array = (List<Object>) value;
            for (int index = 0; index < array.size(); index++) {
                result.rawset(index + 1, toLua(array.get(index)));
            }
            return result;
        }
        if (value instanceof String) {
            return LuaValue.valueOf((String) value);
        }
        if (value instanceof Boolean) {
            return LuaValue.valueOf(((Boolean) value).booleanValue());
        }
        if (value instanceof Number) {
            return LuaValue.valueOf(((Number) value).doubleValue());
        }
        throw new LuaError("Unsupported config value");
    }

    static void update(LuaTable target, LuaTable loaded) {
        for (LuaValue key : target.keys()) {
            if (loaded.rawget(key).isnil()) {
                target.rawset(key, LuaValue.NIL);
            }
        }
        for (LuaValue key : loaded.keys()) {
            LuaValue oldValue = target.rawget(key);
            LuaValue newValue = loaded.rawget(key);
            if (oldValue.istable() && newValue.istable()) {
                update(oldValue.checktable(), newValue.checktable());
            } else {
                target.rawset(key, newValue);
            }
        }
    }

    static byte[] json(Map<String, Object> value) {
        StringBuilder text = new StringBuilder();
        append(text, value, 0);
        text.append('\n');
        return text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void append(StringBuilder text, Object value, int depth) {
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) value;
            text.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : new TreeMap<String, Object>(object).entrySet()) {
                if (!first) {
                    text.append(',');
                }
                first = false;
                text.append('\n');
                indent(text, depth + 1);
                quote(text, entry.getKey());
                text.append(": ");
                append(text, entry.getValue(), depth + 1);
            }
            if (!object.isEmpty()) {
                text.append('\n');
                indent(text, depth);
            }
            text.append('}');
            return;
        }
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> array = (List<Object>) value;
            text.append('[');
            for (int index = 0; index < array.size(); index++) {
                if (index > 0) {
                    text.append(", ");
                }
                append(text, array.get(index), depth + 1);
            }
            text.append(']');
            return;
        }
        if (value instanceof String) {
            quote(text, (String) value);
        } else {
            text.append(value);
        }
    }

    private static void indent(StringBuilder text, int depth) {
        for (int index = 0; index < depth; index++) {
            text.append("  ");
        }
    }

    private static void quote(StringBuilder text, String value) {
        text.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"' || character == '\\') {
                text.append('\\').append(character);
            } else if (character < 32) {
                String digits = Integer.toHexString(character);
                text.append("\\u");
                for (int padding = digits.length(); padding < 4; padding++) {
                    text.append('0');
                }
                text.append(digits);
            } else {
                text.append(character);
            }
        }
        text.append('"');
    }
}
