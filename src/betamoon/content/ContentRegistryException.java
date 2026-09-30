package betamoon.content;

/** A structured conflict reported while staging or publishing content. */
public final class ContentRegistryException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        TYPE_MISMATCH, RESERVED_NAMESPACE, DUPLICATE_KEY, DUPLICATE_CONTENT, RETAINED_KEY_CHANGED, RETAINED_IDENTITY_CHANGED
    }

    private final Reason reason;
    private final ContentKey key;
    private final ContentKey conflictingKey;
    private final String owner;
    private final String conflictingOwner;

    ContentRegistryException(Reason reason, ContentKey key, ContentKey conflictingKey, String owner,
            String conflictingOwner, String message) {
        super(message);
        this.reason = reason;
        this.key = key;
        this.conflictingKey = conflictingKey;
        this.owner = owner;
        this.conflictingOwner = conflictingOwner;
    }

    public Reason getReason() {
        return reason;
    }

    public ContentKey getKey() {
        return key;
    }

    public ContentKey getConflictingKey() {
        return conflictingKey;
    }

    public String getOwner() {
        return owner;
    }

    public String getConflictingOwner() {
        return conflictingOwner;
    }
}
