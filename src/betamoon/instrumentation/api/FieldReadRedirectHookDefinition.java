package betamoon.instrumentation.api;

/**
 * Replaces exactly one mapped instance-field read with a static
 * receiver-to-value policy.
 */
public final class FieldReadRedirectHookDefinition implements HookDefinition {
    private final String id;
    private final MethodRef target;
    private final FieldRef field;
    private final HandlerRef handler;

    public FieldReadRedirectHookDefinition(String id, MethodRef target, FieldRef field, HandlerRef handler) {
        if (id == null || id.isEmpty() || target == null || field == null || handler == null) {
            throw new IllegalArgumentException("Field read redirect requires an ID, target, field and handler");
        }
        this.id = id;
        this.target = target;
        this.field = field;
        this.handler = handler;
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
        return MatchRequirement.exactly(1);
    }

    public int getPriority() {
        return 0;
    }
}
