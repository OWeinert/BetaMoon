package betamoon.content;

/** Describes a validation failure for a central content key. */
public final class ContentKeyException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        MISSING_NAMESPACE, MISSING_TYPE, MISSING_NAME, UNEXPECTED_TYPE, UPPERCASE, INVALID_CHARACTER, EMPTY_SEGMENT, UNSAFE_SEGMENT, RESERVED_NAMESPACE, TOO_LONG
    }

    private final Reason reason;
    private final String input;
    private final String component;
    private final ContentType expectedType;
    private final ContentType actualType;

    ContentKeyException(Reason reason, String input, String component, String message) {
        this(reason, input, component, null, null, message);
    }

    ContentKeyException(Reason reason, String input, String component, ContentType expectedType, ContentType actualType,
            String message) {
        super(message);
        this.reason = reason;
        this.input = input;
        this.component = component;
        this.expectedType = expectedType;
        this.actualType = actualType;
    }

    public Reason getReason() {
        return reason;
    }

    public String getInput() {
        return input;
    }

    /** Returns {@code key}, {@code namespace}, {@code type}, or {@code name}. */
    public String getComponent() {
        return component;
    }

    public ContentType getExpectedType() {
        return expectedType;
    }

    public ContentType getActualType() {
        return actualType;
    }
}
