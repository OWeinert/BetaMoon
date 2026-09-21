package betamoon.network;

import betamoon.network.protocol.ClientHelloMessage;
import betamoon.network.protocol.ProtocolVersion;
import betamoon.network.protocol.ServerHelloMessage;

/** Client half of the strict initial compatibility handshake. */
public final class ClientHandshakeSession {
    private final String engineVersion;
    private final String gameplayDigest;
    private ConnectionPhase phase = ConnectionPhase.NEW;
    private String rejectionReason;

    public ClientHandshakeSession(String engineVersion, String gameplayDigest) {
        if (engineVersion == null || gameplayDigest == null) {
            throw new IllegalArgumentException("Handshake identity is required");
        }
        this.engineVersion = engineVersion;
        this.gameplayDigest = gameplayDigest;
    }

    public ClientHelloMessage receive(ServerHelloMessage server) {
        if (phase != ConnectionPhase.NEW) {
            throw new IllegalStateException("Unexpected repeated BetaMoon server handshake");
        }
        if (server.protocolVersion != ProtocolVersion.CURRENT) {
            reject("BetaMoon network protocol mismatch (server " + server.protocolVersion
                    + ", client " + ProtocolVersion.CURRENT + ")");
        } else if (!engineVersion.equals(server.engineVersion)) {
            reject("BetaMoon version mismatch (server " + server.engineVersion
                    + ", client " + engineVersion + ")");
        } else if (!gameplayDigest.equals(server.gameplayDigest)) {
            reject("BetaMoon Lua gameplay content differs from the server");
        } else {
            phase = ConnectionPhase.ACTIVE;
        }
        return new ClientHelloMessage(ProtocolVersion.CURRENT, engineVersion, gameplayDigest, server.sessionNonce);
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

    private void reject(String reason) {
        phase = ConnectionPhase.REJECTED;
        rejectionReason = reason;
    }
}
