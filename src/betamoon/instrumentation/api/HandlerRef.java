package betamoon.instrumentation.api;

/** Reference to a static callback method invoked by injected bytecode. */
public final class HandlerRef {
    private final MethodRef method;

    public HandlerRef(MethodRef method) {
        if (method == null) {
            throw new IllegalArgumentException("Handler method is required");
        }
        this.method = method;
    }

    public static HandlerRef of(String owner, String name, String descriptor) {
        return new HandlerRef(new MethodRef(new ClassRef(owner), name, descriptor));
    }

    public MethodRef getMethod() {
        return method;
    }

    @Override
    public String toString() {
        return method.toString();
    }
}
