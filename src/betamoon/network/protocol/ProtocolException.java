package betamoon.network.protocol;

import java.io.IOException;

/** Indicates malformed, incompatible, or out-of-bounds BetaMoon network data. */
public final class ProtocolException extends IOException {
    public ProtocolException(String message) {
        super(message);
    }
}
