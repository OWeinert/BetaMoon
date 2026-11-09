package betamoon.instrumentation.api;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Replaces mapped calls inside a method with a static handler. Virtual handlers
 * accept the original receiver first; static handlers preserve the invocation
 * descriptor.
 */
public final class CallRedirectHookDefinition implements HookDefinition {
    private final String id;
    private final MethodRef target;
    private final MethodRef invocation;
    private final ClassRef invocationOwner;
    private final HandlerRef handler;
    private final boolean allMethods;
    private final boolean allCallsInTarget;
    private final boolean staticInvocation;
    private final List<CallRedirectShape> shapes;

    /**
     * Redirect every matching call in the target class, including multiple calls in
     * one method.
     */
    public CallRedirectHookDefinition inAllMethods() {
        return allMethods
                ? this
                : new CallRedirectHookDefinition(id, target, invocation, invocationOwner, handler, true,
                        allCallsInTarget, staticInvocation, shapes);
    }

    /** Redirects every matching call in the selected target method. */
    public CallRedirectHookDefinition allCallsInTarget() {
        return allCallsInTarget
                ? this
                : new CallRedirectHookDefinition(id, target, invocation, invocationOwner, handler, allMethods, true,
                        staticInvocation, shapes);
    }

    /** Selects an {@code INVOKESTATIC} source call instead of a virtual call. */
    public CallRedirectHookDefinition staticInvocation() {
        return staticInvocation
                ? this
                : new CallRedirectHookDefinition(id, target, invocation, invocationOwner, handler, allMethods,
                        allCallsInTarget, true, shapes);
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
                : new CallRedirectHookDefinition(id, target, invocation, owner, handler, allMethods, allCallsInTarget,
                        staticInvocation, shapes);
    }

    /**
     * Unsupported argument shapes preserve the original calls while unrelated hooks
     * can apply.
     */
    public CallRedirectHookDefinition withShapes(CallRedirectShape... shapes) {
        if (shapes == null || shapes.length < 1 || shapes.length > 64 || Arrays.asList(shapes).contains(null)) {
            throw new IllegalArgumentException("Semantic redirects require 1..64 call shapes");
        }
        return new CallRedirectHookDefinition(id, target, invocation, invocationOwner, handler, allMethods,
                allCallsInTarget, staticInvocation, Arrays.asList(shapes.clone()));
    }

    public List<CallRedirectShape> getShapes() {
        return shapes;
    }

    public boolean appliesToAllMethods() {
        return allMethods;
    }

    public boolean redirectsAllCallsInTarget() {
        return allCallsInTarget;
    }

    public boolean isStaticInvocation() {
        return staticInvocation;
    }

    public CallRedirectHookDefinition(String id, MethodRef target, MethodRef invocation, HandlerRef handler) {
        this(id, target, invocation, null, handler, false, false, false, Collections.emptyList());
    }

    private CallRedirectHookDefinition(String id, MethodRef target, MethodRef invocation, ClassRef invocationOwner,
            HandlerRef handler, boolean allMethods, boolean allCallsInTarget, boolean staticInvocation,
            List<CallRedirectShape> shapes) {
        if (id == null || target == null || invocation == null || handler == null) {
            throw new IllegalArgumentException("Call redirect requires an ID, target, invocation, and handler");
        }
        this.id = id;
        this.target = target;
        this.invocation = invocation;
        this.invocationOwner = invocationOwner;
        this.handler = handler;
        this.allMethods = allMethods;
        this.allCallsInTarget = allCallsInTarget;
        this.staticInvocation = staticInvocation;
        if (allMethods && !shapes.isEmpty()) {
            throw new IllegalArgumentException("Exact call shapes require a single target method");
        }
        this.shapes = Collections.unmodifiableList(new ArrayList<>(shapes));
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
        return shapes.isEmpty() ? MatchRequirement.exactly(1) : MatchRequirement.between(0, 1);
    }

    @Override
    public int getPriority() {
        return 0;
    }
}
