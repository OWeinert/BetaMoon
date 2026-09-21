package betamoon.instrumentation.hooks.entity;

import betamoon.entity.EntityNaturalSpawner;
import net.minecraft.src.World;

/** Runtime boundary appended to the vanilla natural-spawn pass. */
public final class EntityNaturalSpawnCallbacks {
    private EntityNaturalSpawnCallbacks() {
    }

    public static int entering() {
        return 0;
    }

    public static int after(World world, boolean hostile, boolean peaceful, int vanillaCount) {
        return vanillaCount + EntityNaturalSpawner.spawn(world, hostile, peaceful);
    }
}
