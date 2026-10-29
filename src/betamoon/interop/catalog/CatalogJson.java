package betamoon.interop.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Small deterministic metadata writer, independent of Minecraft JSON classes and Java object serialization. */
public final class CatalogJson {
    private CatalogJson() {
    }

    public static String encode(Object value) {
        StringBuilder output = new StringBuilder();
        append(output, value, 0);
        return output.toString();
    }

    public static Object decode(String text) {
        if (text == null || text.length() > 16 * 1024 * 1024) {
            throw new IllegalArgumentException("Catalog JSON text limit exceeded");
        }
        Parser parser = new Parser(text);
        Object result = parser.readValue(0);
        parser.skipSpace();
        if (parser.index != text.length()) {
            throw parser.invalid();
        }
        return result;
    }

    private static void append(StringBuilder output, Object value, int depth) {
        if (depth > 32) {
            throw new IllegalArgumentException("Catalog JSON depth limit exceeded");
        }
        if (value == null) {
            output.append("null");
        } else if (value instanceof String) {
            appendString(output, (String) value);
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte) {
            output.append(value);
        } else if (value instanceof Double || value instanceof Float) {
            if (!Double.isFinite(((Number) value).doubleValue())) {
                throw new IllegalArgumentException("JSON numbers must be finite");
            }
            output.append(value);
        } else if (value instanceof Map) {
            output.append('{');
            boolean first = true;
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException("JSON object keys must be strings");
                }
                sorted.put((String) entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) {
                    output.append(',');
                }
                first = false;
                appendString(output, (String) entry.getKey());
                output.append(':');
                append(output, entry.getValue(), depth + 1);
            }
            output.append('}');
        } else if (value instanceof Iterable) {
            output.append('[');
            boolean first = true;
            for (Object element : (Iterable<?>) value) {
                if (!first) {
                    output.append(',');
                }
                first = false;
                append(output, element, depth + 1);
            }
            output.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported catalog JSON value: " + value.getClass());
        }
    }

    private static void appendString(StringBuilder output, String value) {
        output.append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '"' || character == '\\') {
                output.append('\\').append(character);
            } else if (character < 32 || Character.isSurrogate(character)) {
                String hex = Integer.toHexString(character);
                output.append("\\u");
                for (int padding = hex.length(); padding < 4; padding++) {
                    output.append('0');
                }
                output.append(hex);
            } else {
                output.append(character);
            }
        }
        output.append('"');
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text;
        }

        private Object readValue(int depth) {
            if (depth > 32) {
                throw invalid();
            }
            skipSpace();
            if (index == text.length()) {
                throw invalid();
            }
            char first = text.charAt(index);
            if (first == '"') {
                return readString();
            }
            if (first == '{') {
                index++;
                Map<String, Object> result = new LinkedHashMap<>();
                if (take('}')) {
                    return result;
                }
                do {
                    skipSpace();
                    String key = readString();
                    if (result.containsKey(key) || !take(':')) {
                        throw invalid();
                    }
                    result.put(key, readValue(depth + 1));
                    if (take('}')) {
                        return result;
                    }
                } while (take(','));
                throw invalid();
            }
            if (first == '[') {
                index++;
                List<Object> result = new ArrayList<>();
                if (take(']')) {
                    return result;
                }
                do {
                    result.add(readValue(depth + 1));
                    if (take(']')) {
                        return result;
                    }
                } while (take(','));
                throw invalid();
            }
            if (text.startsWith("true", index)) {
                index += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", index)) {
                index += 5;
                return Boolean.FALSE;
            }
            if (text.startsWith("null", index)) {
                index += 4;
                return null;
            }
            return readNumber();
        }

        private String readString() {
            if (index >= text.length() || text.charAt(index++) != '"') {
                throw invalid();
            }
            StringBuilder result = new StringBuilder();
            while (index < text.length()) {
                char value = text.charAt(index++);
                if (value == '"') {
                    return result.toString();
                }
                if (value < 32) {
                    throw invalid();
                }
                if (value == '\\') {
                    if (index == text.length()) {
                        throw invalid();
                    }
                    char escape = text.charAt(index++);
                    int simple = "\"\\/bfnrt".indexOf(escape);
                    if (simple >= 0) {
                        result.append("\"\\/\b\f\n\r\t".charAt(simple));
                    } else if (escape == 'u') {
                        if (index + 4 > text.length()) {
                            throw invalid();
                        }
                        int character = 0;
                        for (int digit = 0; digit < 4; digit++) {
                            char digitValue = text.charAt(index++);
                            int hex = "0123456789abcdef".indexOf(Character.toLowerCase(digitValue));
                            if (hex < 0) {
                                throw invalid();
                            }
                            character = character * 16 + hex;
                        }
                        result.append((char) character);
                    } else {
                        throw invalid();
                    }
                } else {
                    result.append(value);
                }
            }
            throw invalid();
        }

        private Number readNumber() {
            int start = index;
            while (index < text.length() && "-+0123456789.eE".indexOf(text.charAt(index)) >= 0) {
                index++;
            }
            String number = text.substring(start, index);
            if (number.length() > 128 || !number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
                throw invalid();
            }
            try {
                if (number.indexOf('.') < 0 && number.indexOf('e') < 0 && number.indexOf('E') < 0) {
                    return Long.valueOf(number);
                }
                double value = Double.parseDouble(number);
                if (!Double.isFinite(value)) {
                    throw invalid();
                }
                return value;
            } catch (NumberFormatException exception) {
                throw invalid();
            }
        }

        private boolean take(char expected) {
            skipSpace();
            if (index < text.length() && text.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void skipSpace() {
            while (index < text.length() && " \t\r\n".indexOf(text.charAt(index)) >= 0) {
                index++;
            }
        }

        private IllegalArgumentException invalid() {
            return new IllegalArgumentException("Invalid catalog JSON at character " + index);
        }
    }
}
