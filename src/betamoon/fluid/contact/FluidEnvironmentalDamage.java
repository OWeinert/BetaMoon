package betamoon.fluid.contact;

import betamoon.fluid.FluidRegistration;
import net.minecraft.src.Entity;

/** Boundary for native environmental damage until the shared actor damage pipeline exists. */
final class FluidEnvironmentalDamage {
    private FluidEnvironmentalDamage() {
    }

    static void apply(Entity entity, FluidRegistration fluid, int amount) {
        if (fluid == null || amount <= 0) {
            throw new IllegalArgumentException("Fluid damage needs a registered source and positive amount.");
        }
        entity.attackEntityFrom(null, amount);
    }
}
