package betamoon.instrumentation.api;

/** Selects a value from the target method to pass to a hook callback. */
public final class ValueBinding {
    public enum Kind {
        THIS,
        ARGUMENT,
        INSTANCE_FIELD,
        RETURN_VALUE,
        CAPTURED_VALUE
    }

    private final Kind kind;
    private final int argumentIndex;
    private final FieldRef field;

    private ValueBinding(Kind kind, int argumentIndex, FieldRef field) {
        this.kind = kind;
        this.argumentIndex = argumentIndex;
        this.field = field;
    }

    public static ValueBinding thisValue() {
        return new ValueBinding(Kind.THIS, -1, null);
    }

    public static ValueBinding argument(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Argument index cannot be negative");
        }
        return new ValueBinding(Kind.ARGUMENT, index, null);
    }

    public static ValueBinding instanceField(FieldRef field) {
        if (field == null) {
            throw new IllegalArgumentException("Field reference is required");
        }
        return new ValueBinding(Kind.INSTANCE_FIELD, -1, field);
    }

    public static ValueBinding returnValue() {
        return new ValueBinding(Kind.RETURN_VALUE, -1, null);
    }

    public static ValueBinding capturedValue() {
        return new ValueBinding(Kind.CAPTURED_VALUE, -1, null);
    }

    public Kind getKind() {
        return kind;
    }

    public int getArgumentIndex() {
        return argumentIndex;
    }

    public FieldRef getField() {
        return field;
    }
}
