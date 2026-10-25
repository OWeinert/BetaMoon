package betamoon.instrumentation.api;

/**
 * Transforms an assigned field value or the right operand of its immediately
 * preceding arithmetic.
 */
public final class FieldValueTransformHookDefinition implements HookDefinition {
    private final String id;
    private final MethodRef target;
    private final FieldRef field;
    private final ClassRef accessOwner;
    private final HandlerRef handler;
    private final int arithmeticOpcode;
    private final int matches;

    public FieldValueTransformHookDefinition(String id, MethodRef target, FieldRef field, ClassRef accessOwner,
            HandlerRef handler, int arithmeticOpcode, int matches) {
        if (id == null || id.isEmpty() || target == null || field == null || accessOwner == null || handler == null
                || matches < 1) {
            throw new IllegalArgumentException("Field value transform requires exact field/method anchors and matches");
        }
        this.id = id;
        this.target = target;
        this.field = field;
        this.accessOwner = accessOwner;
        this.handler = handler;
        this.arithmeticOpcode = arithmeticOpcode;
        this.matches = matches;
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

    public ClassRef getAccessOwner() {
        return accessOwner;
    }

    public HandlerRef getHandler() {
        return handler;
    }

    public int getArithmeticOpcode() {
        return arithmeticOpcode;
    }

    public int getExpectedMatches() {
        return matches;
    }

    public MatchRequirement getMatchRequirement() {
        return MatchRequirement.exactly(matches);
    }

    public int getPriority() {
        return 0;
    }
}
