package betamoon.network.protocol;

public final class EntityPresentationMessage implements ProtocolMessage {
    public final int entityId;
    public final long sequence;
    public final PresentationSnapshot presentation;

    public EntityPresentationMessage(int entityId, long sequence, PresentationSnapshot presentation) {
        if (presentation == null) {
            throw new IllegalArgumentException("Entity presentation is required");
        }
        this.entityId = entityId;
        this.sequence = sequence;
        this.presentation = presentation;
    }

    @Override
    public MessageType type() {
        return MessageType.ENTITY_PRESENTATION;
    }
}
