package betamoon.instrumentation.registry;

import betamoon.instrumentation.hooks.block.BlockBreakGuardHook;
import betamoon.instrumentation.hooks.block.BlockBrokenHook;
import betamoon.instrumentation.hooks.block.BlockDisplayTickHook;
import betamoon.instrumentation.hooks.block.BlockPlacedHook;
import betamoon.instrumentation.hooks.block.BlockPowerHook;
import betamoon.instrumentation.hooks.block.BlockScheduleHook;
import betamoon.instrumentation.hooks.block.ContentCallbackOverrideHook;
import betamoon.instrumentation.hooks.control.ClientControlHook;
import betamoon.instrumentation.hooks.entity.EntityLifecycleHook;
import betamoon.instrumentation.hooks.entity.EntityNaturalSpawnHook;
import betamoon.instrumentation.hooks.entity.EntityTrackingHook;
import betamoon.instrumentation.hooks.event.TickEventHook;
import betamoon.instrumentation.hooks.fluid.FluidCameraHook;
import betamoon.instrumentation.hooks.fluid.FluidContactHook;
import betamoon.instrumentation.hooks.fluid.FluidEcologyHook;
import betamoon.instrumentation.hooks.fluid.FluidInteractionHook;
import betamoon.instrumentation.hooks.fuel.FuelBurnTimeHook;
import betamoon.instrumentation.hooks.item.ItemHarvestHook;
import betamoon.instrumentation.hooks.item.ItemInteractionHook;
import betamoon.instrumentation.hooks.model.ModelRenderHook;
import betamoon.instrumentation.hooks.multiblock.MultiblockChunkHook;
import betamoon.instrumentation.hooks.multiblock.MultiblockClientChunkHook;
import betamoon.instrumentation.hooks.particle.ParticleHook;
import betamoon.instrumentation.hooks.texture.TextureResourceHook;
import betamoon.instrumentation.hooks.worldgen.AtmosphereHook;
import betamoon.instrumentation.hooks.worldgen.RegionalStructureChunkHook;
import betamoon.instrumentation.hooks.worldgen.WorldGeneratorHook;
import betamoon.instrumentation.hooks.worldgen.WorldCreationHook;
import betamoon.instrumentation.hooks.worldgen.WorldJoinHook;
import betamoon.instrumentation.hooks.worldgen.WorldPopulationHook;
import betamoon.instrumentation.hooks.worldgen.WorldgenSurfaceHook;
import betamoon.runtime.RuntimeSide;

/** Registers the hooks shipped in the BetaMoon JAR. */
public final class BuiltinHookModules {
    private BuiltinHookModules() {
    }

    public static void registerAll(HookRegistry registry) {
        registerCommon(registry);
        registerClient(registry);
    }

    public static void registerForSide(HookRegistry registry, RuntimeSide side) {
        registerCommon(registry);
        if (side == RuntimeSide.DEDICATED_SERVER) {
            registerServer(registry);
        } else {
            registerClient(registry);
        }
    }

    private static void registerCommon(HookRegistry registry) {
        registry.registerModule(new ContentCallbackOverrideHook());
        registry.registerModule(new ItemHarvestHook());
        registry.registerModule(new BlockPowerHook());
        registry.registerModule(new BlockScheduleHook());
        registry.registerModule(new MultiblockChunkHook());
        registry.registerModule(new FluidInteractionHook());
        registry.registerModule(new FluidContactHook());
        registry.registerModule(new EntityLifecycleHook());
        registry.registerModule(new EntityNaturalSpawnHook());
        registry.registerModule(new WorldgenSurfaceHook());
        registry.registerModule(new RegionalStructureChunkHook());
    }

    private static void registerClient(HookRegistry registry) {
        registry.registerModule(new FluidEcologyHook(false));
        registry.registerModule(new WorldGeneratorHook());
        registry.registerModule(new WorldCreationHook());
        registry.registerModule(new WorldJoinHook());
        registry.registerModule(new WorldPopulationHook());
        registry.registerModule(new AtmosphereHook());
        registry.registerModule(new BlockBrokenHook());
        registry.registerModule(new BlockDisplayTickHook());
        registry.registerModule(new BlockBreakGuardHook());
        registry.registerModule(new ItemInteractionHook());
        registry.registerModule(new BlockPlacedHook());
        registry.registerModule(new TextureResourceHook());
        registry.registerModule(new ModelRenderHook());
        registry.registerModule(new FuelBurnTimeHook());
        registry.registerModule(new ClientControlHook());
        registry.registerModule(new TickEventHook());
        registry.registerModule(new FluidCameraHook());
        registry.registerModule(new ParticleHook());
        registry.registerModule(new MultiblockClientChunkHook());
    }

    private static void registerServer(HookRegistry registry) {
        registry.registerModule(new FluidEcologyHook(true));
        registry.registerModule(new EntityTrackingHook());
    }
}
