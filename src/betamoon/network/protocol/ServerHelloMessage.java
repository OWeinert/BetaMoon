package betamoon.network.protocol;

public final class ServerHelloMessage implements ProtocolMessage {
    public final int protocolVersion;
    public final String engineVersion;
    public final String gameplayDigest;
    public final long sessionNonce;

    public ServerHelloMessage(int protocolVersion, String engineVersion, String gameplayDigest, long sessionNonce) {
        this.protocolVersion = protocolVersion;
        this.engineVersion = required(engineVersion, "engine version");
        this.gameplayDigest = required(gameplayDigest, "gameplay digest");
        this.sessionNonce = sessionNonce;
    }

    @Override
    public MessageType type() {
        return MessageType.SERVER_HELLO;
    }

    private static String required(String value, String label) {
        if (value == null || value.length() == 0) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }
}
