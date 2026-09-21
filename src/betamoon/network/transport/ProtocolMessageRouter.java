package betamoon.network.transport;

import betamoon.network.protocol.ProtocolCodec;
import betamoon.network.protocol.ProtocolMessage;
import java.io.IOException;
import net.minecraft.src.NetHandler;

/** Decodes packet payloads before dispatching them to a side-specific session. */
public final class ProtocolMessageRouter {
    private static final ProtocolMessageSink UNAVAILABLE = new ProtocolMessageSink() {
        @Override
        public void receive(NetHandler handler, ProtocolMessage message) {
            malformed(handler, "BetaMoon networking is not initialized");
        }

        @Override
        public void malformed(NetHandler handler, String reason) {
            handler.handleErrorMessage(reason, new Object[0]);
        }
    };

    private static final ProtocolCodec CODEC = new ProtocolCodec();
    private static volatile ProtocolMessageSink sink = UNAVAILABLE;

    private ProtocolMessageRouter() {
    }

    public static void install(ProtocolMessageSink next) {
        sink = next == null ? UNAVAILABLE : next;
    }

    static void receive(NetHandler handler, byte[] payload) {
        try {
            sink.receive(handler, CODEC.decode(payload));
        } catch (IOException | RuntimeException error) {
            sink.malformed(handler, "Malformed BetaMoon message: " + error.getMessage());
        }
    }
}
