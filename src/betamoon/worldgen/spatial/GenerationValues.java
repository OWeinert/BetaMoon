package betamoon.worldgen.spatial;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Strict JSON-compatible declaration values shared by generation frontends. */
public final class GenerationValues {
    private GenerationValues() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value, String path) {
        if (!(value instanceof Map)) {
            throw error(path, "expected an object");
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object value, String path, int min, int max) {
        if (value instanceof Map && ((Map<?, ?>) value).isEmpty() && min == 0) {
            return Collections.emptyList();
        }
        if (!(value instanceof List) || ((List<?>) value).size() < min || ((List<?>) value).size() > max) {
            throw error(path, "expected " + min + ".." + max + " entries");
        }
        return (List<Object>) value;
    }

    public static void fields(Map<String, Object> value, String path, String... allowed) {
        List<String> names = Arrays.asList(allowed);
        for (String key : value.keySet()) {
            if (!names.contains(key)) {
                throw error(path + "." + key, "unknown field");
            }
        }
    }

    public static Object required(Map<String, Object> value, String key, String path) {
        if (!value.containsKey(key) || value.get(key) == null) {
            throw error(path + "." + key, "required field");
        }
        return value.get(key);
    }

    public static double number(Object value, String path) {
        if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())) {
            throw error(path, "expected a finite number");
        }
        return ((Number) value).doubleValue();
    }

    public static double number(Map<String, Object> value, String key, double fallback, String path) {
        return value.containsKey(key) ? number(value.get(key), path + "." + key) : fallback;
    }

    public static int integer(Object value, String path, int min, int max) {
        double number = number(value, path);
        if (number != Math.floor(number) || number < min || number > max) {
            throw error(path, "expected integer " + min + ".." + max);
        }
        return (int) number;
    }

    public static String string(Object value, String path) {
        if (!(value instanceof String) || ((String) value).isEmpty() || ((String) value).length() > 1024) {
            throw error(path, "expected a nonempty string of at most 1024 characters");
        }
        return (String) value;
    }

    public static String string(Map<String, Object> value, String key, String fallback, String path) {
        return value.containsKey(key) ? string(value.get(key), path + "." + key) : fallback;
    }

    public static boolean bool(Map<String, Object> value, String key, boolean fallback, String path) {
        if (!value.containsKey(key)) {
            return fallback;
        }
        if (!(value.get(key) instanceof Boolean)) {
            throw error(path + "." + key, "expected a boolean");
        }
        return (Boolean) value.get(key);
    }

    public static Object freeze(Object value) {
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Object> entry : object(value, "definition").entrySet()) {
                result.put(entry.getKey(), freeze(entry.getValue()));
            }
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<Object>();
            for (Object entry : (List<?>) value) {
                result.add(freeze(entry));
            }
            return Collections.unmodifiableList(result);
        }
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        return number(value, "definition");
    }

    public static String canonical(Object value) {
        if (value instanceof Map) {
            StringBuilder result = new StringBuilder("{");
            for (Map.Entry<String, Object> entry : new TreeMap<String, Object>(object(value, "definition"))
                    .entrySet()) {
                result.append(canonical(entry.getKey())).append(canonical(entry.getValue()));
            }
            return result.append('}').toString();
        }
        if (value instanceof List) {
            StringBuilder result = new StringBuilder("[");
            for (Object entry : (List<?>) value) {
                result.append(canonical(entry));
            }
            return result.append(']').toString();
        }
        String text = value instanceof Number
                ? Double.toHexString(((Number) value).doubleValue())
                : String.valueOf(value);
        return (value == null ? "n" : value instanceof String ? "s" : value instanceof Boolean ? "b" : "d")
                + text.length() + ":" + text;
    }

    public static IllegalArgumentException error(String path, String message) {
        return new IllegalArgumentException(path + ": " + message);
    }
}
