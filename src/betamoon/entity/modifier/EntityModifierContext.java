package betamoon.entity.modifier;

import betamoon.entity.motion.EntityMotionState;
import java.util.Map;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.World;

/**
 * One callback invocation's native inputs; facade factories provide fresh
 * read-only access.
 */
public final class EntityModifierContext {
    public final EntityLiving entity;
    public final World world;
    public final long tick;
    public final long generation;
    public final Object state;
    public final boolean ownsMotion;
    public final EntityMotionState movement;
    public final Map<String, Object> input;
    public final Map<String, Object> nativeIntent;

    public EntityModifierContext(EntityLiving entity, World world, long tick, long generation, Object state,
            boolean ownsMotion, EntityMotionState movement, Map<String, Object> input,
            Map<String, Object> nativeIntent) {
        this.entity = entity;
        this.world = world;
        this.tick = tick;
        this.generation = generation;
        this.state = state;
        this.ownsMotion = ownsMotion;
        this.movement = movement;
        this.input = input;
        this.nativeIntent = nativeIntent;
    }

}
