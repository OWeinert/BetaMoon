package betamoon.luaapi.interop;

import betamoon.interop.interfaces.NativeImplementation;

final class NativeLuaImplementation extends NativeLuaOwned {
    final NativeImplementation implementation;

    NativeLuaImplementation(NativeAccessApi host, NativeImplementation implementation) {
        super(host, implementation.value);
        this.implementation = implementation;
        properties.put("interfaces", host.metadata(implementation.interfaces, true));
        properties.put("describe",
                NativeLuaSupport.function(this, args -> host.metadata(implementation.describe(), true)));
    }
}
