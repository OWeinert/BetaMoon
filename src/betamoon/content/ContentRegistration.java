package betamoon.content;

/** Immutable description of one active central content binding. */
public final class ContentRegistration {
    private final ContentKey key;
    private final ContentType type;
    private final Object content;
    private final String owner;
    private final long revision;

    ContentRegistration(ContentKey key, ContentType type, Object content, String owner, long revision) {
        this.key = key;
        this.type = type;
        this.content = content;
        this.owner = owner;
        this.revision = revision;
    }

    public ContentKey getKey() {
        return key;
    }

    public ContentType getType() {
        return type;
    }

    public Object getContent() {
        return content;
    }

    public String getOwner() {
        return owner;
    }

    public long getRevision() {
        return revision;
    }
}
