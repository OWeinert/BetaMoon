package betamoon.network.protocol;

/** Central defensive limits for untrusted network input. */
public final class ProtocolLimits {
    public static final int MAX_FRAME_BYTES = 1024 * 1024;
    public static final int MAX_KEY_BYTES = 256;
    public static final int MAX_TEXT_BYTES = 16 * 1024;
    public static final int MAX_FIELDS = 256;
    public static final int MAX_LIST_VALUES = 256;
    public static final int MAX_VALUE_DEPTH = 4;
    public static final double MAX_COORDINATE = 32_000_000;
    public static final double MAX_VELOCITY = 1_000;

    private ProtocolLimits() {
    }
}
