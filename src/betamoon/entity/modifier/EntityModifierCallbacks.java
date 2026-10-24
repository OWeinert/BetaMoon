package betamoon.entity.modifier;

import betamoon.entity.motion.EntityMotionCommand;
import betamoon.entity.motion.EntityMotionResult;
import betamoon.entity.motion.MotionVector;

/**
 * Native-facing callback contracts; Lua parsing and callback scopes belong to
 * the facade layer.
 */
public final class EntityModifierCallbacks {
    public interface StateFactory {
        Object create();
    }
    public interface Attachment {
        void call(EntityModifierContext context);
    }
    public interface Detachment {
        void call(EntityModifierContext context, String reason);
    }
    public interface Motion {
        EntityMotionCommand call(EntityModifierContext context);
    }
    public interface Forces {
        MotionVector call(EntityModifierContext context);
    }
    public interface Observation {
        void call(EntityModifierContext context, EntityMotionResult result);
    }

    public static final EntityModifierCallbacks EMPTY = new EntityModifierCallbacks(() -> null, null, null, null, null,
            null);

    public final StateFactory stateFactory;
    public final Attachment onAttach;
    public final Detachment onDetach;
    public final Motion onMotion;
    public final Forces onForces;
    public final Observation onAfterMotion;

    public EntityModifierCallbacks(StateFactory stateFactory, Attachment onAttach, Detachment onDetach, Motion onMotion,
            Forces onForces, Observation onAfterMotion) {
        if (stateFactory == null) {
            throw new IllegalArgumentException("Modifier binding state factory is required");
        }
        this.stateFactory = stateFactory;
        this.onAttach = onAttach;
        this.onDetach = onDetach;
        this.onMotion = onMotion;
        this.onForces = onForces;
        this.onAfterMotion = onAfterMotion;
    }
}
