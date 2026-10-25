package betamoon.entity.motion;

import betamoon.entity.EntityLifecycle;
import betamoon.entity.EntityPhysicsDefinition;
import betamoon.entity.EntityTypeDefinition;
import betamoon.entity.LuaLivingEntity;
import betamoon.entity.modifier.EntityModifierDefinition;
import betamoon.entity.modifier.EntityMovementRule;
import betamoon.instrumentation.api.RequiredNativeHooks;
import betamoon.instrumentation.hooks.entity.EntityMotionHook;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EntityPlayerSP;

/**
 * Explicit support for existing native walking paths, never inferred from
 * EntityLiving alone.
 */
public final class EntityMovementSupport {
    private static final Set<String> WALKING = new HashSet<>(Arrays.asList("EntityPig", "EntityCow", "EntitySheep",
            "EntityChicken", "EntityWolf", "EntityCreeper", "EntityZombie", "EntityPigZombie", "EntitySkeleton",
            "EntitySpider", "EntitySlime", "EntityGiantZombie", "EntityPlayerSP"));
    private static final ClassValue<Boolean> NATIVE_MOVEMENT = new ClassValue<Boolean>() {
        protected Boolean computeValue(Class<?> type) {
            Class<?> heading = NativeLivingFields.methodOwner(type, "moveEntityWithHeading", "(FF)V", float.class,
                    float.class);
            Class<?> jumping = NativeLivingFields.methodOwner(type, "jump", "()V");
            return (heading == EntityLiving.class || heading == EntityPlayer.class)
                    && (jumping == EntityLiving.class || jumping == EntityPlayer.class);
        }
    };

    private EntityMovementSupport() {
    }

    public static boolean knownPath(String path) {
        return EntityMovementRule.find(path) != null || "onAttach".equals(path) || "onDetach".equals(path)
                || "onMotion".equals(path) || "onForces".equals(path) || "onAfterMotion".equals(path);
    }

    public static boolean supports(Entity entity, String path) {
        if (!knownPath(path)) {
            throw new IllegalArgumentException("Unknown entity modification path: " + path);
        }
        if (reason(entity) != null
                || path.equals("movement.sneakBodyAdjustment") && !(entity instanceof EntityPlayerSP)) {
            return false;
        }
        try {
            requireHooks(entity instanceof EntityPlayerSP, false);
            return true;
        } catch (IllegalStateException unavailable) {
            return false;
        }
    }

    public static void requireHooks(boolean player, boolean allowPending) {
        RequiredNativeHooks.require("Entity movement modifiers", allowPending, EntityMotionHook.CORE_IDS);
        if (player) {
            RequiredNativeHooks.require("Local player movement modifiers", allowPending, EntityMotionHook.PLAYER_IDS);
            RequiredNativeHooks.require("Local player movement input", allowPending,
                    "betamoon:client_control:movement_intent", "betamoon:client_control:movement_key");
        }
    }

    public static void require(Entity entity, EntityModifierDefinition definition) {
        String reason = reason(entity);
        if (reason != null) {
            throw new IllegalArgumentException("Entity movement modifiers unavailable: " + reason);
        }
        if (!(entity instanceof EntityPlayerSP)
                && definition.rules.get(EntityMovementRule.SNEAK_BODY_ADJUSTMENT) != null) {
            throw new IllegalArgumentException("movement.sneakBodyAdjustment requires the local player movement path");
        }
        requireHooks(entity instanceof EntityPlayerSP, false);
    }

    public static String reason(Entity entity) {
        if (!(entity instanceof EntityLiving) || entity.worldObj == null || entity.isDead
                || ((EntityLiving) entity).health <= 0) {
            return "entity_unavailable";
        }
        if (entity.worldObj.multiplayerWorld) {
            return "unsupported_environment";
        }
        if (entity.noClip) {
            return "unsupported_collision_mode";
        }
        if (entity instanceof LuaLivingEntity) {
            EntityTypeDefinition definition = ((LuaLivingEntity) entity).entityState().definition();
            if (definition == null || definition.lifecycle != EntityLifecycle.NATIVE
                    || definition.physics.mode == EntityPhysicsDefinition.Mode.STATIC) {
                return "unsupported_lifecycle";
            }
        } else {
            String type = NativeLivingFields.canonicalClass(entity.getClass());
            if (!type.startsWith("net/minecraft/src/")
                    || !WALKING.contains(type.substring("net/minecraft/src/".length()))) {
                return "unsupported_entity";
            }
        }
        if (!NATIVE_MOVEMENT.get(entity.getClass())) {
            return "unsupported_movement_override";
        }
        return null;
    }
}
