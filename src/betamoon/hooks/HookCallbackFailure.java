package betamoon.hooks;

/**
 * Distinguishes synchronous editing-contract failure from an exception in
 * native code.
 */
public final class HookCallbackFailure extends RuntimeException {
    public HookCallbackFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
