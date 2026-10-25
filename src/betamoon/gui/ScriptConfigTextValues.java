package betamoon.gui;

import betamoon.luaapi.config.ScriptConfigField;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a compact config field without changing its stored Lua or JSON
 * shape.
 */
final class ScriptConfigTextValues {
    private ScriptConfigTextValues() {
    }

    static String format(Object value, ScriptConfigField field) {
        List<String> entries = new ArrayList<String>();
        if ("list".equals(field.collectionKind())) {
            for (Object item : (List<?>) value) {
                entries.add(formatScalar(item));
            }
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Object> dictionary = (Map<String, Object>) value;
            for (Map.Entry<String, Object> entry : dictionary.entrySet()) {
                entries.add(formatString(entry.getKey()) + ":" + formatScalar(entry.getValue()));
            }
        }
        StringBuilder result = new StringBuilder();
        for (String entry : entries) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(entry);
        }
        return result.toString();
    }

    static Object parse(String text, ScriptConfigField field) {
        if (text.trim().isEmpty()) {
            return "list".equals(field.collectionKind())
                    ? new ArrayList<Object>()
                    : new LinkedHashMap<String, Object>();
        }
        List<String> entries = split(text, ',', field.name());
        if (entries.size() > field.maxItems()) {
            throw new IllegalArgumentException(field.label() + " allows at most " + field.maxItems() + " entries.");
        }
        if ("list".equals(field.collectionKind())) {
            List<Object> values = new ArrayList<Object>();
            for (String entry : entries) {
                values.add(parseScalar(entry, field.element(), field.label() + " item " + (values.size() + 1)));
            }
            return values;
        }
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        for (String entry : entries) {
            List<String> pair = split(entry, ':', field.label() + " pair");
            if (pair.size() != 2) {
                throw new IllegalArgumentException(field.label() + ": use key:value for each pair.");
            }
            String key = decode(pair.get(0), field.label() + " key");
            if (key.isEmpty()) {
                throw new IllegalArgumentException(field.label() + ": dictionary keys cannot be empty.");
            }
            if (values.containsKey(key)) {
                throw new IllegalArgumentException(field.label() + ": duplicate key '" + key + "'.");
            }
            values.put(key, parseScalar(pair.get(1), field.valueField(), field.label() + "." + key));
        }
        return values;
    }

    private static Object parseScalar(String source, ScriptConfigField field, String path) {
        String value = decode(source, path);
        Object fallback = field.defaultValue();
        Object result;
        if (fallback instanceof String) {
            result = value;
        } else if (fallback instanceof Number) {
            try {
                double number = Double.parseDouble(value);
                if (!Double.isFinite(number)) {
                    throw new NumberFormatException(value);
                }
                result = Double.valueOf(number);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException(path + ": enter a finite number.");
            }
            double number = ((Double) result).doubleValue();
            if (field.minimum() != null && number < field.minimum().doubleValue()
                    || field.maximum() != null && number > field.maximum().doubleValue()) {
                throw new IllegalArgumentException(path + ": number is outside its bounds.");
            }
            if (field.step() != null) {
                double origin = field.minimum() == null ? 0 : field.minimum().doubleValue();
                double steps = (number - origin) / field.step().doubleValue();
                if (Math.abs(steps - Math.rint(steps)) > 0.000001) {
                    throw new IllegalArgumentException(path + ": number does not match its step.");
                }
            }
        } else if (fallback instanceof Boolean) {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new IllegalArgumentException(path + ": enter True or False.");
            }
            result = Boolean.valueOf(value);
        } else {
            throw new IllegalArgumentException(path + ": compact text requires scalar values.");
        }
        if (!field.choices().isEmpty() && field.choice(result) == null) {
            throw new IllegalArgumentException(path + ": value is not among its choices.");
        }
        return result;
    }

    private static List<String> split(String source, char separator, String path) {
        List<String> pieces = new ArrayList<String>();
        boolean quoted = false;
        boolean escaped = false;
        int start = 0;
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    quoted = false;
                }
            } else if (character == '"') {
                quoted = true;
            } else if (character == separator) {
                pieces.add(source.substring(start, index));
                start = index + 1;
            }
        }
        if (quoted || escaped) {
            throw new IllegalArgumentException(path + ": close the quoted value.");
        }
        pieces.add(source.substring(start));
        return pieces;
    }

    private static String decode(String source, String path) {
        String trimmed = source.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(path + ": empty values must be written as \"\".");
        }
        if (trimmed.charAt(0) != '"') {
            if (trimmed.indexOf('"') >= 0) {
                throw new IllegalArgumentException(path + ": quote the complete value.");
            }
            return trimmed;
        }
        if (trimmed.length() < 2 || trimmed.charAt(trimmed.length() - 1) != '"') {
            throw new IllegalArgumentException(path + ": close the quoted value.");
        }
        StringBuilder value = new StringBuilder();
        for (int index = 1; index < trimmed.length() - 1; index++) {
            char character = trimmed.charAt(index);
            if (character == '"') {
                throw new IllegalArgumentException(path + ": escape embedded quotes with a backslash.");
            }
            if (character != '\\') {
                value.append(character);
                continue;
            }
            if (++index >= trimmed.length() - 1) {
                throw new IllegalArgumentException(path + ": incomplete escape sequence.");
            }
            char escaped = trimmed.charAt(index);
            if (escaped == 'n') {
                value.append('\n');
            } else if (escaped == 'r') {
                value.append('\r');
            } else if (escaped == 't') {
                value.append('\t');
            } else if (escaped == '"' || escaped == '\\') {
                value.append(escaped);
            } else if (escaped == 'u' && index + 4 < trimmed.length() - 1) {
                try {
                    value.append((char) Integer.parseInt(trimmed.substring(index + 1, index + 5), 16));
                    index += 4;
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException(path + ": invalid Unicode escape.");
                }
            } else {
                throw new IllegalArgumentException(path + ": unsupported escape sequence.");
            }
        }
        return value.toString();
    }

    private static String formatScalar(Object value) {
        if (value instanceof String) {
            return formatString((String) value);
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? "True" : "False";
        }
        return BigDecimal.valueOf(((Number) value).doubleValue()).stripTrailingZeros().toPlainString();
    }

    private static String formatString(String value) {
        if (!needsQuotes(value)) {
            return value;
        }
        StringBuilder quoted = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"' || character == '\\') {
                quoted.append('\\').append(character);
            } else if (character == '\n') {
                quoted.append("\\n");
            } else if (character == '\r') {
                quoted.append("\\r");
            } else if (character == '\t') {
                quoted.append("\\t");
            } else if (character < 32) {
                String digits = Integer.toHexString(character);
                quoted.append("\\u");
                for (int padding = digits.length(); padding < 4; padding++) {
                    quoted.append('0');
                }
                quoted.append(digits);
            } else {
                quoted.append(character);
            }
        }
        return quoted.append('"').toString();
    }

    private static boolean needsQuotes(String value) {
        if (value.isEmpty() || !value.equals(value.trim())) {
            return true;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == ',' || character == ':' || character == '"' || character == '\\' || character < 32) {
                return true;
            }
        }
        return false;
    }
}
