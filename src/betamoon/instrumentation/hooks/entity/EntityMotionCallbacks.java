package betamoon.instrumentation.hooks.entity;

import betamoon.entity.modifier.EntityModifierRuntime;
import betamoon.entity.modifier.EntityMovementRule;
import betamoon.entity.motion.EntityMotionRuntime;
import betamoon.entity.motion.NativeEntityFields;
import betamoon.entity.motion.NativeLivingFields;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.MovementInput;

/**
 * Narrow native consumers; no installed sources means the original values and
 * movement are retained.
 */
public final class EntityMotionCallbacks {
    private static final ThreadLocal<List<EntityPlayerSP>> PLAYERS = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<EntityLiving>> FALLS = ThreadLocal.withInitial(ArrayList::new);

    private EntityMotionCallbacks() {
    }

    public static int beginLiving(EntityLiving entity) {
        return 0;
    }

    public static void finishLiving(EntityLiving entity, int ignored) {
        EntityMotionRuntime.finish(entity);
    }

    public static void abortLiving(EntityLiving entity, int ignored) {
        EntityMotionRuntime.abort(entity);
    }

    public static int beginPlayer(EntityPlayerSP player) {
        EntityModifierRuntime.synchronize(player.worldObj, player);
        int previous = PLAYERS.get().size();
        PLAYERS.get().add(player);
        return previous;
    }

    public static void finishPlayer(EntityPlayerSP player, int previous) {
        try {
            EntityMotionRuntime.finish(player);
        } finally {
            List<EntityPlayerSP> players = PLAYERS.get();
            while (players.size() > previous) {
                players.remove(players.size() - 1);
            }
        }
    }

    public static void abortPlayer(EntityPlayerSP player, int previous) {
        EntityMotionRuntime.abort(player);
        List<EntityPlayerSP> players = PLAYERS.get();
        while (players.size() > previous) {
            players.remove(players.size() - 1);
        }
    }

    public static void playerInput(MovementInput input, EntityPlayer player) {
        input.updatePlayerMoveState(player);
        if (player instanceof EntityPlayerSP) {
            EntityMotionRuntime.preparePlayer((EntityPlayerSP) player);
        }
    }

    public static boolean bodyAdjustment(MovementInput input) {
        List<EntityPlayerSP> players = PLAYERS.get();
        return input.sneak && (players.isEmpty() || EntityMotionRuntime.rules(players.get(players.size() - 1))
                .booleanValue(EntityMovementRule.SNEAK_BODY_ADJUSTMENT, true));
    }

    public static boolean jumpIntent(EntityLiving entity) {
        return EntityMotionRuntime.jumpIntent(entity, NativeLivingFields.jumping(entity));
    }

    public static int beforeHeading(EntityLiving entity) {
        return EntityMotionRuntime.beforeHeading(entity);
    }

    public static void afterHeading(EntityLiving entity) {
    }

    public static void collide(EntityLiving entity, double x, double y, double z) {
        EntityMotionRuntime.collide(entity, x, y, z);
    }

    public static double gravity(EntityLiving entity, double nativeValue) {
        return EntityMotionRuntime.rules(entity).numberValue(EntityMovementRule.GRAVITY_MULTIPLIER, nativeValue);
    }

    public static double dragY(EntityLiving entity, double nativeValue) {
        return EntityMotionRuntime.drag(entity, nativeValue, true);
    }

    public static double dragX(EntityLiving entity, double nativeValue) {
        return EntityMotionRuntime.drag(entity, nativeValue, false);
    }

    public static double dragZ(EntityLiving entity, double nativeValue) {
        return EntityMotionRuntime.drag(entity, nativeValue, false);
    }

    public static void accelerate(EntityLiving entity, float strafe, float forward, float acceleration) {
        float scaled = (float) EntityMotionRuntime.rules(entity).numberValue(EntityMovementRule.ACCELERATION_MULTIPLIER,
                acceleration);
        entity.moveFlying(strafe, forward, scaled);
    }

    public static double jumpVelocity(EntityLiving entity, double nativeValue) {
        return EntityMotionRuntime.rules(entity).numberValue(EntityMovementRule.JUMP_MULTIPLIER, nativeValue);
    }

    public static boolean edgeGuard(Entity entity) {
        return EntityMotionRuntime.rules(entity).booleanValue(EntityMovementRule.SNEAK_EDGE_GUARD, true)
                && entity.isSneaking();
    }

    public static int beforeFallTracking(Entity entity) {
        if (!EntityMotionRuntime.rules(entity).booleanValue(EntityMovementRule.TRACK_FALL_DISTANCE, true)) {
            NativeEntityFields.fallDistance(entity, 0);
            return 1;
        }
        return 0;
    }

    public static void afterFallTracking(Entity entity) {
    }

    public static int beginFall(EntityLiving entity) {
        int previous = FALLS.get().size();
        FALLS.get().add(entity);
        return previous;
    }

    public static void finishFall(EntityLiving entity, int previous) {
        List<EntityLiving> falls = FALLS.get();
        while (falls.size() > previous) {
            falls.remove(falls.size() - 1);
        }
    }

    public static double fallDamage(double nativeDistance) {
        double amount = Math.ceil(nativeDistance);
        List<EntityLiving> falls = FALLS.get();
        return falls.isEmpty()
                ? amount
                : Math.ceil(EntityMotionRuntime.rules(falls.get(falls.size() - 1))
                        .numberValue(EntityMovementRule.FALL_DAMAGE_MULTIPLIER, amount));
    }
}
