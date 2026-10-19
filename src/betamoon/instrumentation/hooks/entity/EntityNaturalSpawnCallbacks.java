package betamoon.instrumentation.hooks.entity;

import betamoon.entity.EntityNaturalSpawner;
import betamoon.worldgen.GenerationStage;
import betamoon.worldgen.terrain.WorldGenerationRuntime;
import net.minecraft.src.World;

/** Runtime boundary appended to the vanilla natural-spawn pass. */
public final class EntityNaturalSpawnCallbacks {
    private EntityNaturalSpawnCallbacks() {
    }

    public static int entering() {
        return 0;
    }

    public static int after(World world, boolean hostile, boolean peaceful, int vanillaCount) {
        if (!WorldGenerationRuntime.stageEnabled(world, GenerationStage.ENTITIES)) {
            return vanillaCount;
        }
        return vanillaCount + EntityNaturalSpawner.spawn(world, hostile, peaceful);
    }
}
