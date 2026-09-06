package betamoon.instrumentation.diagnostics;

/** Immutable diagnostic snapshot for one hook. */
public final class HookDiagnostic {
    private final String hookId;
    private final HookStatus status;
    private final String message;

    public HookDiagnostic(String hookId, HookStatus status, String message) {
        this.hookId = hookId;
        this.status = status;
        this.message = message;
    }

    public String getHookId() {
        return hookId;
    }

    public HookStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
