package betamoon.network.protocol;

public final class ClientHelloMessage implements ProtocolMessage {
    public final int protocolVersion;
    public final String engineVersion;
    public final String gameplayDigest;
    public final long sessionNonce;

    public ClientHelloMessage(int protocolVersion, String engineVersion, String gameplayDigest, long sessionNonce) {
        this.protocolVersion = protocolVersion;
        if (engineVersion == null || engineVersion.length() == 0
                || gameplayDigest == null || gameplayDigest.length() == 0) {
            throw new IllegalArgumentException("Engine version and gameplay digest are required");
        }
        this.engineVersion = engineVersion;
        this.gameplayDigest = gameplayDigest;
        this.sessionNonce = sessionNonce;
    }

    @Override
    public MessageType type() {
        return MessageType.CLIENT_HELLO;
    }
}
