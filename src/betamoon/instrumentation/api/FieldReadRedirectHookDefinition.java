package betamoon.instrumentation.api;

/**
 * Replaces one mapped instance-field read with a receiver-to-value policy, or
 * filters an exact supported set of read values without replacing the field.
 */
public final class FieldReadRedirectHookDefinition implements HookDefinition {
    private final String id;
    private final MethodRef target;
    private final FieldRef field;
    private final HandlerRef handler;
    private final int filteredReads;

    public FieldReadRedirectHookDefinition(String id, MethodRef target, FieldRef field, HandlerRef handler) {
        this(id, target, field, handler, 0);
    }

    private FieldReadRedirectHookDefinition(String id, MethodRef target, FieldRef field, HandlerRef handler,
            int filteredReads) {
        if (id == null || id.isEmpty() || target == null || field == null || handler == null) {
            throw new IllegalArgumentException("Field read redirect requires an ID, target, field and handler");
        }
        this.id = id;
        this.target = target;
        this.field = field;
        this.handler = handler;
        this.filteredReads = filteredReads;
    }

    /**
     * Preserve native reads and filter their stack values with a value-to-value
     * handler. An unsupported exact read shape is left intact and reports NO_MATCH.
     */
    public FieldReadRedirectHookDefinition filterReads(int expectedReads) {
        if (expectedReads < 1) {
            throw new IllegalArgumentException("Field value filters require a positive exact read count");
        }
        return new FieldReadRedirectHookDefinition(id, target, field, handler, expectedReads);
    }

    public int getFilteredReads() {
        return filteredReads;
    }

    public String getId() {
        return id;
    }

    public MethodRef getTarget() {
        return target;
    }

    public FieldRef getField() {
        return field;
    }

    public HandlerRef getHandler() {
        return handler;
    }

    public MatchRequirement getMatchRequirement() {
        return filteredReads == 0 ? MatchRequirement.exactly(1) : MatchRequirement.between(0, filteredReads);
    }

    public int getPriority() {
        return 0;
    }
}
