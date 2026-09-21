package betamoon.network.protocol;

public enum NetworkEntityKind {
    PROP(0),
    PROJECTILE(1),
    LIVING(2),
    PICKUP(3);

    private final int id;

    NetworkEntityKind(int id) {
        this.id = id;
    }

    int id() {
        return id;
    }

    static NetworkEntityKind byId(int id) throws ProtocolException {
        for (NetworkEntityKind kind : values()) {
            if (kind.id == id) {
                return kind;
            }
        }
        throw new ProtocolException("Unknown entity kind: " + id);
    }
}
