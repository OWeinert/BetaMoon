package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.runtime.NativeContext;
import java.util.Locale;
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
        diagnosticType = reference.lookupType;
        diagnosticMember = reference;
        diagnosticPlan = plan;
        diagnosticDirect = direct;
        properties.put("describe", NativeLuaSupport.scopedFunction(this, context, args -> {
            context.check();
            return host.metadata(CatalogValues.record("identity", plan.identity(), "descriptor", plan.descriptor,
                    "effect", plan.isStatic ? "initialize" : plan.effect.name().toLowerCase(Locale.ROOT),
                    "effectConfidence", plan.effect == NativeContext.Effect.UNKNOWN ? "unknown" : "curated",
                    "virtualOverridesRechecked", true, "nonPublic", plan.nonPublic, "policy",
                    direct ? "direct" : "managed", "context", context.describe()), true);
        }));
        if (reference.kind == NativeMemberRef.Kind.CONSTRUCTOR) {
            properties.put("new", NativeLuaSupport.scopedFunction(this, context, this::construct));
        } else {
            properties.put("call", NativeLuaSupport.scopedFunction(this, context, this::callSelected));
        }
    }

    Varargs callSelected(Varargs arguments) {
        context.check();
        if (arguments.narg() < 1) {
            throw new NativeAccessFailure("receiver_required", "supply an instance receiver or an explicit nil");
        }
        NativeContext execution = host.operationContext(context, arguments);
        Object receiver = host.receiver(arguments.arg1(), execution);
        Object[] input = host.arguments(arguments.subargs(2), execution);
        Object[] converted = execution.bindings.arguments(plan, input, execution);
        Object result = plan.callConverted(execution, direct, receiver, converted);
        return plan.valueType == Void.TYPE ? LuaValue.NONE : host.result(result, execution, direct);
    }

    Varargs construct(Varargs arguments) {
        context.check();
        NativeContext execution = host.operationContext(context, arguments);
        Object[] converted = execution.bindings.arguments(plan, host.arguments(arguments, execution), execution);
        return host.result(plan.callConverted(execution, direct, null, converted), execution, direct);
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
