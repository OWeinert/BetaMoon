package betamoon.client.network;

import betamoon.BetaMoonCommon;
import betamoon.luamodloader.LuaModLoader;
import betamoon.network.ClientHandshakeSession;
import betamoon.network.GameplayContentDigest;
import betamoon.network.protocol.ClientHelloMessage;
import betamoon.network.protocol.EntityPresentationMessage;
import betamoon.network.protocol.EntityResyncRequestMessage;
import betamoon.network.protocol.EntitySoundMessage;
import betamoon.network.protocol.EntitySpawnMessage;
import betamoon.network.protocol.EntityStateMessage;
import betamoon.network.protocol.EntityStateSnapshotMessage;
import betamoon.network.protocol.ProtocolCodec;
import betamoon.network.protocol.ProtocolMessage;
import betamoon.network.protocol.ServerHelloMessage;
import betamoon.network.transport.PacketRegistration;
import betamoon.network.transport.ProtocolMessageRouter;
import betamoon.network.transport.ProtocolMessageSink;
import java.io.IOException;
import net.minecraft.src.NetClientHandler;
import net.minecraft.src.NetHandler;
import net.minecraft.src.WorldClient;

/** Client protocol endpoint. All received gameplay state remains server-authored. */
public final class ClientNetworkSession implements ProtocolMessageSink {
    private final ProtocolCodec codec = new ProtocolCodec();
    private final ClientEntityReplication entities = new ClientEntityReplication(this::sendCurrent);
    private NetClientHandler handler;
    private ClientHandshakeSession handshake;

    public static ClientNetworkSession initialize() {
        ClientNetworkSession session = new ClientNetworkSession();
        ProtocolMessageRouter.install(session);
        return session;
    }

    private ClientNetworkSession() {
    }

    @Override
    public void receive(NetHandler rawHandler, ProtocolMessage message) {
        if (!(rawHandler instanceof NetClientHandler)) {
            malformed(rawHandler, "BetaMoon client received a packet on a server handler");
            return;
        }
        NetClientHandler clientHandler = (NetClientHandler) rawHandler;
        if (message instanceof ServerHelloMessage) {
            receiveHello(clientHandler, (ServerHelloMessage) message);
            return;
        }
        requireActive(clientHandler);
        WorldClient world = world();
        if (message instanceof EntitySpawnMessage) {
            entities.spawn(world, (EntitySpawnMessage) message);
        } else if (message instanceof EntityStateMessage) {
            entities.state(world, (EntityStateMessage) message);
        } else if (message instanceof EntityStateSnapshotMessage) {
            entities.stateSnapshot(world, (EntityStateSnapshotMessage) message);
        } else if (message instanceof EntityPresentationMessage) {
            entities.presentation(world, (EntityPresentationMessage) message);
        } else if (message instanceof EntitySoundMessage) {
            entities.sound(world, (EntitySoundMessage) message);
        } else {
            throw new IllegalStateException("Server sent a client-forbidden BetaMoon message: " + message.type());
        }
    }

    @Override
    public void malformed(NetHandler rawHandler, String reason) {
        rawHandler.handleErrorMessage(reason, new Object[0]);
    }

    private void receiveHello(NetClientHandler clientHandler, ServerHelloMessage hello) {
        if (handler != clientHandler) {
            handler = clientHandler;
            handshake = null;
            entities.clear();
        }
        if (handshake != null) {
            throw new IllegalStateException("Repeated BetaMoon server handshake");
        }
        String digest;
        try {
            digest = GameplayContentDigest.calculate(LuaModLoader.getLuaModsDir());
        } catch (IOException error) {
            throw new IllegalStateException("Cannot identify local Lua gameplay content", error);
        }
        handshake = new ClientHandshakeSession(BetaMoonCommon.VERSION, digest);
        ClientHelloMessage response = handshake.receive(hello);
        send(clientHandler, response);
    }

    private void requireActive(NetClientHandler candidate) {
        if (handler != candidate || handshake == null || !handshake.isActive()) {
            throw new IllegalStateException("BetaMoon gameplay arrived before a compatible handshake");
        }
    }

    private WorldClient world() {
        net.minecraft.src.World world = net.minecraft.src.ModLoader.getMinecraftInstance().theWorld;
        if (!(world instanceof WorldClient)) {
            throw new IllegalStateException("BetaMoon multiplayer state arrived without a remote client world");
        }
        return (WorldClient) world;
    }

    private void sendCurrent(EntityResyncRequestMessage request) {
        if (handler == null || handshake == null || !handshake.isActive()) {
            return;
        }
        send(handler, request);
    }

    private void send(NetClientHandler destination, ProtocolMessage message) {
        try {
            destination.addToSendQueue(PacketRegistration.packet(codec.encode(message)));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot encode BetaMoon " + message.type() + " message", error);
        }
    }
}
