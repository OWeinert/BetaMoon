package betamoon.content;

/**
 * Stable engine-side identity used to protect persisted or numeric content
 * across hot reload. Values are adapter-defined, such as block numeric ID 250.
 */
public final class NativeContentIdentity {
    public static final int MAX_DOMAIN_LENGTH = 32;
    public static final int MAX_VALUE_LENGTH = 128;

    private final ContentType type;
    private final String domain;
    private final String value;

    private NativeContentIdentity(ContentType type, String domain, String value) {
        this.type = type;
        this.domain = domain;
        this.value = value;
    }

    public static NativeContentIdentity of(ContentType type, String domain, String value) {
        if (type == null) {
            throw new NullPointerException("Content type");
        }
        validateDomain(domain);
        validateValue(value);
        return new NativeContentIdentity(type, domain, value);
    }

    private static void validateDomain(String domain) {
        if (domain == null || domain.length() == 0) {
            throw new IllegalArgumentException("Native identity domain must not be empty");
        }
        if (domain.length() > MAX_DOMAIN_LENGTH) {
            throw new IllegalArgumentException("Native identity domain exceeds " + MAX_DOMAIN_LENGTH + " characters");
        }
        for (int index = 0; index < domain.length(); index++) {
            char character = domain.charAt(index);
            if (!(character >= 'a' && character <= 'z') && !(character >= '0' && character <= '9')
                    && character != '_') {
                throw new IllegalArgumentException("Invalid native identity domain: " + domain);
            }
        }
    }

    private static void validateValue(String value) {
        if (value == null || value.length() == 0) {
            throw new IllegalArgumentException("Native identity value must not be empty");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("Native identity value exceeds " + MAX_VALUE_LENGTH + " characters");
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException("Native identity value contains a control character");
            }
        }
    }

    public ContentType type() {
        return type;
    }

    public String domain() {
        return domain;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NativeContentIdentity)) {
            return false;
        }
        NativeContentIdentity identity = (NativeContentIdentity) other;
        return type.equals(identity.type) && domain.equals(identity.domain) && value.equals(identity.value);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + domain.hashCode();
        return 31 * result + value.hashCode();
    }

    @Override
    public String toString() {
        return type + "[" + domain + "=" + value + "]";
    }
}
