package betamoon.network.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict, bounded binary codec shared by local tests and both physical sides. */
public final class ProtocolCodec {
    private static final int MAGIC = 0x424d5031;

    public byte[] encode(ProtocolMessage message) throws IOException {
        if (message == null) {
            throw new IllegalArgumentException("Protocol message is required");
        }

        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        DataOutputStream payload = new DataOutputStream(payloadBytes);
        writePayload(payload, message);
        payload.flush();
        byte[] encodedPayload = payloadBytes.toByteArray();
        if (encodedPayload.length > ProtocolLimits.MAX_FRAME_BYTES) {
            throw new ProtocolException("BetaMoon message exceeds the maximum frame size");
        }

        ByteArrayOutputStream frameBytes = new ByteArrayOutputStream(encodedPayload.length + 11);
        DataOutputStream frame = new DataOutputStream(frameBytes);
        frame.writeInt(MAGIC);
        frame.writeShort(ProtocolVersion.CURRENT);
        frame.writeByte(message.type().id());
        frame.writeInt(encodedPayload.length);
        frame.write(encodedPayload);
        frame.flush();
        return frameBytes.toByteArray();
    }

    public ProtocolMessage decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 11) {
            throw new ProtocolException("Incomplete BetaMoon message frame");
        }
        if (bytes.length > ProtocolLimits.MAX_FRAME_BYTES + 11) {
            throw new ProtocolException("BetaMoon message exceeds the maximum frame size");
        }

        DataInputStream frame = new DataInputStream(new ByteArrayInputStream(bytes));
        if (frame.readInt() != MAGIC) {
            throw new ProtocolException("Invalid BetaMoon message header");
        }
        int version = frame.readUnsignedShort();
        if (version != ProtocolVersion.CURRENT) {
            throw new ProtocolException("Unsupported BetaMoon protocol version: " + version);
        }
        MessageType type = MessageType.byId(frame.readUnsignedByte());
        int length = frame.readInt();
        if (length < 0 || length > ProtocolLimits.MAX_FRAME_BYTES || length != frame.available()) {
            throw new ProtocolException("Invalid BetaMoon message payload length: " + length);
        }

        byte[] payloadBytes = new byte[length];
        frame.readFully(payloadBytes);
        DataInputStream payload = new DataInputStream(new ByteArrayInputStream(payloadBytes));
        try {
            ProtocolMessage message = readPayload(payload, type);
            if (payload.available() != 0) {
                throw new ProtocolException("Trailing data in BetaMoon " + type + " message");
            }
            return message;
        } catch (EOFException error) {
            throw new ProtocolException("Truncated BetaMoon " + type + " message");
        } catch (IllegalArgumentException error) {
            throw new ProtocolException("Invalid BetaMoon " + type + " message: " + error.getMessage());
        }
    }

    private void writePayload(DataOutputStream output, ProtocolMessage message) throws IOException {
        switch (message.type()) {
            case SERVER_HELLO:
                writeServerHello(output, (ServerHelloMessage) message);
                return;
            case CLIENT_HELLO:
                writeClientHello(output, (ClientHelloMessage) message);
                return;
            case ENTITY_SPAWN:
                writeEntitySpawn(output, (EntitySpawnMessage) message);
                return;
            case ENTITY_STATE:
                writeEntityState(output, (EntityStateMessage) message);
                return;
            case ENTITY_PRESENTATION:
                writeEntityPresentation(output, (EntityPresentationMessage) message);
                return;
            case ENTITY_RESYNC_REQUEST:
                EntityResyncRequestMessage request = (EntityResyncRequestMessage) message;
                output.writeInt(request.entityId);
                output.writeLong(request.knownRevision);
                return;
            case ENTITY_SOUND:
                writeEntitySound(output, (EntitySoundMessage) message);
                return;
            case ENTITY_STATE_SNAPSHOT:
                EntityStateSnapshotMessage snapshot = (EntityStateSnapshotMessage) message;
                output.writeInt(snapshot.entityId);
                output.writeLong(snapshot.revision);
                writeMap(output, snapshot.state, 0);
                return;
            default:
                throw new ProtocolException("Unsupported BetaMoon message type: " + message.type());
        }
    }

    private ProtocolMessage readPayload(DataInputStream input, MessageType type) throws IOException {
        switch (type) {
            case SERVER_HELLO:
                return new ServerHelloMessage(input.readInt(), readText(input, ProtocolLimits.MAX_TEXT_BYTES),
                        readText(input, ProtocolLimits.MAX_TEXT_BYTES), input.readLong());
            case CLIENT_HELLO:
                return new ClientHelloMessage(input.readInt(), readText(input, ProtocolLimits.MAX_TEXT_BYTES),
                        readText(input, ProtocolLimits.MAX_TEXT_BYTES), input.readLong());
            case ENTITY_SPAWN:
                return readEntitySpawn(input);
            case ENTITY_STATE:
                return new EntityStateMessage(input.readInt(), input.readLong(), input.readLong(), readMap(input, 0));
            case ENTITY_PRESENTATION:
                return new EntityPresentationMessage(input.readInt(), input.readLong(), readPresentation(input));
            case ENTITY_RESYNC_REQUEST:
                return new EntityResyncRequestMessage(input.readInt(), input.readLong());
            case ENTITY_SOUND:
                return readEntitySound(input);
            case ENTITY_STATE_SNAPSHOT:
                return new EntityStateSnapshotMessage(input.readInt(), input.readLong(), readMap(input, 0));
            default:
                throw new ProtocolException("Unsupported BetaMoon message type: " + type);
        }
    }

    private void writeServerHello(DataOutputStream output, ServerHelloMessage message) throws IOException {
        output.writeInt(message.protocolVersion);
        writeText(output, message.engineVersion, ProtocolLimits.MAX_TEXT_BYTES);
        writeText(output, message.gameplayDigest, ProtocolLimits.MAX_TEXT_BYTES);
        output.writeLong(message.sessionNonce);
    }

    private void writeClientHello(DataOutputStream output, ClientHelloMessage message) throws IOException {
        output.writeInt(message.protocolVersion);
        writeText(output, message.engineVersion, ProtocolLimits.MAX_TEXT_BYTES);
        writeText(output, message.gameplayDigest, ProtocolLimits.MAX_TEXT_BYTES);
        output.writeLong(message.sessionNonce);
    }

    private void writeEntitySpawn(DataOutputStream output, EntitySpawnMessage message) throws IOException {
        output.writeInt(message.entityId);
        writeText(output, message.typeKey, ProtocolLimits.MAX_KEY_BYTES);
        writeText(output, message.stableIdentity, ProtocolLimits.MAX_KEY_BYTES);
        output.writeByte(message.kind.id());
        output.writeInt(message.dimension);
        output.writeLong(message.revision);
        output.writeInt(message.health);
        writeTransform(output, message.transform);
        output.writeBoolean(message.presentation != null);
        if (message.presentation != null) {
            writePresentation(output, message.presentation);
        }
        writeMap(output, message.state, 0);
    }

    private EntitySpawnMessage readEntitySpawn(DataInputStream input) throws IOException {
        int entityId = input.readInt();
        String typeKey = readText(input, ProtocolLimits.MAX_KEY_BYTES);
        String stableIdentity = readText(input, ProtocolLimits.MAX_KEY_BYTES);
        NetworkEntityKind kind = NetworkEntityKind.byId(input.readUnsignedByte());
        int dimension = input.readInt();
        long revision = input.readLong();
        int health = input.readInt();
        EntityTransform transform = readTransform(input);
        PresentationSnapshot presentation = input.readBoolean() ? readPresentation(input) : null;
        return new EntitySpawnMessage(entityId, typeKey, stableIdentity, kind, dimension, revision, health,
                transform, presentation, readMap(input, 0));
    }

    private void writeEntityState(DataOutputStream output, EntityStateMessage message) throws IOException {
        output.writeInt(message.entityId);
        output.writeLong(message.baseRevision);
        output.writeLong(message.revision);
        writeMap(output, message.changedFields, 0);
    }

    private void writeEntityPresentation(DataOutputStream output, EntityPresentationMessage message)
            throws IOException {
        output.writeInt(message.entityId);
        output.writeLong(message.sequence);
        writePresentation(output, message.presentation);
    }

    private void writeEntitySound(DataOutputStream output, EntitySoundMessage message) throws IOException {
        output.writeInt(message.entityId);
        output.writeLong(message.sequence);
        writeText(output, message.eventKey, ProtocolLimits.MAX_KEY_BYTES);
        output.writeDouble(message.x);
        output.writeDouble(message.y);
        output.writeDouble(message.z);
        output.writeFloat(message.volume);
        output.writeFloat(message.pitch);
        output.writeFloat(message.range);
    }

    private EntitySoundMessage readEntitySound(DataInputStream input) throws IOException {
        return new EntitySoundMessage(input.readInt(), input.readLong(),
                readText(input, ProtocolLimits.MAX_KEY_BYTES), input.readDouble(), input.readDouble(),
                input.readDouble(), input.readFloat(), input.readFloat(), input.readFloat());
    }

    private void writeTransform(DataOutputStream output, EntityTransform transform) throws IOException {
        output.writeDouble(transform.x);
        output.writeDouble(transform.y);
        output.writeDouble(transform.z);
        output.writeFloat(transform.yaw);
        output.writeFloat(transform.pitch);
        output.writeDouble(transform.velocityX);
        output.writeDouble(transform.velocityY);
        output.writeDouble(transform.velocityZ);
    }

    private EntityTransform readTransform(DataInputStream input) throws IOException {
        return new EntityTransform(input.readDouble(), input.readDouble(), input.readDouble(),
                input.readFloat(), input.readFloat(), input.readDouble(), input.readDouble(), input.readDouble());
    }

    private void writePresentation(DataOutputStream output, PresentationSnapshot presentation) throws IOException {
        output.writeBoolean(presentation.animation != null);
        if (presentation.animation != null) {
            writeText(output, presentation.animation, ProtocolLimits.MAX_KEY_BYTES);
        }
        output.writeDouble(presentation.animationSeconds);
        output.writeDouble(presentation.animationSpeed);
        output.writeBoolean(presentation.visible);
        output.writeDouble(presentation.offsetX);
        output.writeDouble(presentation.offsetY);
        output.writeDouble(presentation.offsetZ);
        output.writeFloat(presentation.rotationYaw);
        output.writeFloat(presentation.rotationPitch);
        output.writeFloat(presentation.rotationRoll);
        output.writeDouble(presentation.scaleX);
        output.writeDouble(presentation.scaleY);
        output.writeDouble(presentation.scaleZ);
    }

    private PresentationSnapshot readPresentation(DataInputStream input) throws IOException {
        String animation = input.readBoolean() ? readText(input, ProtocolLimits.MAX_KEY_BYTES) : null;
        return new PresentationSnapshot(animation, input.readDouble(), input.readDouble(), input.readBoolean(),
                input.readDouble(), input.readDouble(), input.readDouble(), input.readFloat(), input.readFloat(),
                input.readFloat(), input.readDouble(), input.readDouble(), input.readDouble());
    }

    private void writeMap(DataOutputStream output, Map<String, WireValue> values, int depth) throws IOException {
        if (values.size() > ProtocolLimits.MAX_FIELDS) {
            throw new ProtocolException("Too many synchronized fields: " + values.size());
        }
        output.writeShort(values.size());
        for (Map.Entry<String, WireValue> entry : values.entrySet()) {
            writeText(output, entry.getKey(), ProtocolLimits.MAX_KEY_BYTES);
            writeValue(output, entry.getValue(), depth);
        }
    }

    private Map<String, WireValue> readMap(DataInputStream input, int depth) throws IOException {
        int size = input.readUnsignedShort();
        if (size > ProtocolLimits.MAX_FIELDS) {
            throw new ProtocolException("Too many synchronized fields: " + size);
        }
        Map<String, WireValue> values = new LinkedHashMap<String, WireValue>();
        for (int i = 0; i < size; i++) {
            String key = readText(input, ProtocolLimits.MAX_KEY_BYTES);
            if (values.containsKey(key)) {
                throw new ProtocolException("Duplicate synchronized field: " + key);
            }
            values.put(key, readValue(input, depth));
        }
        return values;
    }

    private void writeValue(DataOutputStream output, WireValue value, int depth) throws IOException {
        if (value == null) {
            throw new ProtocolException("Synchronized values cannot be null");
        }
        output.writeByte(value.type().id());
        switch (value.type()) {
            case NULL:
                return;
            case BOOLEAN:
                output.writeBoolean(((Boolean) value.value()).booleanValue());
                return;
            case INTEGER:
                output.writeInt(((Integer) value.value()).intValue());
                return;
            case LONG:
                output.writeLong(((Long) value.value()).longValue());
                return;
            case DOUBLE:
                output.writeDouble(((Double) value.value()).doubleValue());
                return;
            case STRING:
                writeText(output, (String) value.value(), ProtocolLimits.MAX_TEXT_BYTES);
                return;
            case LIST:
                checkDepth(depth);
                List<WireValue> list = value.listValue();
                if (list.size() > ProtocolLimits.MAX_LIST_VALUES) {
                    throw new ProtocolException("Synchronized list is too large: " + list.size());
                }
                output.writeShort(list.size());
                for (WireValue entry : list) {
                    writeValue(output, entry, depth + 1);
                }
                return;
            case RECORD:
                checkDepth(depth);
                writeMap(output, value.recordValue(), depth + 1);
                return;
            default:
                throw new ProtocolException("Unsupported synchronized value type: " + value.type());
        }
    }

    private WireValue readValue(DataInputStream input, int depth) throws IOException {
        WireValue.Type type = WireValue.Type.byId(input.readUnsignedByte());
        switch (type) {
            case NULL:
                return WireValue.nullValue();
            case BOOLEAN:
                return WireValue.bool(input.readBoolean());
            case INTEGER:
                return WireValue.integer(input.readInt());
            case LONG:
                return WireValue.longInteger(input.readLong());
            case DOUBLE:
                return WireValue.decimal(input.readDouble());
            case STRING:
                return WireValue.text(readText(input, ProtocolLimits.MAX_TEXT_BYTES));
            case LIST:
                checkDepth(depth);
                int size = input.readUnsignedShort();
                if (size > ProtocolLimits.MAX_LIST_VALUES) {
                    throw new ProtocolException("Synchronized list is too large: " + size);
                }
                List<WireValue> list = new ArrayList<WireValue>(size);
                for (int i = 0; i < size; i++) {
                    list.add(readValue(input, depth + 1));
                }
                return WireValue.list(list);
            case RECORD:
                checkDepth(depth);
                return WireValue.record(readMap(input, depth + 1));
            default:
                throw new ProtocolException("Unsupported synchronized value type: " + type);
        }
    }

    private void checkDepth(int depth) throws ProtocolException {
        if (depth >= ProtocolLimits.MAX_VALUE_DEPTH) {
            throw new ProtocolException("Synchronized value nesting exceeds " + ProtocolLimits.MAX_VALUE_DEPTH);
        }
    }

    private void writeText(DataOutputStream output, String value, int limit) throws IOException {
        if (value == null) {
            throw new ProtocolException("Required network text is missing");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limit) {
            throw new ProtocolException("Network text exceeds " + limit + " UTF-8 bytes");
        }
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private String readText(DataInputStream input, int limit) throws IOException {
        int length = input.readUnsignedShort();
        if (length > limit) {
            throw new ProtocolException("Network text exceeds " + limit + " UTF-8 bytes");
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
