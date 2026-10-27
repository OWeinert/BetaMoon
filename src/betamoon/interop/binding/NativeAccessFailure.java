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

    public NativeAccessFailure(String reason, String detail) {
        super(reason + (detail == null ? "" : ": " + detail));
        this.reason = reason;
    }

    public NativeAccessFailure(String reason, String detail, Throwable cause) {
        super(reason + ": " + detail, cause);
        this.reason = reason;
    }

    public NativeAccessFailure explain(Map<String, Object> details) {
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            if (entry.getKey().startsWith("requested") || entry.getKey().equals("policy")) {
                diagnostic.put(entry.getKey(), entry.getValue());
            } else {
                diagnostic.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return this;
    }

    public Map<String, Object> diagnostic() {
        return CatalogValues.object(CatalogValues.freeze(diagnostic));
    }
}
