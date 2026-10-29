package betamoon.luaapi.interop;

import betamoon.interop.attachments.NativeAttachment;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.runtime.NativeContext;
import org.luaj.vm2.LuaValue;

/**
 * The token retains implementation identity and detached status, without a
 * native consumer reference.
 */
final class NativeLuaAttachment extends NativeLuaHandle {
    NativeLuaAttachment(NativeAccessApi host, NativeLuaImplementation implementation, NativeAttachment attachment) {
        super("Native attachment");
        NativeAttachment.Status status = attachment.status;
        properties.put("implementation", implementation);
        properties.put("describe", NativeLuaSupport.function(this, args -> {
            return host.metadata(
                    CatalogValues.record("state", status.state(), "implementation", implementation.describe()), true);
        }));
        properties.put("close", NativeLuaSupport.function(this, args -> {
            NativeContext context = NativeContext.current();
            context.bindings.interfaces.requestClose(implementation.implementation, context);
            return LuaValue.NONE;
        }));
    }
}
