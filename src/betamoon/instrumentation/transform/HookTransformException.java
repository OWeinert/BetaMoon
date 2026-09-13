package betamoon.instrumentation.transform;

/** Describes a target mismatch or unsafe bytecode transformation. */
public final class HookTransformException extends Exception {
    private final String hookId;

    public HookTransformException(String hookId, String message) {
        super(message);
        this.hookId = hookId;
    }

    public HookTransformException(String hookId, String message, Throwable cause) {
        super(message, cause);
        this.hookId = hookId;
    }

    public String getHookId() {
        return hookId;
    }
}
