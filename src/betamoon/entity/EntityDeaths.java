package betamoon.entity;

import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;

/** Explicit fatal transitions for typed entities, including healthless props. */
public final class EntityDeaths {
    private EntityDeaths() {
    }

    public static boolean kill(Entity entity, Entity attacker) {
        if (!(entity instanceof TypedEntity) || entity instanceof LuaEntityPart || entity.isDead) {
            return false;
        }
        EntityInstanceState state = ((TypedEntity) entity).entityState();
        EntityTypeDefinition definition = state.definition();
        if (definition == null || state.deathNotified()) {
            return false;
        }

        if (entity instanceof LuaLivingEntity) {
            ((EntityLiving) entity).health = 0;
            ((LuaLivingEntity) entity).onDeath(attacker);
            return true;
        }

        if (definition.health != null) {
            state.health(0);
        }
        EntityLifecycleEvents.died(entity, state, attacker);
        entity.setEntityDead();
        return true;
    }
}
