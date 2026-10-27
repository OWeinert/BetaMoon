package betamoon.luaapi.interop;

import betamoon.hooks.HookFailure;
import betamoon.hooks.HookInvocation;
import betamoon.hooks.HookLuaValue;
import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.diagnostics.NativeDiagnosticEvidence;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.value.NativeConversions;
import org.luaj.vm2.LuaValue;
import org.objectweb.asm.Type;

/**
 * Scoped call facade using the existing exact native conversions and borrowed
 * object views.
 */
public final class NativeHookCall extends HookLuaValue {
    private final HookInvocation invocation;
    private final NativeContext context;
    private final Type returnType;

    public NativeHookCall(HookInvocation invocation, NativeContext context, boolean editsAllowed) {
        this.invocation = invocation;
        this.context = context;
        String descriptor = invocation.site.substring(invocation.site.indexOf('('));
        Type[] arguments = Type.getArgumentTypes(descriptor);
        Type result = Type.getReturnType(descriptor);
        returnType = result;
        // Accessors check the invocation as well as the native scope, including
        // retained method closures.
        properties.put("arguments", new Arguments(invocation, context, arguments, editsAllowed));
        properties.put("native", NativeAccessApi.contextAccess(context));
        properties.put("id", valueOf(invocation.definition.id));
        properties.put("owner", valueOf(invocation.definition.owner));
        properties.put("site", valueOf(invocation.site));
        properties.put("mode", valueOf(invocation.definition.at));
        properties.put("cancel", method(args -> {
            edit(invocation, editsAllowed);
            if (args.narg() != 0 || !invocation.definition.at.equals("before") || result.getSort() != Type.VOID) {
                throw new HookFailure("hook_edit_denied", "cancel() requires a void before hook");
            }
            invocation.result(null);
            return NONE;
        }));
        properties.put("returnValue", method(args -> {
            edit(invocation, editsAllowed);
            if (args.narg() != 1 || !invocation.definition.at.equals("before") || result.getSort() == Type.VOID) {
                throw new HookFailure("hook_edit_denied", "returnValue(value) requires a non-void before hook");
            }
            invocation.result(convert(args.arg1(), result, invocation, context));
            return NONE;
        }));
        properties.put("setResult", method(args -> {
            edit(invocation, editsAllowed);
            if (args.narg() != 1 || !invocation.definition.at.equals("after") || result.getSort() == Type.VOID) {
                throw new HookFailure("hook_edit_denied", "setResult(value) requires a non-void after hook");
            }
            invocation.result(convert(args.arg1(), result, invocation, context));
            return NONE;
        }));
        if (invocation.receiver() != null) {
            properties.put("receiver", NativeAccessApi.hookValue(invocation.receiver(), context));
        }
        if (invocation.definition.at.equals("after") && result.getSort() != Type.VOID) {
            properties.put("result", NativeAccessApi.hookValue(invocation.result(), context));
        }
        if (invocation.exception() != null) {
            Throwable error = invocation.exception();
            NativeAccessApi host = new NativeAccessApi(context.bindings::snapshot);
            properties.put("exception",
                    host.metadata(CatalogValues.record("type", error.getClass().getName(), "message",
                            NativeDiagnosticEvidence.message(error), "nativeCauses",
                            NativeDiagnosticEvidence.capture(error)), true));
        }
    }

    @Override
    public LuaValue get(LuaValue key) {
        invocation.check();
        if (key.type() == TSTRING && key.tojstring().equals("result") && invocation.definition.at.equals("after")
                && returnType.getSort() != Type.VOID) {
            return NativeAccessApi.hookValue(invocation.result(), context);
        }
        return super.get(key);
    }

    private static void edit(HookInvocation invocation, boolean allowed) {
        invocation.check();
        if (!allowed || !invocation.definition.edit) {
            throw new HookFailure("hook_edit_denied", "declare effects=edit in an eligible enclosing phase");
        }
    }

    private static Object convert(LuaValue value, Type type, HookInvocation invocation, NativeContext context) {
        Class<?> target = NativeConversions.primitive(type.getClassName());
        if (target == null) {
            try {
                target = Class.forName(
                        type.getSort() == Type.ARRAY ? type.getDescriptor().replace('/', '.') : type.getClassName(),
                        false, invocation.declaringClass().getClassLoader());
            } catch (ClassNotFoundException error) {
                throw new HookFailure("missing_dependency", "hook parameter/return type " + type, error);
            }
        }
        try {
            return NativeAccessApi.hookConvert(value, target, context);
        } catch (NativeAccessFailure failure) {
            throw NativeLuaSupport.error(failure);
        }
    }

    private static final class Arguments extends HookLuaValue {
        private final HookInvocation invocation;

        Arguments(HookInvocation invocation, NativeContext context, Type[] types, boolean editsAllowed) {
            this.invocation = invocation;
            properties.put("count", valueOf(types.length));
            properties.put("get", method(args -> {
                invocation.check();
                if (args.narg() != 1) {
                    throw new HookFailure("hook_argument_index", "get(index) requires one index");
                }
                int index = NativeAccessApi.integer(args.arg1(), 0, Math.max(0, types.length - 1));
                return NativeAccessApi.hookValue(invocation.argument(index), context);
            }));
            properties.put("set", method(args -> {
                edit(invocation, editsAllowed);
                if (args.narg() != 2) {
                    throw new HookFailure("hook_argument_index", "set(index, value) requires two arguments");
                }
                int index = NativeAccessApi.integer(args.arg1(), 0, Math.max(0, types.length - 1));
                invocation.argument(index);
                invocation.argument(index, convert(args.arg(2), types[index], invocation, context));
                return NONE;
            }));
        }

        @Override
        public LuaValue get(LuaValue key) {
            invocation.check();
            return super.get(key);
        }
    }
}
