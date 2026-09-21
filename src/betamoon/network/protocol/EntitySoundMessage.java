package betamoon.network.protocol;

/** One-shot server-authored positional sound for clients tracking an entity. */
public final class EntitySoundMessage implements ProtocolMessage {
    public final int entityId;
    public final long sequence;
    public final String eventKey;
    public final double x;
    public final double y;
    public final double z;
    public final float volume;
    public final float pitch;
    public final float range;

    public EntitySoundMessage(int entityId, long sequence, String eventKey,
            double x, double y, double z, float volume, float pitch, float range) {
        if (eventKey == null || eventKey.length() == 0) {
            throw new IllegalArgumentException("Sound event key is required");
        }
        finite(x);
        finite(y);
        finite(z);
        finite(volume);
        finite(pitch);
        finite(range);
        if (Math.abs(x) > ProtocolLimits.MAX_COORDINATE || Math.abs(y) > ProtocolLimits.MAX_COORDINATE
                || Math.abs(z) > ProtocolLimits.MAX_COORDINATE) {
            throw new IllegalArgumentException("Sound position exceeds the network bound");
        }
        if (volume < 0 || volume > 16 || pitch < 0 || pitch > 8 || range < 0 || range > 1024) {
            throw new IllegalArgumentException("Sound volume, pitch, or range is out of bounds");
        }
        this.entityId = entityId;
        this.sequence = sequence;
        this.eventKey = eventKey;
        this.x = x;
        this.y = y;
        this.z = z;
        this.volume = volume;
        this.pitch = pitch;
        this.range = range;
    }

    @Override
    public MessageType type() {
        return MessageType.ENTITY_SOUND;
    }

    private static void finite(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Sound values must be finite");
        }
    }
}
