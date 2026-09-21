package betamoon.network;

/** Result of validating a client's response before any BetaMoon state is sent. */
public final class HandshakeResult {
    private final boolean accepted;
    private final String rejectionReason;

    private HandshakeResult(boolean accepted, String rejectionReason) {
        this.accepted = accepted;
        this.rejectionReason = rejectionReason;
    }

    public static HandshakeResult accepted() {
        return new HandshakeResult(true, null);
    }

    public static HandshakeResult rejected(String reason) {
        return new HandshakeResult(false, reason);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String rejectionReason() {
        return rejectionReason;
    }
}
