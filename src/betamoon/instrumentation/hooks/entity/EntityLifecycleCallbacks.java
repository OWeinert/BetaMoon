package betamoon.instrumentation.hooks.entity;

import betamoon.entity.EntityLifecycleEvents;
import net.minecraft.src.Chunk;
import net.minecraft.src.Entity;

/** Narrow bridge from native world scheduling to the entity lifecycle contract. */
public final class EntityLifecycleCallbacks {
    private EntityLifecycleCallbacks() {
    }

    public static int entering() {
        return 0;
    }

    public static void chunkUnloaded(Chunk chunk) {
        EntityLifecycleEvents.chunkUnloaded(chunk);
    }

    public static int beforeUpdate(Entity entity) {
        return entity.ticksExisted;
    }

    public static void afterUpdate(Entity entity, boolean forced, int previousAge) {
        if (forced && entity.ticksExisted == previousAge) {
            EntityLifecycleEvents.simulationPaused(entity);
        }
    }
}
