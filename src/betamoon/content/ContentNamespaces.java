package betamoon.content;

/** Namespace constants and user-content reservation policy. */
public final class ContentNamespaces {
    public static final String MINECRAFT = "minecraft";
    public static final String BETAMOON = "betamoon";
    public static final String EXAMPLE = "example";

    private ContentNamespaces() {
    }

    public static boolean isReserved(String namespace) {
        return MINECRAFT.equals(namespace) || BETAMOON.equals(namespace) || EXAMPLE.equals(namespace);
    }

    public static void requireUserNamespace(ContentKey key) {
        if (isReserved(key.namespace())) {
            throw new ContentKeyException(ContentKeyException.Reason.RESERVED_NAMESPACE, key.toString(), "namespace",
                    "Content namespace '" + key.namespace() + "' is reserved");
        }
    }
}
