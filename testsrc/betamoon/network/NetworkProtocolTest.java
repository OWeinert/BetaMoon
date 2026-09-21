package betamoon.network;

import betamoon.network.protocol.ClientHelloMessage;
import betamoon.network.protocol.EntityPresentationMessage;
import betamoon.network.protocol.EntityResyncRequestMessage;
import betamoon.network.protocol.EntitySoundMessage;
import betamoon.network.protocol.EntitySpawnMessage;
import betamoon.network.protocol.EntityStateMessage;
import betamoon.network.protocol.EntityStateSnapshotMessage;
import betamoon.network.protocol.EntityTransform;
import betamoon.network.protocol.NetworkEntityKind;
import betamoon.network.protocol.PresentationSnapshot;
import betamoon.network.protocol.ProtocolCodec;
import betamoon.network.protocol.ProtocolException;
import betamoon.network.protocol.ProtocolMessage;
import betamoon.network.protocol.ProtocolVersion;
import betamoon.network.protocol.ServerHelloMessage;
import betamoon.network.protocol.WireValue;
import betamoon.network.transport.PacketBetaMoon;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executable contract test for bounded encoding and deterministic content identity. */
public final class NetworkProtocolTest {
    private final ProtocolCodec codec = new ProtocolCodec();

    public static void main(String[] args) throws Exception {
        NetworkProtocolTest test = new NetworkProtocolTest();
        test.roundTripsEveryMessage();
        test.roundTripsPacketCarrier();
        test.rejectsMalformedFramesAndValues();
        test.validatesHandshakeIdentity();
        test.enforcesHandshakePhases();
        test.digestsGameplaySourcesDeterministically();
        System.out.println("Network protocol checks passed.");
    }

    private void roundTripsEveryMessage() throws Exception {
        ServerHelloMessage server = new ServerHelloMessage(ProtocolVersion.CURRENT, "0.7.0", repeat('a', 64), 42);
        ServerHelloMessage decodedServer = (ServerHelloMessage) roundTrip(server);
        require(decodedServer.sessionNonce == 42 && decodedServer.gameplayDigest.equals(server.gameplayDigest),
                "server hello round trip");

        ClientHelloMessage client = new ClientHelloMessage(ProtocolVersion.CURRENT, "0.7.0", repeat('a', 64), 42);
        ClientHelloMessage decodedClient = (ClientHelloMessage) roundTrip(client);
        require(decodedClient.protocolVersion == ProtocolVersion.CURRENT, "client hello round trip");

        Map<String, WireValue> nested = new LinkedHashMap<String, WireValue>();
        nested.put("owner", WireValue.text("player:Alex"));
        nested.put("path", WireValue.list(Arrays.asList(WireValue.integer(1), WireValue.decimal(2.5))));
        Map<String, WireValue> state = new LinkedHashMap<String, WireValue>();
        state.put("active", WireValue.bool(true));
        state.put("details", WireValue.record(nested));
        PresentationSnapshot presentation = presentation();
        EntitySpawnMessage spawn = new EntitySpawnMessage(12, "example:entity/orb", "world:entity/abc",
                NetworkEntityKind.PROP, -1, 7, 4, new EntityTransform(1, 2, 3, 90, 15, 0.1, 0.2, 0.3),
                presentation, state);
        EntitySpawnMessage decodedSpawn = (EntitySpawnMessage) roundTrip(spawn);
        require(decodedSpawn.entityId == 12 && decodedSpawn.kind == NetworkEntityKind.PROP,
                "entity spawn identity round trip");
        require(decodedSpawn.state.equals(state), "entity spawn state round trip");
        require("idle".equals(decodedSpawn.presentation.animation), "entity presentation round trip");

        Map<String, WireValue> changed = new LinkedHashMap<String, WireValue>();
        changed.put("active", WireValue.bool(false));
        EntityStateMessage delta = (EntityStateMessage) roundTrip(new EntityStateMessage(12, 7, 8, changed));
        require(delta.baseRevision == 7 && delta.revision == 8 && delta.changedFields.equals(changed),
                "entity delta round trip");
        EntityStateSnapshotMessage fullState = (EntityStateSnapshotMessage) roundTrip(
                new EntityStateSnapshotMessage(12, 8, state));
        require(fullState.revision == 8 && fullState.state.equals(state), "entity resync snapshot round trip");

        EntityPresentationMessage visual = (EntityPresentationMessage) roundTrip(
                new EntityPresentationMessage(12, 3, presentation));
        require(visual.sequence == 3 && visual.presentation.scaleX == 1.25,
                "presentation update round trip");

        EntityResyncRequestMessage resync = (EntityResyncRequestMessage) roundTrip(
                new EntityResyncRequestMessage(12, 8));
        require(resync.entityId == 12 && resync.knownRevision == 8, "resync request round trip");

        EntitySoundMessage sound = (EntitySoundMessage) roundTrip(new EntitySoundMessage(12, 4,
                "example:sound/chime", 1, 2, 3, 0.8f, 1.1f, 24));
        require(sound.sequence == 4 && sound.eventKey.equals("example:sound/chime"), "sound round trip");
    }

    private void rejectsMalformedFramesAndValues() throws Exception {
        byte[] frame = codec.encode(new EntityResyncRequestMessage(1, 0));
        frame[0] = 0;
        expectProtocolFailure(frame, "invalid magic");

        byte[] trailing = Arrays.copyOf(codec.encode(new EntityResyncRequestMessage(1, 0)), 20);
        expectProtocolFailure(trailing, "payload length mismatch");

        boolean finiteRejected = false;
        try {
            WireValue.decimal(Double.NaN);
        } catch (IllegalArgumentException expected) {
            finiteRejected = true;
        }
        require(finiteRejected, "non-finite synchronized number rejection");

        WireValue value = WireValue.integer(1);
        for (int i = 0; i < 6; i++) {
            List<WireValue> list = new ArrayList<WireValue>();
            list.add(value);
            value = WireValue.list(list);
        }
        Map<String, WireValue> tooDeep = new LinkedHashMap<String, WireValue>();
        tooDeep.put("nested", value);
        boolean depthRejected = false;
        try {
            codec.encode(new EntityStateMessage(1, 0, 1, tooDeep));
        } catch (ProtocolException expected) {
            depthRejected = true;
        }
        require(depthRejected, "nested synchronized value rejection");
    }

    private void roundTripsPacketCarrier() throws Exception {
        byte[] encoded = codec.encode(new EntityResyncRequestMessage(4, 9));
        PacketBetaMoon outgoing = new PacketBetaMoon(encoded);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        outgoing.writePacketData(new DataOutputStream(bytes));
        PacketBetaMoon incoming = new PacketBetaMoon();
        incoming.readPacketData(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        require(Arrays.equals(encoded, incoming.payload()) && incoming.getPacketSize() == encoded.length + 4,
                "native packet carrier round trip");
    }

    private void validatesHandshakeIdentity() {
        ServerHelloMessage server = new ServerHelloMessage(1, "0.7.0", "digest", 99);
        require(NetworkHandshake.verify(server, new ClientHelloMessage(1, "0.7.0", "digest", 99)).isAccepted(),
                "matching handshake acceptance");
        require(!NetworkHandshake.verify(server, new ClientHelloMessage(1, "0.7.0", "other", 99)).isAccepted(),
                "gameplay mismatch rejection");
        require(!NetworkHandshake.verify(server, new ClientHelloMessage(2, "0.7.0", "digest", 99)).isAccepted(),
                "protocol mismatch rejection");
    }

    private void enforcesHandshakePhases() {
        ServerHandshakeSession server = new ServerHandshakeSession("0.7.0", "digest", 123);
        ClientHandshakeSession client = new ClientHandshakeSession("0.7.0", "digest");
        ClientHelloMessage response = client.receive(server.begin());
        require(client.isActive() && server.accept(response).isAccepted() && server.isActive(),
                "handshake phase activation");

        ServerHandshakeSession mismatchedServer = new ServerHandshakeSession("0.7.0", "server", 456);
        ClientHandshakeSession mismatchedClient = new ClientHandshakeSession("0.7.0", "client");
        ClientHelloMessage mismatch = mismatchedClient.receive(mismatchedServer.begin());
        require(mismatchedClient.phase() == ConnectionPhase.REJECTED,
                "client mismatch phase rejection");
        require(!mismatchedServer.accept(mismatch).isAccepted()
                && mismatchedServer.phase() == ConnectionPhase.REJECTED,
                "server mismatch phase rejection");
    }

    private void digestsGameplaySourcesDeterministically() throws Exception {
        File root = Files.createTempDirectory("betamoon-network-digest").toFile();
        try {
            File nested = new File(root, "nested");
            require(nested.mkdirs(), "digest fixture directory creation");
            Files.write(new File(root, "a.lua").toPath(), "return 1\r\n".getBytes(StandardCharsets.UTF_8));
            Files.write(new File(nested, "b.lua").toPath(), "return 2\n".getBytes(StandardCharsets.UTF_8));
            Files.write(new File(root, "texture.png").toPath(), new byte[]{1, 2, 3});
            String first = GameplayContentDigest.calculate(root);

            Files.write(new File(root, "a.lua").toPath(), "return 1\n".getBytes(StandardCharsets.UTF_8));
            require(first.equals(GameplayContentDigest.calculate(root)), "line-ending independent gameplay digest");
            Files.write(new File(root, "texture.png").toPath(), new byte[]{9});
            require(first.equals(GameplayContentDigest.calculate(root)), "asset-independent gameplay digest");
            Files.write(new File(nested, "b.lua").toPath(), "return 3\n".getBytes(StandardCharsets.UTF_8));
            require(!first.equals(GameplayContentDigest.calculate(root)), "gameplay change detection");
        } finally {
            delete(root);
        }
    }

    private ProtocolMessage roundTrip(ProtocolMessage message) throws Exception {
        return codec.decode(codec.encode(message));
    }

    private void expectProtocolFailure(byte[] bytes, String context) throws Exception {
        try {
            codec.decode(bytes);
            throw new AssertionError("Expected protocol rejection: " + context);
        } catch (ProtocolException expected) {
        }
    }

    private static PresentationSnapshot presentation() {
        return new PresentationSnapshot("idle", 1.5, 1, true, 0, 0.25, 0,
                10, 20, 30, 1.25, 1.25, 1.25);
    }

    private static String repeat(char character, int count) {
        char[] characters = new char[count];
        Arrays.fill(characters, character);
        return new String(characters);
    }

    private static void delete(File file) throws Exception {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                delete(child);
            }
        }
        Files.delete(file.toPath());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
