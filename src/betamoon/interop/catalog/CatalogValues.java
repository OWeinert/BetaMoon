package betamoon.interop.catalog;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validated, detached JSON values shared by catalog readers, queries and exports. */
public final class CatalogValues {
    private CatalogValues() {
    }

    public static Map<String, Object> record(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Expected a catalog object");
        }
        return (Map<String, Object>) value;
    }

    public static String text(Map<String, Object> record, String key) {
        Object value = record.get(key);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Expected catalog string: " + key);
        }
        return (String) value;
    }

    public static int integer(Map<String, Object> record, String key) {
        Object value = record.get(key);
        if (!(value instanceof Number) || ((Number) value).doubleValue() != ((Number) value).intValue()) {
            throw new IllegalArgumentException("Expected catalog integer: " + key);
        }
        return ((Number) value).intValue();
    }

    public static List<String> strings(Object value) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("Expected catalog string list");
        }
        List<String> result = new ArrayList<>();
        for (Object entry : (List<?>) value) {
            if (entry != null && !(entry instanceof String)) {
                throw new IllegalArgumentException("Expected a string list entry");
            }
            result.add((String) entry);
        }
        return result;
    }

    public static Object freeze(Object value) {
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : object(value).entrySet()) {
                result.put(entry.getKey(), freeze(entry.getValue()));
            }
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object entry : (List<?>) value) {
                result.add(freeze(entry));
            }
            return Collections.unmodifiableList(result);
        }
        return value;
    }

    public static BufferedReader utf8(InputStream input) {
        return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
    }

    public static byte[] bytes(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted() || (long) output.size() + count > limit) {
                throw new IOException("input_cancelled_or_size_limit");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    /** A readLine replacement that applies limits before allocating an unbounded line. */
    public static String line(BufferedReader reader, int maxCharacters, long[] remaining) throws IOException {
        StringBuilder result = new StringBuilder();
        int character;
        while ((character = reader.read()) != -1) {
            if (--remaining[0] < 0 || result.length() >= maxCharacters) {
                throw new IOException("Catalog text size limit exceeded");
            }
            if (character == '\n') {
                if (result.length() > 0 && result.charAt(result.length() - 1) == '\r') {
                    result.setLength(result.length() - 1);
                }
                return result.toString();
            }
            result.append((char) character);
        }
        return result.length() == 0 ? null : result.toString();
    }
}
