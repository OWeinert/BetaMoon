package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.value.NativeConversions;
import org.luaj.vm2.LuaValue;

final class NativeLuaField extends NativeLuaHandle {
    NativeLuaField(NativeAccessApi host, NativeContext context, boolean direct, NativeMemberPlan plan,
            Integer receiver) {
        super("Native field(" + plan.identity() + ")");
        properties.put("get", NativeLuaSupport.scopedFunction(this, context, args -> {
            NativeContext execution = receiver == null ? context : context.ownershipContext(receiver);
            Object value = plan.get(execution, direct, receiver == null ? null : context.value(receiver));
            return host.result(value, execution, direct);
        }));
        properties.put("set", NativeLuaSupport.scopedFunction(this, context, args -> {
            NativeContext execution = receiver == null ? context : context.ownershipContext(receiver);
            Object value = host.argument(args.arg1(), execution);
            execution.bindings.validateNull(value, plan.valueType, execution);
            plan.set(execution, direct, receiver == null ? null : context.value(receiver),
                    NativeConversions.convert(value, plan.valueType));
            return LuaValue.NONE;
        }));
    }
}
