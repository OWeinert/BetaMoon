package betamoon.instrumentation.mapping;

/** Runtime owner, name, and descriptor for a mapped method. */
public final class ResolvedMethod {
    private final String owner;
    private final String name;
    private final String descriptor;

    public ResolvedMethod(String owner, String name, String descriptor) {
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }

    @Override
    public String toString() {
        return owner + "." + name + descriptor;
    }
}
