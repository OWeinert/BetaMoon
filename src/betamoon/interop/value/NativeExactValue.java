package betamoon.interop.value;

import betamoon.interop.reference.NativeTypeRef;

/**
 * Detached scalar or typed null. Payloads are restricted to immutable Java
 * scalar values.
 */
public final class NativeExactValue {
    public static final NativeExactValue NULL = new NativeExactValue(null, null);
    public final NativeTypeRef type;
    public final Object value;

    public NativeExactValue(NativeTypeRef type, Object value) {
        boolean numeric = value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Float || value instanceof Double;
        if (value != null && !numeric && !(value instanceof String) && !(value instanceof Character)
                && !(value instanceof Boolean)) {
            throw new IllegalArgumentException("exact_value_requires_scalar");
        }
        this.type = type;
        this.value = value;
    }
}
