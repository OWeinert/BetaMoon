package betamoon.instrumentation.api;

/**
 * Replaces one virtual call inside a method with a static handler accepting its
 * receiver and arguments.
 */
public final class CallRedirectHookDefinition implements HookDefinition {
    private final String id;
    private final MethodRef target;
    private final MethodRef invocation;
    private final ClassRef invocationOwner;
    private final HandlerRef handler;
    private final boolean allMethods;

    /**
     * Redirect every matching call in the target class, including multiple calls in
     * one method.
     */
    public CallRedirectHookDefinition inAllMethods() {
        return allMethods
                ? this
                : new CallRedirectHookDefinition(id, target, invocation, invocationOwner, handler, true);
    }

    /**
     * Matches a virtual call whose bytecode owner is a subtype of the method's
     * declaring class. Java emits this shape for inherited methods invoked through
     * a more specific receiver type.
     */
    public CallRedirectHookDefinition withInvocationOwner(ClassRef owner) {
        if (owner == null) {
            throw new IllegalArgumentException("Invocation owner is required");
        }
        return invocationOwner != null && owner.getInternalName().equals(invocationOwner.getInternalName())
                ? this
                : new CallRedirectHookDefinition(id, target, invocation, owner, handler, allMethods);
    }

    public boolean appliesToAllMethods() {
        return allMethods;
    }

    public CallRedirectHookDefinition(String id, MethodRef target, MethodRef invocation, HandlerRef handler) {
        this(id, target, invocation, null, handler, false);
    }

    private CallRedirectHookDefinition(String id, MethodRef target, MethodRef invocation, ClassRef invocationOwner,
            HandlerRef handler, boolean allMethods) {
        if (id == null || target == null || invocation == null || handler == null) {
            throw new IllegalArgumentException("Call redirect requires an ID, target, invocation, and handler");
        }
        this.id = id;
        this.target = target;
        this.invocation = invocation;
        this.invocationOwner = invocationOwner;
        this.handler = handler;
        this.allMethods = allMethods;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public MethodRef getTarget() {
        return target;
    }

    public MethodRef getInvocation() {
        return invocation;
    }

    public ClassRef getInvocationOwner() {
        return invocationOwner;
    }

    public HandlerRef getHandler() {
        return handler;
    }

    @Override
    public MatchRequirement getMatchRequirement() {
        return MatchRequirement.exactly(1);
    }

    @Override
    public int getPriority() {
        return 0;
    }
}
