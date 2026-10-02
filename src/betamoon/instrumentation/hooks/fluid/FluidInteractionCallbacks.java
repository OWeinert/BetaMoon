package betamoon.instrumentation.hooks.fluid;

import betamoon.BetaMoonCommon;
import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import betamoon.fluid.interaction.FluidInteractionRegistry;
import net.minecraft.src.BlockFluid;
import net.minecraft.src.World;

/** Runtime bridge for identity-aware vanilla fluid contact. */
public final class FluidInteractionCallbacks {
    private FluidInteractionCallbacks() {
    }

    public static int beforeHarden(BlockFluid block, World world, int x, int y, int z) {
        if (block == null || world == null) {
            return 0;
        }
        FluidRegistration fluid = FluidRegistry.instance().getByBlockId(block.blockID);
        if (fluid == null || !fluid.definition().isVanilla()) {
            return 0;
        }
        try {
            FluidInteractionRegistry.instance().evaluateCell(world, x, y, z);
            return 1;
        } catch (RuntimeException error) {
            BetaMoonCommon.LOGGER.warning("Vanilla fluid interaction evaluation failed: " + error);
            return 0;
        }
    }

    public static void complete() {
    }
}
