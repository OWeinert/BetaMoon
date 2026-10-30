package betamoon.gui.presentation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Atomic, deeply detached model publication for new UI hosts. Not a legacy
 * source conversion.
 */
public final class GuiPublishedData {
    private Map<String, Object> values = Collections.emptyMap();
    private long revision;
    private boolean publishing;

    public Map<String, Object> snapshot() {
        return values;
    }

    public long revision() {
        return revision;
    }

    public boolean publish(Map<String, ?> candidate) {
        return publish(candidate, null);
    }

    /**
     * An engine admission callback checks the detached candidate before this source
     * changes.
     */
    public boolean publish(Map<String, ?> candidate, Consumer<Map<String, Object>> admission) {
        if (publishing) {
            throw new IllegalStateException("Detached publication cannot reenter its own admission");
        }
        publishing = true;
        try {
            Map<String, Object> copied = new Copier().record(candidate, "data", 0);
            if (values.equals(copied)) {
                return false;
            }
            if (admission != null) {
                admission.accept(copied);
            }
            values = copied;
            revision++;
            return true;
        } finally {
            publishing = false;
        }
    }

    private static final class Copier {
        private static final int MAX_DEPTH = 16;
        private static final int MAX_VALUES = 16384;
        private static final int MAX_TEXT_BYTES = 1024 * 1024;

        private final Set<Object> ancestors = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        private int count;
        private int bytes;

        private Object copy(Object value, String path, int depth) {
            if (++count > MAX_VALUES || depth > MAX_DEPTH) {
                throw invalid(path, "snapshot exceeds depth/value limits");
            }
            if (value == null || value instanceof Boolean) {
                return value;
            }
            if (value instanceof String) {
                text((String) value, path);
                return value;
            }
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                    || value instanceof Float || value instanceof Double) {
                double number = ((Number) value).doubleValue();
                if (!Double.isFinite(number) || value instanceof Long && Math.abs(number) > 9007199254740991D) {
                    throw invalid(path, "numbers must be finite; publish exact large integers as text");
                }
                return value;
            }
            if (value instanceof Map) {
                return record((Map<?, ?>) value, path, depth);
            }
            if (value instanceof List) {
                enter(value, path);
                try {
                    List<Object> result = new ArrayList<>();
                    for (Object item : (List<?>) value) {
                        if (item == null) {
                            throw invalid(path + "[" + (result.size() + 1) + "]", "list entries cannot be nil");
                        }
                        result.add(copy(item, path + "[" + (result.size() + 1) + "]", depth + 1));
                    }
                    return Collections.unmodifiableList(result);
                } finally {
                    ancestors.remove(value);
                }
            }
            throw invalid(path, "expected detached scalar, list or string-key record");
        }

        private Map<String, Object> record(Map<?, ?> value, String path, int depth) {
            if (value == null) {
                throw invalid(path, "record is required");
            }
            enter(value, path);
            try {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : value.entrySet()) {
                    if (!(entry.getKey() instanceof String)) {
                        throw invalid(path, "record keys must be strings");
                    }
                    String key = (String) entry.getKey();
                    text(key, path);
                    result.put(key, copy(entry.getValue(), path + "." + key, depth + 1));
                }
                return Collections.unmodifiableMap(result);
            } finally {
                ancestors.remove(value);
            }
        }

        private void enter(Object value, String path) {
            if (!ancestors.add(value)) {
                throw invalid(path, "cyclic snapshots are unsupported");
            }
        }

        private void text(String value, String path) {
            // Reject before allocating an unbounded temporary UTF-8 array.
            if (value.length() > MAX_TEXT_BYTES - bytes) {
                throw invalid(path, "snapshot exceeds UTF-8 text budget");
            }
            bytes += value.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_TEXT_BYTES) {
                throw invalid(path, "snapshot exceeds UTF-8 text budget");
            }
        }

        private static IllegalArgumentException invalid(String path, String detail) {
            return new IllegalArgumentException(path + ": " + detail);
        }
    }
}
