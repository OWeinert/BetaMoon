package betamoon.network;

import betamoon.network.protocol.ClientHelloMessage;
import betamoon.network.protocol.ProtocolVersion;
import betamoon.network.protocol.ServerHelloMessage;

/** Per-connection server handshake state. It never accepts gameplay before ACTIVE. */
public final class ServerHandshakeSession {
    private final String engineVersion;
    private final String gameplayDigest;
    private final long nonce;
    private ConnectionPhase phase = ConnectionPhase.NEW;
    private String rejectionReason;

    public ServerHandshakeSession(String engineVersion, String gameplayDigest, long nonce) {
        if (engineVersion == null || gameplayDigest == null) {
            throw new IllegalArgumentException("Handshake identity is required");
        }
        this.engineVersion = engineVersion;
        this.gameplayDigest = gameplayDigest;
        this.nonce = nonce;
    }

    public ServerHelloMessage begin() {
        if (phase != ConnectionPhase.NEW) {
            throw new IllegalStateException("BetaMoon handshake already started");
        }
        phase = ConnectionPhase.HELLO_SENT;
        return new ServerHelloMessage(ProtocolVersion.CURRENT, engineVersion, gameplayDigest, nonce);
    }

    public HandshakeResult accept(ClientHelloMessage response) {
        if (phase != ConnectionPhase.HELLO_SENT) {
            return reject("Unexpected BetaMoon client handshake");
        }
        HandshakeResult result = NetworkHandshake.verify(
                new ServerHelloMessage(ProtocolVersion.CURRENT, engineVersion, gameplayDigest, nonce), response);
        if (result.isAccepted()) {
            phase = ConnectionPhase.ACTIVE;
            rejectionReason = null;
        } else {
            phase = ConnectionPhase.REJECTED;
            rejectionReason = result.rejectionReason();
        }
        return result;
    }

    public ConnectionPhase phase() {
        return phase;
    }

    public boolean isActive() {
        return phase == ConnectionPhase.ACTIVE;
    }

    public String rejectionReason() {
        return rejectionReason;
    }

    private HandshakeResult reject(String reason) {
        phase = ConnectionPhase.REJECTED;
        rejectionReason = reason;
        return HandshakeResult.rejected(reason);
    }
}
