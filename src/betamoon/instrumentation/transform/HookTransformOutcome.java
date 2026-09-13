package betamoon.instrumentation.transform;

/** Result of applying one hook definition to its target class. */
enum HookTransformOutcome {
    APPLIED, ALREADY_APPLIED, NO_MATCH
}
