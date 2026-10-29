package betamoon.interop.diagnostics;

import betamoon.interop.catalog.CatalogValues;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Copies bounded exception evidence without invoking foreign exception methods.
 */
public final class NativeDiagnosticEvidence {
    public static final int MAXIMUM_CAUSES = 8;
    public static final int MAXIMUM_MESSAGE_BYTES = 2048;
    private static final Reader READER = new Reader();

    private NativeDiagnosticEvidence() {
    }

    public static Map<String, Object> capture(Throwable error) {
        return READER.capture(error);
    }

    public static String message(Throwable error) {
        if (error == null) {
            return "";
        }
        String value = READER.message(error);
        return value == null ? error.getClass().getName() : bounded(value, MAXIMUM_MESSAGE_BYTES);
    }

    /**
     * Keeps complete Unicode code points; malformed UTF-16 is replaced
     * deterministically.
     */
    public static String bounded(String value, int maximumBytes) {
        if (value == null) {
            return null;
        }
        if (maximumBytes < 0) {
            throw new IllegalArgumentException("Negative diagnostic text limit");
        }
        if (value.length() <= maximumBytes) {
            boolean ascii = true;
            for (int index = 0; index < value.length(); index++) {
                if (value.charAt(index) >= 128) {
                    ascii = false;
                    break;
                }
            }
            if (ascii) {
                return value;
            }
        }
        StringBuilder result = new StringBuilder(Math.min(value.length(), maximumBytes));
        int bytes = 0;
        for (int index = 0; index < value.length();) {
            char first = value.charAt(index);
            int point = first;
            int width = 1;
            if (Character.isHighSurrogate(first) && index + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(index + 1))) {
                point = Character.toCodePoint(first, value.charAt(index + 1));
                width = 2;
            } else if (Character.isSurrogate(first)) {
                point = 0xFFFD;
            }
            int encoded = point < 0x80 ? 1 : point < 0x800 ? 2 : point < 0x10000 ? 3 : 4;
            if (bytes + encoded > maximumBytes) {
                break;
            }
            result.appendCodePoint(point);
            bytes += encoded;
            index += width;
        }
        return result.toString();
    }

    static final class Reader {
        private final Field message;
        private final Field cause;

        Reader() {
            Field storedMessage = null;
            Field storedCause = null;
            try {
                storedMessage = Throwable.class.getDeclaredField("detailMessage");
                storedCause = Throwable.class.getDeclaredField("cause");
                storedMessage.setAccessible(true);
                storedCause.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                storedMessage = null;
                storedCause = null;
            }
            message = storedMessage;
            cause = storedCause;
        }

        String message(Throwable error) {
            if (message == null) {
                return null;
            }
            try {
                return (String) message.get(error);
            } catch (IllegalAccessException | RuntimeException unavailable) {
                return null;
            }
        }

        Map<String, Object> capture(Throwable error) {
            List<Map<String, Object>> chain = new ArrayList<>();
            Map<Throwable, Boolean> seen = new IdentityHashMap<>();
            boolean cycle = false;
            boolean truncated = false;
            boolean unavailable = message == null || cause == null;
            Throwable current = error;
            while (current != null) {
                if (seen.containsKey(current)) {
                    cycle = true;
                    break;
                }
                if (chain.size() == MAXIMUM_CAUSES) {
                    truncated = true;
                    break;
                }
                seen.put(current, Boolean.TRUE);
                Map<String, Object> row = CatalogValues.record("type", current.getClass().getName());
                if (!unavailable) {
                    try {
                        String raw = (String) message.get(current);
                        String copy = bounded(raw, MAXIMUM_MESSAGE_BYTES);
                        row.put("message", copy);
                        row.put("messageTruncated", raw != null && copy.length() < raw.length());
                        Throwable next = (Throwable) cause.get(current);
                        // Throwable uses itself as the sentinel for an unset cause.
                        current = next == current ? null : next;
                    } catch (IllegalAccessException | RuntimeException denied) {
                        unavailable = true;
                        current = null;
                    }
                } else {
                    current = null;
                }
                chain.add(row);
            }
            return CatalogValues.object(CatalogValues.freeze(CatalogValues.record("source", "stored_throwable_fields",
                    "chain", chain, "cycle", cycle, "truncated", truncated, "unavailable", unavailable)));
        }
    }
}
