package betamoon.luaapi.interop;

import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeEntry;
import betamoon.luaapi.utils.LuaCallbackScope;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** One owner-specific entry per dispatched callback, with a closed default. */
public final class NativeInvocation implements AutoCloseable {
    private final NativeContext context;
    private final LuaTable target;
    private final LuaValue previous;

    private NativeInvocation(String owner, Varargs arguments, LuaCallbackScope explicitScope, boolean expose) {
        LuaValue first = arguments.arg1();
        LuaCallbackScope scope = explicitScope != null
                ? explicitScope
                : first instanceof NativeCallbackContext ? ((NativeCallbackContext) first).nativeCallbackScope() : null;
        NativeEntry entry = scope == null ? null : scope.nativeEntry();
        target = entry != null && expose && first.istable() ? first.checktable() : null;
        previous = target == null ? LuaValue.NIL : target.rawget("native");
        context = entry == null ? NativeContext.denied(owner) : entry.open(owner);
        try {
            if (target != null) {
                target.rawset("native", NativeAccessApi.contextAccess(context));
            }
        } catch (RuntimeException | Error failure) {
            context.close();
            throw failure;
        }
    }

    public static NativeInvocation open(String owner, Varargs arguments) {
        return new NativeInvocation(owner, arguments, null, true);
    }

    public static NativeInvocation open(String owner, Varargs arguments, LuaCallbackScope scope) {
        return new NativeInvocation(owner, arguments, scope, false);
    }

    @Override
    public void close() {
        try {
            if (target != null) {
                target.rawset("native", previous);
            }
        } finally {
            context.close();
        }
    }
}
