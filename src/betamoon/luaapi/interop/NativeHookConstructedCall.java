package betamoon.luaapi.interop;

import betamoon.hooks.HookFailure;
import betamoon.hooks.HookInvocation;
import betamoon.hooks.HookLuaValue;
import betamoon.interop.runtime.NativeContext;
import java.util.function.Supplier;
import org.luaj.vm2.LuaValue;

/**
 * Observes one declaring constructor return without exposing any edit or
 * continuation functions.
 */
public final class NativeHookConstructedCall extends HookLuaValue {
    private final HookInvocation invocation;
    private final Supplier<NativeContext> context;
    private NativeContext accessContext;
    private LuaValue access;

    public NativeHookConstructedCall(HookInvocation invocation, Supplier<NativeContext> context) {
        this.invocation = invocation;
        this.context = context;
        properties.put("id", valueOf(invocation.definition.id));
        properties.put("owner", valueOf(invocation.definition.owner));
        properties.put("site", valueOf(invocation.site));
        properties.put("mode", valueOf("constructed"));
        properties.put("arguments", new Arguments(invocation, context));
    }

    @Override
    public LuaValue get(LuaValue key) {
        invocation.check();
        if (key.type() == TSTRING) {
            if (key.tojstring().equals("native")) {
                NativeContext current = context.get();
                if (accessContext != current) {
                    accessContext = current;
                    access = NativeAccessApi.contextAccess(current);
                }
                return access;
            }
            if (key.tojstring().equals("receiver")) {
                return NativeAccessApi.hookValue(invocation.receiver(), context.get());
            }
        }
        return super.get(key);
    }

    private static final class Arguments extends HookLuaValue {
        private final HookInvocation invocation;

        Arguments(HookInvocation invocation, Supplier<NativeContext> context) {
            this.invocation = invocation;
            properties.put("count", valueOf(invocation.argumentCount()));
            properties.put("get", method(args -> {
                invocation.check();
                if (args.narg() != 1 || invocation.argumentCount() == 0) {
                    throw new HookFailure("hook_argument_index",
                            "get(index) selects a zero-based constructor parameter");
                }
                int index = NativeAccessApi.integer(args.arg1(), 0, invocation.argumentCount() - 1);
                return NativeAccessApi.hookValue(invocation.argument(index), context.get());
            }));
        }

        @Override
        public LuaValue get(LuaValue key) {
            invocation.check();
            return super.get(key);
        }
    }
}
