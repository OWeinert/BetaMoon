package betamoon.luaapi.interop;

import betamoon.interop.attachments.NativeAttachment;
import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.interfaces.NativeImplementation;
import betamoon.interop.interfaces.NativeInterfaceContract;
import betamoon.interop.interfaces.NativeInterfaces;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeMemberReference;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.value.NativeConversions;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Declaration parsing and Lua conversion, separate from the Java proxy/resource
 * registry.
 */
final class NativeLuaInterfaces {
    private NativeLuaInterfaces() {
    }

    static NativeLuaImplementation implement(NativeAccessApi host, NativeContext context, LuaValue request,
            boolean direct, boolean observer) {
        context.require(observer ? NativeContext.Effect.WRITE : NativeContext.Effect.UNKNOWN, direct);
        LuaTable options = observer
                ? NativeAccessApi.options(request, "methods")
                : NativeAccessApi.options(request, "interfaces", "methods", "policy", "lifetime");
        List<Class<?>> interfaces = new ArrayList<>();
        if (observer) {
            interfaces.add(context.bindings.names.curatedType("net/minecraft/src/IWorldAccess"));
        } else {
            LuaTable requested = options.get("interfaces").checktable();
            int count = NativeLuaAccess.sequence(requested, 8);
            for (int i = 1; i <= count; i++) {
                interfaces.add(context.bindings.type(host.type(requested.get(i), LuaValue.NIL), context));
            }
        }
        NativeInterfaceContract contract = new NativeInterfaceContract(interfaces);
        LuaTable methods = options.get("methods").checktable();
        int count = NativeLuaAccess.sequence(methods, 256);
        Map<String, NativeInterfaces.MethodCallback> callbacks = new LinkedHashMap<>();
        NativeImplementation[] implementation = new NativeImplementation[1];
        String owner = context.owner();
        String policy = observer || options.get("policy").isnil()
                ? "query"
                : NativeAccessApi.text(options.get("policy"));
        for (int i = 1; i <= count; i++) {
            LuaTable selected = NativeAccessApi.options(methods.get(i), "method", "callback", "policy");
            NativeMemberRef reference = method(host, selected.get("method"));
            NativeMemberPlan plan = context.bindings.exact(reference, context, false);
            String key = contract.select((Method) plan.member);
            LuaValue callback = selected.get("callback").checkfunction();
            String methodPolicy = selected.get("policy").isnil()
                    ? policy
                    : NativeAccessApi.text(selected.get("policy"));
            NativeInterfaces.MethodCallback definition = new NativeInterfaces.MethodCallback((actual, arguments,
                    scope) -> dispatch(host, owner, reference, callback, implementation[0], actual, arguments, scope),
                    methodPolicy);
            if (callbacks.put(key, definition) != null) {
                throw new NativeAccessFailure("duplicate_interface_method", key);
            }
        }
        String lifetime = observer
                ? "world"
                : options.get("lifetime").isnil() ? "owner" : NativeAccessApi.text(options.get("lifetime"));
        implementation[0] = context.bindings.interfaces.create(context, contract, callbacks, policy, lifetime,
                observer);
        return new NativeLuaImplementation(host, implementation[0]);
    }

    static NativeLuaAttachment attach(NativeAccessApi host, NativeContext context, LuaValue request, boolean direct) {
        context.require(NativeContext.Effect.UNKNOWN, direct);
        LuaTable options = NativeAccessApi.options(request, "implementation", "receiver", "add", "remove", "lifetime");
        if (!(options.get("implementation") instanceof NativeLuaImplementation)) {
            throw new NativeAccessFailure("native_implementation_required", "use an implementation handle");
        }
        NativeLuaImplementation implementation = (NativeLuaImplementation) options.get("implementation");
        Object receiver = host.receiver(options.get("receiver"), context);
        NativeMemberPlan add = context.bindings.exact(method(host, options.get("add")), context, true);
        NativeMemberPlan remove = context.bindings.exact(method(host, options.get("remove")), context, true);
        NativeAttachment attachment = context.bindings.interfaces.attach(context, implementation.implementation,
                receiver, add, remove, NativeAccessApi.text(options.get("lifetime")), false);
        return new NativeLuaAttachment(host, implementation, attachment);
    }

    static NativeLuaAttachment observeWorld(NativeAccessApi host, NativeContext context, LuaValue options) {
        context.require(NativeContext.Effect.WRITE, false);
        Object world = context.root("world");
        Class<?> worldType = context.bindings.names.curatedType("net/minecraft/src/World");
        if (world == null || !worldType.isInstance(world)) {
            throw new NativeAccessFailure("world_unavailable", "observe the current local world");
        }
        NativeTypeRef type = context.bindings.reference(worldType);
        String descriptor = "(Lnet/minecraft/src/IWorldAccess;)V";
        NativeMemberPlan add = context.bindings.exact(new NativeMemberRef(NativeMemberRef.Kind.METHOD, type, type,
                "addWorldAccess", descriptor, "mapped", "mapped"), context, false);
        NativeMemberPlan remove = context.bindings.exact(new NativeMemberRef(NativeMemberRef.Kind.METHOD, type, type,
                "removeWorldAccess", descriptor, "mapped", "mapped"), context, false);
        try {
            if (world.getClass().getMethod(add.member.getName(), add.parameters).getDeclaringClass() != worldType
                    || world.getClass().getMethod(remove.member.getName(), remove.parameters)
                            .getDeclaringClass() != worldType) {
                throw new NativeAccessFailure("native_adapter_override",
                        "use an explicit direct attachment for overridden world registration");
            }
        } catch (NoSuchMethodException exception) {
            throw new NativeAccessFailure("native_adapter_unavailable", "world registration contract changed",
                    exception);
        }
        NativeLuaImplementation implementation = implement(host, context, options, true, true);
        try {
            NativeAttachment attachment = context.bindings.interfaces.attach(context, implementation.implementation,
                    world, add, remove, "world", true);
            return new NativeLuaAttachment(host, implementation, attachment);
        } catch (RuntimeException | Error failure) {
            context.bindings.owned.invalidate(implementation.value, "attachment_install_failed");
            throw failure;
        }
    }

    private static Object dispatch(NativeAccessApi host, String owner, NativeMemberRef reference, LuaValue callback,
            NativeImplementation implementation, Method method, Object[] arguments, LuaCallbackScope scope) {
        NativeCallbackTable table = new NativeCallbackTable(scope);
        table.set("method", host.reference(reference));
        Object[] result = new Object[1];
        ScriptExecution.invokeNative(owner, "native.interface." + reference.name, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs ignored) {
                NativeContext context = NativeContext.current();
                context.bindings.interfaces.requireLive(implementation, context);
                table.set("native", NativeAccessApi.contextAccess(context));
                LuaValue[] values = new LuaValue[arguments.length + 1];
                values[0] = table;
                for (int i = 0; i < arguments.length; i++) {
                    values[i + 1] = host.result(arguments[i], context, false);
                }
                Varargs returned = callback.invoke(LuaValue.varargsOf(values));
                context.check();
                context.bindings.interfaces.requireLive(implementation, context);
                if (method.getReturnType() == Void.TYPE) {
                    if (returned.narg() > 1 || !returned.arg1().isnil()) {
                        throw new NativeAccessFailure("invalid_callback_return", "void callback must return no value");
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

    private static NativeMemberRef method(NativeAccessApi host, LuaValue value) {
        NativeMemberRef method = value instanceof NativeMemberReference
                ? ((NativeMemberReference) value).nativeMemberReference()
                : host.methodRecord(value);
        if (method == null || method.kind != NativeMemberRef.Kind.METHOD) {
            throw new NativeAccessFailure("invalid_interface_method", "supply an exact method reference or metadata");
        }
        return method;
    }
}
