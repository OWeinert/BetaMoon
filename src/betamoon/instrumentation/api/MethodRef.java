package betamoon.instrumentation.api;

/** Canonical named reference to one method. Descriptors make overload matching unambiguous. */
public final class MethodRef {
    private final ClassRef owner;
    private final String name;
    private final String descriptor;

    public MethodRef(ClassRef owner, String name, String descriptor) {
        if (owner == null) {
            throw new IllegalArgumentException("Method owner is required");
        }
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("Method name is required");
        }
        if (descriptor == null || descriptor.length() == 0 || descriptor.charAt(0) != '(') {
            throw new IllegalArgumentException("A JVM method descriptor is required");
        }
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
    }

    public ClassRef getOwner() {
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
