package betamoon.network.transport;

import betamoon.network.protocol.ProtocolLimits;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import net.minecraft.src.NetHandler;
import net.minecraft.src.Packet;

/** Vanilla packet carrier for one bounded BetaMoon protocol frame. */
public final class PacketBetaMoon extends Packet {
    private byte[] payload = new byte[0];

    public PacketBetaMoon() {
    }

    public PacketBetaMoon(byte[] payload) {
        if (payload == null || payload.length > ProtocolLimits.MAX_FRAME_BYTES + 11) {
            throw new IllegalArgumentException("Invalid BetaMoon packet payload");
        }
        this.payload = Arrays.copyOf(payload, payload.length);
    }

    @Override
    public void readPacketData(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > ProtocolLimits.MAX_FRAME_BYTES + 11) {
            throw new IOException("Invalid BetaMoon packet length: " + length);
        }
        payload = new byte[length];
        input.readFully(payload);
    }

    @Override
    public void writePacketData(DataOutputStream output) throws IOException {
        output.writeInt(payload.length);
        output.write(payload);
    }

    @Override
    public void processPacket(NetHandler handler) {
        ProtocolMessageRouter.receive(handler, payload);
    }

    @Override
    public int getPacketSize() {
        return 4 + payload.length;
    }

    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
