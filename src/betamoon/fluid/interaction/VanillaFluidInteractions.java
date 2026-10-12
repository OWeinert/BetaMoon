package betamoon.fluid.interaction;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.src.Block;

/** Built-in identity-aware equivalents of Beta 1.7.3 fluid interactions. */
public final class VanillaFluidInteractions {
    public static final ContentKey LAVA_WATER_KEY = ContentKey.of("minecraft", ContentType.FLUID_INTERACTION,
            "lava_water");

    private VanillaFluidInteractions() {
    }

    public static void register(FluidRegistry fluids) {
        FluidRegistration lava = fluids.get(ContentKey.of("minecraft", ContentType.FLUID, "lava"));
        FluidRegistration water = fluids.get(ContentKey.of("minecraft", ContentType.FLUID, "water"));
        if (lava == null || water == null) {
            throw new IllegalStateException("Vanilla fluid interactions require registered water and lava.");
        }

        List<FluidInteractionOutcome> outcomes = new ArrayList<FluidInteractionOutcome>();
        outcomes.add(new FluidInteractionOutcome(Boolean.TRUE, Boolean.FALSE, 0, 0, Block.obsidian.blockID));
        outcomes.add(new FluidInteractionOutcome(Boolean.FALSE, Boolean.FALSE, 1, 4,
                Block.cobblestone.blockID));
        FluidInteractionDefinition hardening = new FluidInteractionDefinition(LAVA_WATER_KEY, "minecraft", lava,
                water, EnumSet.of(FluidFace.UP, FluidFace.NORTH, FluidFace.SOUTH, FluidFace.WEST, FluidFace.EAST),
                outcomes, FluidInteractionEffects.vanillaLavaMix());
        FluidInteractionRegistry.instance().registerPermanent(hardening);
    }
}
