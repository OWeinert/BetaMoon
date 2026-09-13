package betamoon.instrumentation.mapping;

/** Runtime owner, name, and descriptor for a mapped field. */
public final class ResolvedField {
    private final String owner;
    private final String name;
    private final String descriptor;

    public ResolvedField(String owner, String name, String descriptor) {
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
        return owner + "." + name + ":" + descriptor;
    }
}
