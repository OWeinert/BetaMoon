package betamoon.instrumentation.api;

/** Canonical named reference to a class, using JVM internal-name syntax. */
public final class ClassRef {
    private final String internalName;

    public ClassRef(String internalName) {
        if (internalName == null || internalName.length() == 0 || internalName.indexOf('.') >= 0) {
            throw new IllegalArgumentException("Class names must use non-empty JVM internal-name syntax");
        }
        this.internalName = internalName;
    }

    public String getInternalName() {
        return internalName;
    }

    @Override
    public String toString() {
        return internalName;
    }
}
