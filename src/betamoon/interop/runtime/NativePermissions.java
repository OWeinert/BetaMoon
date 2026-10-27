package betamoon.interop.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Immutable entry capabilities. Private selection never adds an execution
 * capability.
 */
public final class NativePermissions {
    public enum Capability {
        READ, BINDING_PREPARATION, CLIENT_RESOURCE, CLIENT_ACTION, GAMEPLAY, UNKNOWN_EFFECT, INITIALIZATION
    }

    public static final NativePermissions NONE = of();
    public static final NativePermissions READ_ONLY = of(Capability.READ);
    public static final NativePermissions GAMEPLAY = of(Capability.READ, Capability.BINDING_PREPARATION,
            Capability.CLIENT_RESOURCE, Capability.CLIENT_ACTION, Capability.GAMEPLAY, Capability.UNKNOWN_EFFECT,
            Capability.INITIALIZATION);
    public static final NativePermissions CLIENT_PREPARATION = of(Capability.READ, Capability.BINDING_PREPARATION,
            Capability.CLIENT_RESOURCE);
    public static final NativePermissions CLIENT_ACTION = of(Capability.READ, Capability.CLIENT_ACTION);
    private final int bits;

    private NativePermissions(int bits) {
        this.bits = bits;
    }

    private static NativePermissions of(Capability... capabilities) {
        int result = 0;
        for (Capability capability : capabilities) {
            result |= 1 << capability.ordinal();
        }
        return new NativePermissions(result);
    }

    public static NativePermissions legacy(boolean mutable) {
        return mutable ? GAMEPLAY : READ_ONLY;
    }

    public boolean allows(Capability capability) {
        return (bits & 1 << capability.ordinal()) != 0;
    }

    public NativePermissions intersect(NativePermissions other) {
        return new NativePermissions(bits & other.bits);
    }

    public List<String> describe() {
        List<String> result = new ArrayList<>();
        for (Capability capability : Capability.values()) {
            if (allows(capability)) {
                result.add(capability.name().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }
}
