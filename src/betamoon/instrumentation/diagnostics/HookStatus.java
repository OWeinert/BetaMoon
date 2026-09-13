package betamoon.instrumentation.diagnostics;

/** Lifecycle state of one registered hook. */
public enum HookStatus {
    REGISTERED, WAITING_FOR_TARGET, APPLIED, ALREADY_APPLIED, NO_MATCH, FAILED
}
