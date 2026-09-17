package betamoon.assets.model;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict, bounded JSON input for exported assets; never evaluates expressions.
 */
public final class ModelJson {
    private final String text;
    private int cursor;
    private int nodes;

    private ModelJson(String text) {
        this.text = text;
    }

    public static Map<String, Object> read(byte[] bytes) throws IOException {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            ModelJson reader = new ModelJson(text);
            if (text.startsWith("\ufeff")) {
                reader.cursor++;
            }
            Object result = reader.value(0);
            reader.space();
            if (reader.cursor != text.length()) {
                throw reader.error("Trailing content");
            }
            return object(result, "root");
        } catch (CharacterCodingException error) {
            throw new IOException("Model JSON must be valid UTF-8", error);
        }
    }

    private Object value(int depth) throws IOException {
        if (depth > 48 || ++nodes > 150000) {
            throw error("JSON nesting or value count exceeds asset limits");
        }
        space();
        if (cursor == text.length()) {
            throw error("Expected value");
        }
        char start = text.charAt(cursor);
        if (start == '"') {
            return string();
        }
        if (start == '{' || start == '[') {
            cursor++;
            Map<String, Object> object = new LinkedHashMap<>();
            List<Object> array = new ArrayList<>();
            char end = start == '{' ? '}' : ']';
            space();
            if (!take(end)) {
                do {
                    space();
                    if (start == '{') {
                        String key = string();
                        space();
                        expect(':');
                        if (object.containsKey(key)) {
                            throw error("Duplicate field " + key);
                        }
                        object.put(key, value(depth + 1));
                    } else {
                        array.add(value(depth + 1));
                    }
                    space();
                } while (take(','));
                expect(end);
            }
            return start == '{' ? object : array;
        }
        for (String literal : Arrays.asList("true", "false", "null")) {
            if (text.startsWith(literal, cursor)) {
                cursor += literal.length();
                return literal.equals("null") ? null : Boolean.valueOf(literal);
            }
        }
        int begin = cursor;
        while (cursor < text.length() && "-+0123456789.eE".indexOf(text.charAt(cursor)) >= 0) {
            cursor++;
        }
        String number = text.substring(begin, cursor);
        if (!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
            throw error("Expected JSON number");
        }
        double result = Double.parseDouble(number);
        if (!Double.isFinite(result)) {
            throw error("Number must be finite");
        }
        return result;
    }

    private String string() throws IOException {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (cursor < text.length()) {
            char next = text.charAt(cursor++);
            if (next == '"') {
                return result.toString();
            }
            if (next < 32) {
                throw error("Control character in string");
            }
            if (next == '\\') {
                if (cursor == text.length()) {
                    throw error("Unfinished escape");
                }
                next = text.charAt(cursor++);
                int escape = "\"\\/bfnrt".indexOf(next);
                if (escape >= 0) {
                    next = "\"\\/\b\f\n\r\t".charAt(escape);
                } else if (next == 'u' && cursor + 4 <= text.length()) {
                    try {
                        String digits = text.substring(cursor, cursor + 4);
                        if (!digits.matches("[0-9a-fA-F]{4}")) {
                            throw new NumberFormatException();
                        }
                        next = (char) Integer.parseInt(digits, 16);
                    } catch (NumberFormatException error) {
                        throw error("Invalid Unicode escape");
                    }
                    cursor += 4;
                } else {
                    throw error("Invalid escape");
                }
            }
            result.append(next);
        }
        throw error("Unfinished string");
    }

    private void space() {
        while (cursor < text.length() && " \r\n\t".indexOf(text.charAt(cursor)) >= 0) {
            cursor++;
        }
    }

    private boolean take(char value) {
        if (cursor < text.length() && text.charAt(cursor) == value) {
            cursor++;
            return true;
        }
        return false;
    }

    private void expect(char value) throws IOException {
        if (!take(value)) {
            throw error("Expected '" + value + "'");
        }
    }

    private IOException error(String message) {
        return new IOException(message + " at JSON character " + cursor);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value, String path) throws IOException {
        if (!(value instanceof Map)) {
            throw new IOException(path + ": expected object");
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object value, String path) throws IOException {
        if (!(value instanceof List)) {
            throw new IOException(path + ": expected array");
        }
        return (List<Object>) value;
    }

    public static String name(Object value, String path) throws IOException {
        if (!(value instanceof String) || ((String) value).isEmpty() || ((String) value).length() > 256) {
            throw new IOException(path + ": expected nonempty string up to 256 characters");
        }
        return (String) value;
    }

    public static double number(Object value, String path) throws IOException {
        if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())
                || Math.abs(((Number) value).doubleValue()) > 1000000) {
            throw new IOException(
                    path + ": expected finite numeric constant within +/-1000000; expressions are unsupported");
        }
        return ((Number) value).doubleValue();
    }

    public static boolean bool(Object value, String path) throws IOException {
        if (!(value instanceof Boolean)) {
            throw new IOException(path + ": expected boolean");
        }
        return (Boolean) value;
    }

    public static void fields(Map<String, Object> object, String path, String... supported) throws IOException {
        List<String> names = Arrays.asList(supported);
        for (String field : object.keySet()) {
            if (!names.contains(field)) {
                throw new IOException(path + "." + field + ": unsupported field");
            }
        }
    }
}
