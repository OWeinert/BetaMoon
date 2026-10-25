package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.value.NativeConversions;
import java.lang.reflect.Array;
import java.util.Arrays;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

final class NativeLuaAccess extends NativeLuaHandle {
    NativeLuaAccess(NativeAccessApi host, NativeContext context, boolean direct) {
        super(direct ? "Native direct access" : "Native managed access");
        properties.put("direct", NativeLuaSupport.function(this, args -> {
            context.check();
            return new NativeLuaAccess(host, context, true);
        }));
        properties.put("bind", NativeLuaSupport.function(this, args -> {
            context.check();
            try {
                LuaValue request = args.arg1();
                if (request.istable() && !(request.get("ref").isnil())
                        && request.get("ref") instanceof NativeLuaReference) {
                    request = request.get("ref");
                }
                if (request instanceof NativeLuaReference && ((NativeLuaReference) request).memberRef != null) {
                    NativeAccessApi.options(args.arg(2));
                    NativeMemberRef member = ((NativeLuaReference) request).memberRef;
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
        properties.put("call", NativeLuaSupport.function(this, args -> {
            context.check();
            if (args.narg() < 3) {
                throw new NativeAccessFailure("receiver_required", "supply type, method name and receiver/nil");
            }
            NativeTypeRef type = host.type(args.arg1(), LuaValue.NIL);
            String name = NativeAccessApi.text(args.arg(2));
            Object receiver = host.receiver(args.arg(3), context);
            Object[] input = host.arguments(args.subargs(4), context);
            NativeMemberPlan plan = context.bindings.method(type, name, receiver, input, context);
            Object result = plan.call(context, direct, receiver, context.bindings.arguments(plan, input, context));
            return plan.valueType == Void.TYPE ? LuaValue.NONE : host.result(result, context, direct);
        }));
        for (String name : new String[]{"client", "world", "player", "screen", "subject"}) {
            properties.put(name, NativeLuaSupport.function(this, args -> {
                Object root = context.root(name);
                return root == null
                        ? NativeAccessApi.absent(name + "_unavailable", LuaValue.NIL)
                        : host.result(root, context, direct);
            }));
        }
        properties.put("array", NativeLuaSupport.function(this, args -> {
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
