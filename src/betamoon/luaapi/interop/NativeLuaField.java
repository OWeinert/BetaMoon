package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.value.NativeConversions;
import org.luaj.vm2.LuaValue;

final class NativeLuaField extends NativeLuaHandle {
    NativeLuaField(NativeAccessApi host, NativeContext context, boolean direct, NativeMemberPlan plan,
            Integer receiver) {
        super("Native field(" + plan.identity() + ")");
        properties.put("get",
                NativeLuaSupport.function(this,
                        args -> host.result(
                                plan.get(context, direct, receiver == null ? null : context.value(receiver)), context,
                                direct)));
        properties.put("set", NativeLuaSupport.function(this, args -> {
            Object value = host.argument(args.arg1(), context);
            context.bindings.validateNull(value, plan.valueType, context);
            plan.set(context, direct, receiver == null ? null : context.value(receiver),
                    NativeConversions.convert(value, plan.valueType));
            return LuaValue.NONE;
        }));
    }
}
