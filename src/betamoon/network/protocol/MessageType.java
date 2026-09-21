package betamoon.network.protocol;

public enum MessageType {
    SERVER_HELLO(1),
    CLIENT_HELLO(2),
    ENTITY_SPAWN(3),
    ENTITY_STATE(4),
    ENTITY_PRESENTATION(5),
    ENTITY_RESYNC_REQUEST(6),
    ENTITY_SOUND(7),
    ENTITY_STATE_SNAPSHOT(8);

    private final int id;

    MessageType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    static MessageType byId(int id) throws ProtocolException {
        for (MessageType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new ProtocolException("Unknown BetaMoon message type: " + id);
    }
}
