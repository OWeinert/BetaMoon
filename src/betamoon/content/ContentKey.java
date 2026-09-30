package betamoon.content;

/** Immutable canonical identity in the form {@code namespace:type/name}. */
public final class ContentKey {
    public static final int MAX_LENGTH = 255;
    public static final int MAX_NAMESPACE_LENGTH = 64;
    public static final int MAX_TYPE_LENGTH = 32;
    public static final int MAX_NAME_LENGTH = 192;

    private final String namespace;
    private final ContentType type;
    private final String name;

    private ContentKey(String namespace, ContentType type, String name) {
        this.namespace = namespace;
        this.type = type;
        this.name = name;
    }

    public static ContentKey parseCanonical(String value) {
        ParsedInput input = splitNamespace(value);
        int typeSeparator = input.remainder.indexOf('/');
        if (typeSeparator < 0) {
            throw failure(ContentKeyException.Reason.MISSING_TYPE, value, "type",
                    "Canonical content key must include a type: " + display(value));
        }
        if (typeSeparator == 0) {
            throw failure(ContentKeyException.Reason.MISSING_TYPE, value, "type",
                    "Content key type is missing: " + display(value));
        }

        String typeValue = input.remainder.substring(0, typeSeparator);
        String name = input.remainder.substring(typeSeparator + 1);
        return create(value, input.namespace, typeValue, name);
    }

    public static ContentKey parseForType(String value, ContentType expectedType) {
        if (expectedType == null) {
            throw new NullPointerException("Expected content type");
        }
        ParsedInput input = splitNamespace(value);
        int typeSeparator = input.remainder.indexOf('/');
        if (typeSeparator < 0) {
            return create(value, input.namespace, expectedType.value(), input.remainder);
        }

        ContentKey key = parseCanonical(value);
        if (!expectedType.equals(key.type)) {
            throw new ContentKeyException(ContentKeyException.Reason.UNEXPECTED_TYPE, value, "type", expectedType,
                    key.type, "Expected content type '" + expectedType + "', found '" + key.type + "'");
        }
        return key;
    }

    public static ContentKey of(String namespace, ContentType type, String name) {
        if (type == null) {
            throw new NullPointerException("Content type");
        }
        String input = String.valueOf(namespace) + ":" + type + "/" + String.valueOf(name);
        return create(input, namespace, type.value(), name);
    }

    private static ContentKey create(String input, String namespace, String typeValue, String name) {
        validateNamespace(namespace, input);
        validateType(typeValue, input);
        validateName(name, input);

        ContentType type = ContentType.of(typeValue);
        ContentKey key = new ContentKey(namespace, type, name);
        if (key.toString().length() > MAX_LENGTH) {
            throw failure(ContentKeyException.Reason.TOO_LONG, input, "key",
                    "Content key exceeds " + MAX_LENGTH + " characters: " + display(input));
        }
        return key;
    }

    private static ParsedInput splitNamespace(String value) {
        if (value == null) {
            throw failure(ContentKeyException.Reason.MISSING_NAMESPACE, null, "namespace", "Content key is required");
        }
        int separator = value.indexOf(':');
        if (separator < 0) {
            throw failure(ContentKeyException.Reason.MISSING_NAMESPACE, value, "namespace",
                    "Content key must include a namespace: " + display(value));
        }
        if (separator == 0) {
            throw failure(ContentKeyException.Reason.MISSING_NAMESPACE, value, "namespace",
                    "Content key namespace is missing: " + display(value));
        }
        if (separator != value.lastIndexOf(':')) {
            throw failure(ContentKeyException.Reason.INVALID_CHARACTER, value, "key",
                    "Content key contains an additional colon: " + display(value));
        }
        return new ParsedInput(value.substring(0, separator), value.substring(separator + 1));
    }

    private static void validateNamespace(String namespace, String input) {
        if (namespace == null || namespace.length() == 0) {
            throw failure(ContentKeyException.Reason.MISSING_NAMESPACE, input, "namespace",
                    "Content key namespace is missing: " + display(input));
        }
        if (namespace.length() > MAX_NAMESPACE_LENGTH) {
            throw tooLong(input, "namespace", MAX_NAMESPACE_LENGTH);
        }
        validateLowercase(namespace, input, "namespace");
        String[] segments = namespace.split("\\.", -1);
        for (String segment : segments) {
            if (segment.length() == 0) {
                throw emptySegment(input, "namespace");
            }
            if (".".equals(segment) || "..".equals(segment)) {
                throw unsafeSegment(input, "namespace");
            }
            for (int index = 0; index < segment.length(); index++) {
                char character = segment.charAt(index);
                if (!isLowercaseLetter(character) && !isDigit(character) && character != '_' && character != '-') {
                    throw invalidCharacter(input, "namespace", character);
                }
            }
        }
    }

    static void validateType(String type, String input) {
        if (type == null || type.length() == 0) {
            throw failure(ContentKeyException.Reason.MISSING_TYPE, input, "type",
                    "Content key type is missing: " + display(input));
        }
        if (type.length() > MAX_TYPE_LENGTH) {
            throw tooLong(input, "type", MAX_TYPE_LENGTH);
        }
        validateLowercase(type, input, "type");
        for (int index = 0; index < type.length(); index++) {
            char character = type.charAt(index);
            if (!isLowercaseLetter(character) && !isDigit(character) && character != '_') {
                throw invalidCharacter(input, "type", character);
            }
        }
    }

    private static void validateName(String name, String input) {
        if (name == null || name.length() == 0) {
            throw failure(ContentKeyException.Reason.MISSING_NAME, input, "name",
                    "Content key name is missing: " + display(input));
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw tooLong(input, "name", MAX_NAME_LENGTH);
        }
        validateLowercase(name, input, "name");
        String[] segments = name.split("/", -1);
        for (String segment : segments) {
            if (segment.length() == 0) {
                throw emptySegment(input, "name");
            }
            if (".".equals(segment) || "..".equals(segment) || segment.endsWith(".")) {
                throw unsafeSegment(input, "name");
            }
            for (int index = 0; index < segment.length(); index++) {
                char character = segment.charAt(index);
                if (!isLowercaseLetter(character) && !isDigit(character) && character != '_' && character != '.'
                        && character != '-') {
                    throw invalidCharacter(input, "name", character);
                }
            }
        }
    }

    private static void validateLowercase(String value, String input, String component) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= 'A' && character <= 'Z') {
                throw failure(ContentKeyException.Reason.UPPERCASE, input, component, "Content key " + component
                        + " contains uppercase character '" + character + "': " + display(input));
            }
        }
    }

    private static boolean isLowercaseLetter(char character) {
        return character >= 'a' && character <= 'z';
    }

    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private static ContentKeyException invalidCharacter(String input, String component, char character) {
        return failure(ContentKeyException.Reason.INVALID_CHARACTER, input, component, "Content key " + component
                + " contains invalid character " + describe(character) + ": " + display(input));
    }

    private static ContentKeyException emptySegment(String input, String component) {
        return failure(ContentKeyException.Reason.EMPTY_SEGMENT, input, component,
                "Content key " + component + " contains an empty segment: " + display(input));
    }

    private static ContentKeyException unsafeSegment(String input, String component) {
        return failure(ContentKeyException.Reason.UNSAFE_SEGMENT, input, component,
                "Content key " + component + " contains an unsafe segment: " + display(input));
    }

    private static ContentKeyException tooLong(String input, String component, int maximum) {
        return failure(ContentKeyException.Reason.TOO_LONG, input, component,
                "Content key " + component + " exceeds " + maximum + " characters: " + display(input));
    }

    private static ContentKeyException failure(ContentKeyException.Reason reason, String input, String component,
            String message) {
        return new ContentKeyException(reason, input, component, message);
    }

    private static String display(String input) {
        return input == null ? "<null>" : "'" + input + "'";
    }

    private static String describe(char character) {
        if (Character.isISOControl(character)) {
            String hex = Integer.toHexString(character).toUpperCase();
            return "U+" + "0000".substring(hex.length()) + hex;
        }
        return "'" + character + "'";
    }

    public String namespace() {
        return namespace;
    }

    public ContentType type() {
        return type;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ContentKey)) {
            return false;
        }
        ContentKey key = (ContentKey) other;
        return namespace.equals(key.namespace) && type.equals(key.type) && name.equals(key.name);
    }

    @Override
    public int hashCode() {
        int result = namespace.hashCode();
        result = 31 * result + type.hashCode();
        return 31 * result + name.hashCode();
    }

    @Override
    public String toString() {
        return namespace + ":" + type + "/" + name;
    }

    private static final class ParsedInput {
        private final String namespace;
        private final String remainder;

        private ParsedInput(String namespace, String remainder) {
            this.namespace = namespace;
            this.remainder = remainder;
        }
    }
}
