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
import java.util.List;
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
    private final List<Type> arguments;
    private final Function<Object[], Object> continuation;
    private NativeContext accessContext;
    private LuaValue access;

    public NativeHookCall(HookInvocation invocation, NativeContext context, boolean editsAllowed) {
        this(invocation, () -> context, editsAllowed, null);
    }

    public NativeHookCall(HookInvocation invocation, Supplier<NativeContext> context, boolean editsAllowed,
            Function<Object[], Object> continuation) {
        this.invocation = invocation;
        this.context = context;
        this.editsAllowed = editsAllowed;
        arguments = invocation.parameterTypes();
        returnType = invocation.returnType();
        this.continuation = continuation;
    }

    @Override
    protected LuaValue createProperty(String name) {
        switch (name) {
            case "exception":
                return exceptionEvidence();
            case "arguments":
                return new Arguments(invocation, context, arguments, editsAllowed);
            case "id":
                return valueOf(invocation.definition.id);
            case "owner":
                return valueOf(invocation.definition.owner);
            case "site":
                return valueOf(invocation.site);
            case "mode":
                return valueOf(invocation.definition.at);
            case "cancel":
                return method(args -> {
                    edit(invocation, editsAllowed);
                    if (args.narg() != 0 || !invocation.definition.at.equals("before")
                            || returnType.getSort() != Type.VOID) {
                        throw new HookFailure("hook_edit_denied", "cancel() requires a void before hook");
                    }
                    invocation.result(null);
                    return NONE;
                });
            case "returnValue":
                return method(args -> {
                    edit(invocation, editsAllowed);
                    if (args.narg() != 1 || !invocation.definition.at.equals("before")
                            || returnType.getSort() == Type.VOID) {
                        throw new HookFailure("hook_edit_denied", "returnValue(value) requires a non-void before hook");
                    }
                    invocation.result(convert(args.arg1(), returnType, invocation, context.get()));
                    return NONE;
                });
            case "setResult":
                return method(args -> {
                    edit(invocation, editsAllowed);
                    if (args.narg() != 1 || !invocation.definition.at.equals("after")
                            || returnType.getSort() == Type.VOID) {
                        throw new HookFailure("hook_edit_denied", "setResult(value) requires a non-void after hook");
                    }
                    invocation.result(convert(args.arg1(), returnType, invocation, context.get()));
                    return NONE;
                });
            case "proceed":
                return invocation.definition.at.equals("around")
                        ? method(args -> proceed(args, false, arguments, continuation))
                        : NIL;
            case "proceedWith":
                return invocation.definition.at.equals("around")
                        ? method(args -> proceed(args, true, arguments, continuation))
                        : NIL;
            default:
                return NIL;
        }
    }

    private LuaValue exceptionEvidence() {
        Throwable error = invocation.exception();
        if (error == null) {
            return NIL;
        }
        NativeAccessApi host = new NativeAccessApi(context.get().bindings::snapshot);
        return host.metadata(CatalogValues.record("type", error.getClass().getName(), "message",
                NativeDiagnosticEvidence.message(error), "nativeCauses", NativeDiagnosticEvidence.capture(error)),
                true);
    }

    @Override
    public LuaValue get(LuaValue key) {
        invocation.check();
        if (key.type() == TSTRING && key.tojstring().equals("native")) {
            NativeContext current = context.get();
            if (accessContext != current) {
                accessContext = current;
                access = NativeAccessApi.contextAccess(current);
            }
            return access;
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

    private Varargs proceed(Varargs args, boolean replace, List<Type> types, Function<Object[], Object> continuation) {
        edit(invocation, editsAllowed);
        if (continuation == null || args.narg() != (replace ? types.size() : 0)) {
            throw new HookFailure("hook_invalid_value", "use proceed() or proceedWith(the exact Java argument list)");
        }
        Object[] actual = invocation.argumentValues();
        if (replace) {
            for (int index = 0; index < types.size(); index++) {
                actual[index] = convert(args.arg(index + 1), types.get(index), invocation, context.get());
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

    static Object convert(LuaValue value, Type type, HookInvocation invocation, NativeContext context) {
        Class<?> target = invocation.valueClass(type);
        try {
            return NativeAccessApi.hookConvert(value, target, context);
        } catch (NativeAccessFailure failure) {
            throw NativeLuaSupport.error(failure);
        }
    }

    private static final class Arguments extends HookLuaValue {
        private final HookInvocation invocation;

        Arguments(HookInvocation invocation, Supplier<NativeContext> context, List<Type> types, boolean editsAllowed) {
            this.invocation = invocation;
            properties.put("count", valueOf(types.size()));
            properties.put("get", method(args -> {
                invocation.check();
                if (args.narg() != 1) {
                    throw new HookFailure("hook_argument_index", "get(index) requires one index");
                }
                int index = NativeAccessApi.integer(args.arg1(), 0, Math.max(0, types.size() - 1));
                return NativeAccessApi.hookValue(invocation.argument(index), context.get());
            }));
            properties.put("set", method(args -> {
                edit(invocation, editsAllowed);
                if (args.narg() != 2) {
                    throw new HookFailure("hook_argument_index", "set(index, value) requires two arguments");
                }
                int index = NativeAccessApi.integer(args.arg1(), 0, Math.max(0, types.size() - 1));
                invocation.argument(index);
                invocation.argument(index, convert(args.arg(2), types.get(index), invocation, context.get()));
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
