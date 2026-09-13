package betamoon.instrumentation.api;

/**
 * Common contract for immutable hook definitions.
 *
 * Concrete definition types describe their injection behavior while the
 * registry handles shared concerns such as identity, ordering, and targeting.
 */
public interface HookDefinition {
    String getId();

    MethodRef getTarget();

    MatchRequirement getMatchRequirement();

    int getPriority();
}
