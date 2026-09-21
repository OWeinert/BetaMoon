package betamoon.entity;

import betamoon.BetaMoonCommon;

import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptErrors;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Damage decisions for BetaMoon bridge entities, before and after native handling. */
public final class EntityDamageEvents {
    @FunctionalInterface
    public interface NativeDamage {
        boolean apply(int amount);
    }

    private static final Map<EntityTypeDefinition, Set<String>> DISABLED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Origin> ORIGIN = new ThreadLocal<>();

    private EntityDamageEvents() {
    }

    public static boolean withOrigin(Entity directSource, String cause, Supplier<Boolean> action) {
        Origin previous = ORIGIN.get();
        ORIGIN.set(new Origin(directSource, cause));
        try {
            return action.get();
        } finally {
            if (previous == null) {
                ORIGIN.remove();
            } else {
                ORIGIN.set(previous);
            }
        }
    }

    public static boolean apply(Entity target, Entity attacker, int requested, NativeDamage nativeDamage) {
        EntityInstanceState state = ((TypedEntity) target).entityState();
        EntityTypeDefinition definition = state.definition();
        if (target.isDead) {
            return false;
        }
        if (definition == null || target.worldObj == null || target.worldObj.multiplayerWorld) {
            return false;
        }
        if (requested <= 0 || state.damageInProgress()) {
            return false;
        }

        Origin origin = ORIGIN.get();
        Entity directSource = origin == null || origin.directSource == null ? attacker : origin.directSource;
        String cause = origin == null ? attacker == null ? "environment" : "entity" : origin.cause;
        int before = health(target, state, definition);
        state.beginDamage();
        try {
            int amount = before(target, definition, attacker, directSource, cause, requested, before);
            if (target.isDead) {
                return state.deathNotified();
            }

            boolean accepted = false;
            if (amount > 0) {
                if (definition.health != null) {
                    state.health(Math.max(0, state.health() - amount));
                    accepted = true;
                    if (state.health() == 0) {
                        EntityLifecycleEvents.died(target, state, attacker);
                    }
                } else {
                    accepted = nativeDamage.apply(amount);
                    if (definition.kind == EntityKind.PICKUP) {
                        // EntityItem always returns false even when its private health decreases.
                        accepted = true;
                        if (target.isDead) {
                            state.removalReason("destroyed");
                        }
                    }
                }
            }

            int after = health(target, state, definition);
            after(target, definition, attacker, directSource, cause, requested, amount, before, after, accepted);
            if (accepted && !target.isDead && !state.deathNotified()
                    && (before < 0 || after < before)) {
                EntityPresentationEvents.sound(target,
                        definition.sounds.get(EntitySoundsDefinition.Event.HURT));
            }
            if (definition.health != null && state.health() == 0 && !target.isDead) {
                target.setEntityDead();
            }
            return accepted;
        } finally {
            state.endDamage();
            if (target.isDead) {
                EntityLifecycleEvents.removed(target, state);
            }
        }
    }

    private static int before(Entity target, EntityTypeDefinition definition, Entity attacker, Entity directSource,
            String cause, int requested, int health) {
        if (definition.onBeforeDamage.isnil() || disabled(definition, "onBeforeDamage")) {
            return requested;
        }
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable context = context(scope, target, attacker, directSource, cause);
            context.set("amount", requested);
            if (health >= 0) {
                context.set("health", health);
            }
            LuaValue result = definition.onBeforeDamage.call(context);
            if (result.isnil()) {
                return requested;
            }
            double value = result.checkdouble();
            if (!Double.isFinite(value) || value != Math.floor(value) || value < 0 || value > 32767) {
                throw new IllegalArgumentException("onBeforeDamage must return an integer 0..32767 or nil");
            }
            return (int) value;
        } catch (RuntimeException error) {
            disable(definition, "onBeforeDamage", error);
            return 0;
        }
    }

    private static void after(Entity target, EntityTypeDefinition definition, Entity attacker, Entity directSource,
            String cause, int requested, int amount, int before, int after, boolean accepted) {
        if (definition.onAfterDamage.isnil() || disabled(definition, "onAfterDamage")) {
            return;
        }
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable context = context(scope, target, attacker, directSource, cause);
            context.set("requestedAmount", requested);
            context.set("amount", amount);
            context.set("accepted", LuaValue.valueOf(accepted));
            if (before >= 0 && after >= 0) {
                context.set("healthBefore", before);
                context.set("healthAfter", after);
                context.set("healthLost", Math.max(0, before - after));
            }
            definition.onAfterDamage.call(context);
        } catch (RuntimeException error) {
            disable(definition, "onAfterDamage", error);
        }
    }

    private static LuaTable context(LuaCallbackScope scope, Entity target, Entity attacker, Entity directSource,
            String cause) {
        LuaTable context = new LuaTable();
        context.set("entity", LuaEntityActionAccess.create(scope, null, target));
        context.set("world", LuaWorldActionAccess.create(scope, target.worldObj,
                (int) Math.floor(target.posX), (int) Math.floor(target.posY), (int) Math.floor(target.posZ)));
        context.set("cause", cause);
        if (attacker != null && attacker.worldObj == target.worldObj) {
            context.set("attacker", LuaEntityActionAccess.create(scope, null, attacker));
        }
        if (directSource != null && directSource.worldObj == target.worldObj) {
            context.set("directSource", LuaEntityActionAccess.create(scope, null, directSource));
        }
        return context;
    }

    private static int health(Entity target, EntityInstanceState state, EntityTypeDefinition definition) {
        if (target instanceof EntityLiving) {
            return Math.max(0, ((EntityLiving) target).health);
        }
        return definition.health == null ? -1 : state.health();
    }

    private static boolean disabled(EntityTypeDefinition definition, String event) {
        Set<String> events = DISABLED.get(definition);
        return events != null && events.contains(event);
    }

    private static void disable(EntityTypeDefinition definition, String event, RuntimeException error) {
        if (DISABLED.computeIfAbsent(definition, ignored -> new HashSet<>()).add(event)) {
            String message = definition.key + "." + event + " disabled after error: " + error.getMessage();
            String owner = EntityTypeRegistry.owner(definition.key);
            LuaScriptErrors.add(owner == null ? definition.key.toString() : owner, message);
            BetaMoonCommon.LOGGER.warning(message);
        }
    }

    private static final class Origin {
        private final Entity directSource;
        private final String cause;

        private Origin(Entity directSource, String cause) {
            this.directSource = directSource;
            this.cause = cause;
        }
    }
}
