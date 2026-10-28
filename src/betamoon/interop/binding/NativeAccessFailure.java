package betamoon.interop.binding;

import betamoon.interop.catalog.CatalogValues;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stable bridge reason plus selector/cause, without handing native exceptions
 * to Lua as raw objects.
 */
public final class NativeAccessFailure extends RuntimeException {
    public final String reason;
    private final Map<String, Object> diagnostic = new LinkedHashMap<>();
    private String journalKey;

    public NativeAccessFailure(String reason, String detail) {
        super(reason + (detail == null ? "" : ": " + detail));
        this.reason = reason;
    }

    public NativeAccessFailure(String reason, String detail, Throwable cause) {
        super(reason + ": " + detail, cause);
        this.reason = reason;
    }

    public NativeAccessFailure explain(Map<String, Object> details) {
        journalKey = null;
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            if (entry.getKey().startsWith("requested") || entry.getKey().equals("policy")) {
                diagnostic.put(entry.getKey(), entry.getValue());
            } else {
                diagnostic.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return this;
    }

    /**
     * Bounded grouping identity; no foreign value or Throwable getters are invoked.
     */
    String journalKey() {
        if (journalKey == null) {
            StringBuilder key = new StringBuilder(256);
            keyPart(key, reason);
            keyPart(key, getMessage());
            Throwable cause = getCause();
            keyPart(key, cause == null ? "" : cause.getClass().getName());
            int remaining = 32;
            for (Map.Entry<String, Object> entry : diagnostic.entrySet()) {
                if (remaining-- == 0) {
                    break;
                }
                Object value = entry.getValue();
                if (value == null || value instanceof String || value instanceof Boolean || value instanceof Integer
                        || value instanceof Long || value instanceof Short || value instanceof Byte) {
                    keyPart(key, entry.getKey());
                    keyPart(key, value == null ? "" : value.toString());
                }
            }
            journalKey = key.toString();
        }
        return journalKey;
    }

    private static void keyPart(StringBuilder key, String value) {
        int length = value == null ? 0 : Math.min(value.length(), 256);
        key.append(length).append(':');
        if (length != 0) {
            key.append(value, 0, length);
        }
        key.append(';');
    }

    public Map<String, Object> diagnostic() {
        return CatalogValues.object(CatalogValues.freeze(diagnostic));
    }
}
