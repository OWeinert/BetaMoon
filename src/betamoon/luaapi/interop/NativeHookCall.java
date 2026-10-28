package betamoon.luaapi.interop;

import betamoon.hooks.HookFailure;
import betamoon.hooks.HookInvocation;
import betamoon.hooks.HookLuaValue;
import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.diagnostics.NativeDiagnosticEvidence;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.value.NativeConversions;
import java.util.function.Function;
import java.util.function.Supplier;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.Varargs;
import org.objectweb.asm.Type;

/**
 * Scoped call facade using the existing exact native conversions and borrowed
 * object views.
 */
public final class NativeHookCall extends HookLuaValue {
    private final HookInvocation invocation;
    private final Supplier<NativeContext> context;
    private final boolean editsAllowed;
    private final Type returnType;

    public NativeHookCall(HookInvocation invocation, NativeContext context, boolean editsAllowed) {
        this(invocation, () -> context, editsAllowed, null);
    }

    public NativeHookCall(HookInvocation invocation, Supplier<NativeContext> context, boolean editsAllowed,
            Function<Object[], Object> continuation) {
        this.invocation = invocation;
        this.context = context;
        this.editsAllowed = editsAllowed;
        String descriptor = invocation.site.substring(invocation.site.indexOf('('));
        Type[] arguments = Type.getArgumentTypes(descriptor);
        Type result = Type.getReturnType(descriptor);
        returnType = result;
        // Accessors check the invocation as well as the native scope, including
        // retained method closures.
        properties.put("arguments", new Arguments(invocation, context, arguments, editsAllowed));
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
            invocation.result(convert(args.arg1(), result, invocation, context.get()));
            return NONE;
        }));
        properties.put("setResult", method(args -> {
            edit(invocation, editsAllowed);
            if (args.narg() != 1 || !invocation.definition.at.equals("after") || result.getSort() == Type.VOID) {
                throw new HookFailure("hook_edit_denied", "setResult(value) requires a non-void after hook");
            }
            invocation.result(convert(args.arg1(), result, invocation, context.get()));
            return NONE;
        }));
        if (invocation.definition.at.equals("around")) {
            properties.put("proceed", method(args -> proceed(args, false, arguments, continuation)));
            properties.put("proceedWith", method(args -> proceed(args, true, arguments, continuation)));
        }
        if (invocation.receiver() != null) {
            properties.put("receiver", NativeAccessApi.hookValue(invocation.receiver(), context.get()));
        }
        if (invocation.definition.at.equals("after") && result.getSort() != Type.VOID) {
            properties.put("result", NativeAccessApi.hookValue(invocation.result(), context.get()));
        }
        if (invocation.exception() != null) {
            Throwable error = invocation.exception();
            NativeAccessApi host = new NativeAccessApi(context.get().bindings::snapshot);
            properties.put("exception",
                    host.metadata(CatalogValues.record("type", error.getClass().getName(), "message",
                            NativeDiagnosticEvidence.message(error), "nativeCauses",
                            NativeDiagnosticEvidence.capture(error)), true));
        }
    }

    @Override
    public LuaValue get(LuaValue key) {
        invocation.check();
        if (key.type() == TSTRING && key.tojstring().equals("native")) {
            return NativeAccessApi.contextAccess(context.get());
        }
        if (key.type() == TSTRING && key.tojstring().equals("receiver")) {
            return NativeAccessApi.hookValue(invocation.receiver(), context.get());
        }
        if (key.type() == TSTRING && key.tojstring().equals("failure")) {
            Throwable failure = invocation.nativeFailure();
            return failure == null ? NIL : new FailureEvidence(invocation, failure);
        }
        if (key.type() == TSTRING && key.tojstring().equals("result") && invocation.definition.at.equals("after")
                && returnType.getSort() != Type.VOID) {
            return NativeAccessApi.hookValue(invocation.result(), context.get());
        }
        return super.get(key);
    }

    private Varargs proceed(Varargs args, boolean replace, Type[] types, Function<Object[], Object> continuation) {
        edit(invocation, editsAllowed);
        if (continuation == null || args.narg() != (replace ? types.length : 0)) {
            throw new HookFailure("hook_invalid_value", "use proceed() or proceedWith(the exact Java argument list)");
        }
        Object[] actual = invocation.argumentValues();
        if (replace) {
            for (int index = 0; index < types.length; index++) {
                actual[index] = convert(args.arg(index + 1), types[index], invocation, context.get());
            }
        }
        Object value;
        try {
            value = continuation.apply(actual);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            // The dispatcher retains original Throwable identity; Lua receives copied
            // evidence only.
            throw new ContinuationError("hook continuation failed: " + failure.getClass().getName() + ": "
                    + NativeDiagnosticEvidence.message(failure));
        }
        return returnType.getSort() == Type.VOID ? NONE : NativeAccessApi.hookValue(value, context.get());
    }

    public void complete(Varargs returned) {
        edit(invocation, editsAllowed);
        boolean noValue = returnType.getSort() == Type.VOID;
        if (returned.narg() != (noValue ? 0 : 1)) {
            throw new HookFailure("hook_invalid_value",
                    noValue
                            ? "void around callbacks return no value"
                            : "around callbacks return exactly one typed replacement/result");
        }
        invocation.result(noValue ? null : convert(returned.arg1(), returnType, invocation, context.get()));
    }

    private static final class ContinuationError extends LuaError {
        ContinuationError(String message) {
            super(message);
        }
    }

    private static final class FailureEvidence extends HookLuaValue {
        private final HookInvocation invocation;

        FailureEvidence(HookInvocation invocation, Throwable failure) {
            this.invocation = invocation;
            properties.put("type", valueOf(failure.getClass().getName()));
            properties.put("message", valueOf(NativeDiagnosticEvidence.message(failure)));
            NativeAccessApi metadata = new NativeAccessApi(NativeIndexSnapshot::empty);
            properties.put("nativeCauses", metadata.metadata(NativeDiagnosticEvidence.capture(failure), true));
        }

        @Override
        public LuaValue get(LuaValue key) {
            invocation.check();
            return super.get(key);
        }
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

        Arguments(HookInvocation invocation, Supplier<NativeContext> context, Type[] types, boolean editsAllowed) {
            this.invocation = invocation;
            properties.put("count", valueOf(types.length));
            properties.put("get", method(args -> {
                invocation.check();
                if (args.narg() != 1) {
                    throw new HookFailure("hook_argument_index", "get(index) requires one index");
                }
                int index = NativeAccessApi.integer(args.arg1(), 0, Math.max(0, types.length - 1));
                return NativeAccessApi.hookValue(invocation.argument(index), context.get());
            }));
            properties.put("set", method(args -> {
                edit(invocation, editsAllowed);
                if (args.narg() != 2) {
                    throw new HookFailure("hook_argument_index", "set(index, value) requires two arguments");
                }
                int index = NativeAccessApi.integer(args.arg1(), 0, Math.max(0, types.length - 1));
                invocation.argument(index);
                invocation.argument(index, convert(args.arg(2), types[index], invocation, context.get()));
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
