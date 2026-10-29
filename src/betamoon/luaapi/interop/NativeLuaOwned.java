package betamoon.luaapi.interop;

import betamoon.interop.runtime.NativeOwnedValue;

/**
 * No native payload or invocation authority is carried by a retainable handle.
 */
class NativeLuaOwned extends NativeLuaHandle {
    final NativeOwnedValue value;

    NativeLuaOwned(NativeAccessApi host, NativeOwnedValue value) {
        super("Native owned value");
        this.value = value;
        properties.put("type", host.reference(value.type));
        properties.put("describe", NativeLuaSupport.function(this, args -> host.metadata(value.describe(), true)));
    }
}
