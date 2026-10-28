package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.hosts.NativeHostShape;
import betamoon.interop.interfaces.NativeImplementation;
import betamoon.interop.interfaces.NativeInterfaceContract;
import betamoon.interop.interfaces.NativeInterfaces;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.value.NativeConversions;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Class declarations/callback parsing reuse existing native conversion,
 * callback and attachment services.
 */
final class NativeLuaHosts {
    private NativeLuaHosts() {
    }

    static NativeLuaHost create(NativeAccessApi host, NativeContext context, LuaValue requested, LuaValue definition,
            boolean direct) {
        if (!direct) {
            throw new NativeAccessFailure("direct_required", "newHost requires explicit direct access");
        }
        if (!(requested instanceof NativeLuaHostType)) {
            throw new NativeAccessFailure("invalid_native_host_type", "use betamoon.native:hostType first");
        }
        NativeHostShape shape = context.bindings.hostShapes.resolve(((NativeLuaHostType) requested).type, context);
        LuaTable options = NativeAccessApi.options(definition, "arguments", "methods", "lifetime", "dispose", "setup");
        LuaTable argumentTable = options.get("arguments").checktable();
        int count = NativeLuaAccess.sequence(argumentTable, 254);
        Object[] arguments = new Object[count];
        for (int index = 0; index < count; index++) {
            arguments[index] = host.argument(argumentTable.get(index + 1), context);
        }
        LuaTable methods = options.get("methods").checktable();
        int methodsCount = NativeLuaAccess.sequence(methods, 128);
        NativeImplementation[] implementation = new NativeImplementation[1];
        Map<String, NativeInterfaces.MethodCallback> callbacks = new LinkedHashMap<>();
        Map<String, String> closedPolicies = new LinkedHashMap<>();
        String owner = context.owner();
        for (int index = 1; index <= methodsCount; index++) {
            LuaTable method = NativeAccessApi.options(methods.get(index), "method", "callback", "policy", "onClosed");
            NativeMemberRef reference = NativeLuaHostType.member(host, method.get("method"),
                    NativeMemberRef.Kind.METHOD);
            Method actual = (Method) context.bindings.exact(reference, context, true).member;
            String key = NativeInterfaceContract.key(actual);
            if (!actual.getDeclaringClass().isAssignableFrom(shape.base)) {
                throw new NativeAccessFailure("invalid_native_host_methods",
                        "callback method belongs to another hierarchy");
            }
            int selected = -1;
            for (int candidate = 0; candidate < shape.methods.size(); candidate++) {
                if (NativeInterfaceContract.key(shape.methods.get(candidate)).equals(key)) {
                    selected = candidate;
                    break;
                }
            }
            if (selected < 0) {
                throw new NativeAccessFailure("invalid_native_host_methods", "callback is outside the immutable shape");
            }
            int slot = selected;
            LuaValue callback = method.get("callback").checkfunction();
            String policy = method.get("policy").isnil() ? "query" : NativeAccessApi.text(method.get("policy"));
            NativeInterfaces.MethodCallback supplied = new NativeInterfaces.MethodCallback(
                    (nativeMethod, input, scope) -> dispatch(host, owner, reference, callback, implementation[0], shape,
                            slot, nativeMethod, input, scope),
                    policy);
            if (callbacks.put(key, supplied) != null) {
                throw new NativeAccessFailure("duplicate_native_host_method", key);
            }
            if (!method.get("onClosed").isnil()) {
                closedPolicies.put(key, NativeAccessApi.text(method.get("onClosed")));
            }
        }
        LuaValue setup = options.get("setup").isnil() ? LuaValue.NIL : options.get("setup").checkfunction();
        NativeMemberPlan disposer = options.get("dispose").isnil()
                ? null
                : context.bindings.exact(
                        NativeLuaHostType.member(host, options.get("dispose"), NativeMemberRef.Kind.METHOD), context,
                        true);
        implementation[0] = context.bindings.interfaces.createClass(context, shape, callbacks, closedPolicies,
                arguments, options.get("lifetime").isnil() ? "owner" : NativeAccessApi.text(options.get("lifetime")),
                disposer, (token, instance) -> {
                    if (!setup.isnil()) {
                        try (NativeContext preparation = context.child()) {
                            LuaTable view = new LuaTable();
                            view.set("native", NativeAccessApi.contextAccess(preparation));
                            view.set("receiver", host.result(instance, preparation, false));
                            ScriptExecution.invokeNative(owner, "native.host.setup", setup, view);
                            preparation.check();
                        }
                    }
                });
        return new NativeLuaHost(host, implementation[0], context.bindings.reference(shape.base), shape.fingerprint);
    }

    private static Object dispatch(NativeAccessApi host, String owner, NativeMemberRef reference, LuaValue callback,
            NativeImplementation implementation, NativeHostShape shape, int slot, Method method, Object[] arguments,
            LuaCallbackScope scope) {
        NativeCallbackTable table = new NativeCallbackTable(scope);
        table.set("method", host.reference(reference));
        Object[] result = new Object[1];
        ScriptExecution.invoke(owner, "native.host." + reference.name, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs ignored) {
                NativeContext context = NativeContext.current();
                context.bindings.interfaces.requireLive(implementation, context);
                Object receiver = implementation.value.use(context);
                table.set("receiver", host.result(receiver, context, false));
                table.set("callSuper", NativeLuaSupport.scopedFunction(table, context, input -> {
                    context.require(NativeContext.Effect.UNKNOWN, true);
                    if (input.narg() != method.getParameterCount()) {
                        throw new NativeAccessFailure("invalid_native_host_arguments",
                                "callSuper requires the exact parameter list");
                    }
                    Object[] values = host.arguments(input, context);
                    Class<?>[] types = method.getParameterTypes();
                    for (int index = 0; index < values.length; index++) {
                        context.bindings.validateNull(values[index], types[index], context);
                        values[index] = NativeConversions.convert(values[index], types[index]);
                    }
                    Object value = shape.callSuper(slot, receiver, values);
                    context.check();
                    context.bindings.interfaces.requireLive(implementation, context);
                    return method.getReturnType() == Void.TYPE ? LuaValue.NONE : host.result(value, context, false);
                }));
                LuaValue[] values = new LuaValue[arguments.length + 1];
                values[0] = table;
                for (int index = 0; index < arguments.length; index++) {
                    values[index + 1] = host.result(arguments[index], context, false);
                }
                Varargs returned = callback.invoke(LuaValue.varargsOf(values));
                context.check();
                context.bindings.interfaces.requireLive(implementation, context);
                if (method.getReturnType() == Void.TYPE) {
                    if (returned.narg() > 1 || !returned.arg1().isnil()) {
                        throw new NativeAccessFailure("invalid_callback_return", "void host callback returns no value");
                    }
                } else {
                    if (returned.narg() != 1) {
                        throw new NativeAccessFailure("invalid_callback_return",
                                "return exactly one native-compatible value");
                    }
                    Object value = host.argument(returned.arg1(), context);
                    context.bindings.validateNull(value, method.getReturnType(), context);
                    result[0] = NativeConversions.convert(value, method.getReturnType());
                }
                return LuaValue.NONE;
            }
        }, table);
        return result[0];
    }
}
