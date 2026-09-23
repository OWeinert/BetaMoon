package betamoon.instrumentation.hooks.fuel;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/** Adds full ItemStack-aware BetaMoon fuel rules to vanilla furnaces. */
public final class FuelBurnTimeHook implements HookModule {
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/fuel/FuelBurnTimeCallbacks";

    @Override
    public String getId() {
        return "betamoon:furnace_fuel";
    }

    @Override
    public void register(HookRegistrar registrar) {
        MethodRef target = new MethodRef(new ClassRef("net/minecraft/src/TileEntityFurnace"), "getItemBurnTime",
                "(Lnet/minecraft/src/ItemStack;)I");
        registrar.register(AroundHookDefinition.builder(getId(), target)
                .capture(HandlerRef.of(CALLBACKS, "capture", "(Lnet/minecraft/src/ItemStack;)I"),
                        ValueBinding.argument(0))
                .onReturn(HandlerRef.of(CALLBACKS, "result", "(ILnet/minecraft/src/ItemStack;)I"),
                        ValueBinding.returnValue(), ValueBinding.argument(0))
                .build());
    }
}
