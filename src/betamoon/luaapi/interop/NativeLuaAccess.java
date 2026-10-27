package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeMemberReference;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeOwnedValue;
import betamoon.interop.value.NativeConversions;
import java.lang.reflect.Array;
import java.util.Arrays;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

final class NativeLuaAccess extends NativeLuaHandle {
    NativeLuaAccess(NativeAccessApi host, NativeContext context, boolean direct) {
        super(direct ? "Native direct access" : "Native managed access");
        diagnosticDirect = direct;
        NativeLuaDataQueries.attach(this, host, context, direct);
        properties.put("describe",
                NativeLuaSupport.scopedFunction(this, context, args -> host.metadata(context.describe(), true)));
        properties.put("direct", NativeLuaSupport.scopedFunction(this, context, args -> {
            context.check();
            return new NativeLuaAccess(host, context, true);
        }));
        properties.put("bind", NativeLuaSupport.scopedFunction(this, context, args -> {
            context.check();
            try {
                LuaValue request = args.arg1();
                if (request.istable() && !(request.get("ref").isnil())
                        && request.get("ref") instanceof NativeMemberReference) {
                    request = request.get("ref");
                }
                if (request instanceof NativeMemberReference
                        && ((NativeMemberReference) request).nativeMemberReference() != null) {
                    NativeAccessApi.options(args.arg(2));
                    NativeMemberRef member = ((NativeMemberReference) request).nativeMemberReference();
                    if (member.kind == NativeMemberRef.Kind.FIELD) {
                        throw new NativeAccessFailure("field_requires_receiver", "select a field through a bound view");
                    }
                    return new NativeLuaMethod(host, context, direct, member);
                }
                if (request.istable()) {
                    return new NativeLuaMethod(host, context, direct, host.methodRecord(request));
                }
                NativeTypeRef type = host.type(request, args.arg(2));
                return new NativeLuaView(host, context, direct, type, context.bindings.type(type, context));
            } catch (NativeAccessFailure exception) {
                if (Arrays.asList("class_not_found", "member_not_found", "side_absent", "missing_dependency")
                        .contains(exception.reason)) {
                    return NativeAccessApi.absent(exception.reason, LuaValue.valueOf(exception.getMessage()));
                }
                throw exception;
            }
        }));
        properties.put("call", NativeLuaSupport.scopedFunction(this, context, args -> {
            context.check();
            if (args.narg() < 3) {
                throw new NativeAccessFailure("receiver_required", "supply type, method name and receiver/nil");
            }
            NativeTypeRef type = host.type(args.arg1(), LuaValue.NIL);
            String name = NativeAccessApi.text(args.arg(2));
            NativeContext execution = host.operationContext(context, args.subargs(3));
            Object receiver = host.receiver(args.arg(3), execution);
            Object[] input = host.arguments(args.subargs(4), execution);
            NativeMemberPlan plan = execution.bindings.method(type, name, receiver, input, execution);
            Object result = plan.call(execution, direct, receiver,
                    execution.bindings.arguments(plan, input, execution));
            return plan.valueType == Void.TYPE ? LuaValue.NONE : host.result(result, execution, direct);
        }));
        for (String name : new String[]{"client", "world", "player", "screen", "subject"}) {
            properties.put(name, NativeLuaSupport.scopedFunction(this, context, args -> {
                Object root = context.root(name);
                return root == null
                        ? NativeAccessApi.absent(name + "_unavailable", LuaValue.NIL)
                        : host.result(root, context, direct);
            }));
        }
        properties.put("array", NativeLuaSupport.scopedFunction(this, context, args -> {
            context.prepare();
            NativeTypeRef component = host.type(args.arg1(), LuaValue.NIL);
            Class<?> type = context.bindings.type(component, context);
            if (type == Void.TYPE || component.dimensions() >= 255) {
                throw new NativeAccessFailure("invalid_array_type", component.name());
            }
            LuaValue input = args.arg(2);
            int length = input.istable()
                    ? sequence(input.checktable(), 16384)
                    : NativeAccessApi.integer(input, 0, 16384);
            Object result = Array.newInstance(type, length);
            Object[] converted = new Object[length];
            if (input.istable()) {
                for (int i = 0; i < length; i++) {
                    Object value = host.argument(input.get(i + 1), context);
                    context.bindings.validateNull(value, type, context);
                    converted[i] = NativeConversions.convert(value, type);
                }
                for (int i = 0; i < length; i++) {
                    Array.set(result, i, converted[i]);
                }
            }
            return host.result(result, context, direct);
        }));
        properties.put("retain", NativeLuaSupport.scopedFunction(this, context, args -> {
            context.require(NativeContext.Effect.UNKNOWN, direct);
            Object value = object(host, context, args.arg1());
            LuaTable options = NativeAccessApi.options(args.arg(2), "lifetime", "dispose");
            String lifetime = NativeAccessApi.text(options.get("lifetime"));
            NativeMemberPlan disposer = null;
            if (!options.get("dispose").isnil()) {
                LuaValue selected = options.get("dispose");
                NativeMemberRef member = selected instanceof NativeMemberReference
                        ? ((NativeMemberReference) selected).nativeMemberReference()
                        : host.methodRecord(selected);
                if (member == null || member.kind != NativeMemberRef.Kind.METHOD) {
                    throw new NativeAccessFailure("invalid_native_disposer", "use an exact instance method ()V");
                }
                disposer = context.bindings.exact(member, context, true);
            }
            return new NativeLuaOwned(host, context.bindings.owned.retain(context, value, lifetime, disposer));
        }));
        properties.put("use", NativeLuaSupport.scopedFunction(this, context, args -> {
            NativeOwnedValue value = owned(args.arg1());
            Object object = value.use(context);
            return host.result(object, context.ownedScope(value), direct);
        }));
        properties.put("release", NativeLuaSupport.scopedFunction(this, context, args -> {
            owned(args.arg1()).release(context, direct);
            return LuaValue.NONE;
        }));
        properties.put("copy", NativeLuaSupport.scopedFunction(this, context, args -> {
            context.require(NativeContext.Effect.WRITE, direct);
            Object value = object(host, context, args.arg1());
            LuaTable options = NativeAccessApi.options(args.arg(2), "lifetime");
            String lifetime = options.get("lifetime").isnil() ? "owner" : NativeAccessApi.text(options.get("lifetime"));
            Class<?> type = value.getClass();
            Object copy;
            if (type.isArray() && type.getComponentType().isPrimitive()) {
                int length = Array.getLength(value);
                if (length > 16384) {
                    throw new NativeAccessFailure("native_copy_limit", "primitive array copy exceeds 16384 elements");
                }
                copy = Array.newInstance(type.getComponentType(), length);
                System.arraycopy(value, 0, copy, 0, length);
            } else if (context.bindings.names.curatedClass(type, "net/minecraft/src/ItemStack")) {
                NativeTypeRef reference = context.bindings.reference(type);
                NativeMemberRef method = new NativeMemberRef(NativeMemberRef.Kind.METHOD, reference, reference, "copy",
                        "()Lnet/minecraft/src/ItemStack;", "mapped", "mapped");
                NativeMemberPlan plan = context.bindings.exact(method, context, false);
                copy = plan.call(context, false, value, new Object[0]);
                if (copy == null || copy == value || copy.getClass() != type) {
                    throw new NativeAccessFailure("native_copy_failed",
                            "stack copy did not return an independent stack");
                }
            } else {
                throw new NativeAccessFailure("unsupported_native_copy",
                        "supported copies: primitive arrays and ItemStack");
            }
            return new NativeLuaOwned(host, context.bindings.owned.retain(context, copy, lifetime, null));
        }));
        properties.put("implement", NativeLuaSupport.scopedFunction(this, context,
                args -> NativeLuaInterfaces.implement(host, context, args.arg1(), direct, false)));
        properties.put("attach", NativeLuaSupport.scopedFunction(this, context,
                args -> NativeLuaInterfaces.attach(host, context, args.arg1(), direct)));
        properties.put("observeWorld", NativeLuaSupport.scopedFunction(this, context,
                args -> NativeLuaInterfaces.observeWorld(host, context, args.arg1())));
    }

    private static Object object(NativeAccessApi host, NativeContext context, LuaValue value) {
        if (!(value instanceof NativeLuaView) || ((NativeLuaView) value).classView) {
            throw new NativeAccessFailure("native_object_required", "use a scoped native object view");
        }
        return host.receiver(value, context);
    }

    private static NativeOwnedValue owned(LuaValue value) {
        if (!(value instanceof NativeLuaOwned)) {
            throw new NativeAccessFailure("owned_value_required", "use a handle returned by copy() or direct retain()");
        }
        return ((NativeLuaOwned) value).value;
    }

    static int sequence(LuaTable table, int maximum) {
        int length = table.length();
        if (length > maximum || table.keyCount() != length) {
            throw new NativeAccessFailure("invalid_native_sequence", "expected a dense bounded one-based sequence");
        }
        for (LuaValue key : table.keys()) {
            NativeAccessApi.integer(key, 1, length);
        }
        return length;
    }
}
