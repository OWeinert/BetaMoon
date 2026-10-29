package betamoon.hooks;

/**
 * A stable, actionable hook failure; never a substitute native return value.
 */
public final class HookFailure extends RuntimeException {
    public final String reason;

    public HookFailure(String reason, String message) {
        super(reason + ": " + message);
        this.reason = reason;
    }

    public HookFailure(String reason, String message, Throwable cause) {
        super(reason + ": " + message, cause);
        this.reason = reason;
    }
}
