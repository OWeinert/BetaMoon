package betamoon.network.transport;

import betamoon.network.protocol.ProtocolMessage;
import net.minecraft.src.NetHandler;

/** Physical-side endpoint installed after its network bootstrap is ready. */
public interface ProtocolMessageSink {
    void receive(NetHandler handler, ProtocolMessage message);

    void malformed(NetHandler handler, String reason);
}
