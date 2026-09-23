package betamoon.instrumentation.diagnostics;

/** Immutable diagnostic snapshot for one hook. */
public final class HookDiagnostic {
    private final String hookId;
    private final HookStatus status;
    private final String message;
    private final String target;
    private final String matchRequirement;
    private final boolean required;

    public HookDiagnostic(String hookId, HookStatus status, String message) {
        this(hookId, status, message, null, null, false);
    }

    public HookDiagnostic(String hookId, HookStatus status, String message, String target, String matchRequirement,
            boolean required) {
        this.hookId = hookId;
        this.status = status;
        this.message = message;
        this.target = target;
        this.matchRequirement = matchRequirement;
        this.required = required;
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

    public String getTarget() {
        return target;
    }

    public String getMatchRequirement() {
        return matchRequirement;
    }

    public boolean isRequired() {
        return required;
    }
}
