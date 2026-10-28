package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.hosts.NativeHostType;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeMemberReference;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Symbolic immutable shape only; no native instance, scope or callbacks. */
final class NativeLuaHostType extends NativeLuaHandle {
    final NativeHostType type;

    private NativeLuaHostType(NativeHostType type) {
        super("Native class host type");
        this.type = type;
    }

    static NativeLuaHostType create(NativeAccessApi host, LuaValue definition) {
        LuaTable table = NativeAccessApi.options(definition, "base", "constructor", "overrides");
        NativeMemberRef constructor = member(host, table.get("constructor"), NativeMemberRef.Kind.CONSTRUCTOR);
        LuaTable overrides = table.get("overrides").checktable();
        int count = NativeLuaAccess.sequence(overrides, 128);
        List<NativeMemberRef> methods = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            methods.add(member(host, overrides.get(index), NativeMemberRef.Kind.METHOD));
        }
        return new NativeLuaHostType(new NativeHostType(host.type(table.get("base"), NIL), constructor, methods));
    }

    static NativeMemberRef member(NativeAccessApi host, LuaValue value, NativeMemberRef.Kind kind) {
        NativeMemberRef reference = value instanceof NativeMemberReference
                ? ((NativeMemberReference) value).nativeMemberReference()
                : host.methodRecord(value);
        if (reference == null || reference.kind != kind) {
            throw new NativeAccessFailure("invalid_native_host_type",
                    "use an exact " + kind + " reference or metadata");
        }
        return reference;
    }
}
