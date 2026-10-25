package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.runtime.NativeContext;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Selected method views never retain an instance receiver. */
final class NativeLuaMethod extends NativeLuaHandle {
    private final NativeAccessApi host;
    private final NativeContext context;
    private final boolean direct;
    private final NativeMemberPlan plan;

    NativeLuaMethod(NativeAccessApi host, NativeContext context, boolean direct, NativeMemberRef reference) {
        super("Native selected " + reference.kind + "(" + reference.name + ")");
        this.host = host;
        this.context = context;
        this.direct = direct;
        if (reference.kind == NativeMemberRef.Kind.FIELD) {
            throw new NativeAccessFailure("field_requires_receiver",
                    "select a field through a bound object/class view");
        }
        this.plan = context.bindings.exact(reference, context, direct);
        if (reference.kind == NativeMemberRef.Kind.CONSTRUCTOR) {
            properties.put("new", NativeLuaSupport.function(this, this::construct));
        } else {
            properties.put("call", NativeLuaSupport.function(this, this::callSelected));
        }
    }

    Varargs callSelected(Varargs arguments) {
        context.check();
        if (arguments.narg() < 1) {
            throw new NativeAccessFailure("receiver_required", "supply an instance receiver or an explicit nil");
        }
        Object receiver = host.receiver(arguments.arg1(), context);
        Object[] input = host.arguments(arguments.subargs(2), context);
        Object[] converted = context.bindings.arguments(plan, input, context);
        Object result = plan.call(context, direct, receiver, converted);
        return plan.valueType == Void.TYPE ? LuaValue.NONE : host.result(result, context, direct);
    }

    Varargs construct(Varargs arguments) {
        context.check();
        Object[] converted = context.bindings.arguments(plan, host.arguments(arguments, context), context);
        return host.result(plan.call(context, direct, null, converted), context, direct);
    }

    @Override
    public LuaValue get(LuaValue key) {
        try {
            context.check();
            return super.get(key);
        } catch (RuntimeException exception) {
            throw NativeLuaSupport.error(exception);
        }
    }
}
