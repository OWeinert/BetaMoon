package betamoon.network;

import betamoon.network.protocol.ClientHelloMessage;
import betamoon.network.protocol.ServerHelloMessage;

/** Initial strict compatibility policy. Later protocol negotiation can replace this policy. */
public final class NetworkHandshake {
    private NetworkHandshake() {
    }

    public static HandshakeResult verify(ServerHelloMessage server, ClientHelloMessage client) {
        if (server == null || client == null) {
            return HandshakeResult.rejected("Missing BetaMoon handshake");
        }
        if (server.sessionNonce != client.sessionNonce) {
            return HandshakeResult.rejected("BetaMoon handshake session mismatch");
        }
        if (server.protocolVersion != client.protocolVersion) {
            return HandshakeResult.rejected("BetaMoon network protocol mismatch (server "
                    + server.protocolVersion + ", client " + client.protocolVersion + ")");
        }
        if (!server.engineVersion.equals(client.engineVersion)) {
            return HandshakeResult.rejected("BetaMoon version mismatch (server " + server.engineVersion
                    + ", client " + client.engineVersion + ")");
        }
        if (!server.gameplayDigest.equals(client.gameplayDigest)) {
            return HandshakeResult.rejected("BetaMoon Lua gameplay content differs from the server");
        }
        return HandshakeResult.accepted();
    }
}
