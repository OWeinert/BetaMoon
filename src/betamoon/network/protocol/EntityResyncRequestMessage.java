package betamoon.network.protocol;

public final class EntityResyncRequestMessage implements ProtocolMessage {
    public final int entityId;
    public final long knownRevision;

    public EntityResyncRequestMessage(int entityId, long knownRevision) {
        this.entityId = entityId;
        this.knownRevision = knownRevision;
    }

    @Override
    public MessageType type() {
        return MessageType.ENTITY_RESYNC_REQUEST;
    }
}
