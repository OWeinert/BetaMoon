package betamoon.instrumentation.hooks.entity;

import betamoon.entity.EntityKind;
import betamoon.entity.EntityNetworkSnapshots;
import betamoon.entity.EntityPhysicsDefinition;
import betamoon.entity.EntityTypeDefinition;
import betamoon.entity.TypedEntity;
import betamoon.network.protocol.ProtocolCodec;
import betamoon.network.transport.PacketRegistration;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.src.Entity;
import net.minecraft.src.Packet;

/** Server-safe callbacks that avoid compile-time references to server-only tracker classes. */
public final class EntityTrackingCallbacks {
    private static final ProtocolCodec CODEC = new ProtocolCodec();

    private EntityTrackingCallbacks() {
    }

    public static int track(Object tracker, Entity entity) {
        if (!(entity instanceof TypedEntity)) {
            return 0;
        }
        EntityTypeDefinition definition = ((TypedEntity) entity).entityState().definition();
        if (definition == null) {
            return 0;
        }
        TrackingPolicy policy = policy(definition);
        try {
            Method method = trackingMethod(tracker.getClass());
            method.setAccessible(true);
            method.invoke(tracker, entity, Integer.valueOf(policy.range), Integer.valueOf(policy.interval),
                    Boolean.valueOf(policy.velocity));
            return 1;
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot access native entity tracking", error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            throw new IllegalStateException("Native entity tracking failed: " + cause.getMessage(), cause);
        }
    }

    public static void tracked(int custom) {
    }

    public static int custom(Entity entity) {
        return entity instanceof TypedEntity ? 1 : 0;
    }

    public static Packet spawn(Packet original, Entity entity, int custom) {
        if (custom == 0) {
            return original;
        }
        try {
            PacketRegistration.register();
            return PacketRegistration.packet(CODEC.encode(EntityNetworkSnapshots.spawn(entity)));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot encode BetaMoon entity spawn", error);
        }
    }

    private static Method trackingMethod(Class<?> trackerClass) {
        for (Method method : trackerClass.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getReturnType() == Void.TYPE && parameters.length == 4
                    && Entity.class.isAssignableFrom(parameters[0])
                    && parameters[1] == Integer.TYPE && parameters[2] == Integer.TYPE
                    && parameters[3] == Boolean.TYPE) {
                return method;
            }
        }
        throw new IllegalStateException("Native entity tracking method was not found");
    }

    private static TrackingPolicy policy(EntityTypeDefinition definition) {
        boolean moving = definition.physics.mode == EntityPhysicsDefinition.Mode.DYNAMIC;
        if (definition.kind == EntityKind.PROJECTILE) {
            return new TrackingPolicy(96, 2, true);
        }
        if (definition.kind == EntityKind.PICKUP) {
            return new TrackingPolicy(64, 10, true);
        }
        if (definition.kind == EntityKind.LIVING) {
            return new TrackingPolicy(160, 3, moving);
        }
        return new TrackingPolicy(160, 5, moving);
    }

    private static final class TrackingPolicy {
        private final int range;
        private final int interval;
        private final boolean velocity;

        private TrackingPolicy(int range, int interval, boolean velocity) {
            this.range = range;
            this.interval = interval;
            this.velocity = velocity;
        }
    }
}
