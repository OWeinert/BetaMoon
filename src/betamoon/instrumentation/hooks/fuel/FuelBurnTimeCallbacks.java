package betamoon.instrumentation.hooks.fuel;

import betamoon.fuel.FuelRegistry;
import betamoon.fuel.FuelResolution;
import net.minecraft.src.ItemStack;

/** Runtime bridge kept free of transformation logic. */
public final class FuelBurnTimeCallbacks {
    private FuelBurnTimeCallbacks() {
    }

    public static int capture(ItemStack stack) {
        return 0;
    }

    public static int result(int nativeBurnTime, ItemStack stack) {
        FuelResolution resolution = FuelRegistry.resolveRegistered(stack, FuelRegistry.FURNACE);
        return resolution.isFuel() ? resolution.burnTime : nativeBurnTime;
    }
}
