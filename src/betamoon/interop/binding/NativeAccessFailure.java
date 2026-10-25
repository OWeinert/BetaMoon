package betamoon.interop.binding;

/**
 * Stable bridge reason plus selector/cause, without handing native exceptions
 * to Lua as raw objects.
 */
public final class NativeAccessFailure extends RuntimeException {
    public final String reason;

    public NativeAccessFailure(String reason, String detail) {
        super(reason + (detail == null ? "" : ": " + detail));
        this.reason = reason;
    }

    public NativeAccessFailure(String reason, String detail, Throwable cause) {
        super(reason + ": " + detail, cause);
        this.reason = reason;
    }
}
