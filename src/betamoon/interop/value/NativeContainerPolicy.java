package betamoon.interop.value;

import betamoon.interop.runtime.NativeContext;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Shared effect classification for native container views and data projections.
 */
public final class NativeContainerPolicy {
    private NativeContainerPolicy() {
    }

    public static NativeContext.Effect effect(Object value, boolean write) {
        if (value.getClass().isArray() || value.getClass() == ArrayList.class || value.getClass() == LinkedList.class) {
            return write ? NativeContext.Effect.WRITE : NativeContext.Effect.READ;
        }
        return NativeContext.Effect.UNKNOWN;
    }
}
