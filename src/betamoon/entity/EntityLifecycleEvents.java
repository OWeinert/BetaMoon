package betamoon.entity;

import betamoon.BetaMoonCommon;

import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptErrors;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.src.Chunk;
import net.minecraft.src.Entity;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** One-shot lifecycle reactions; failed callbacks are disabled independently. */
public final class EntityLifecycleEvents {
    private static final Map<EntityTypeDefinition, Set<String>> DISABLED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private EntityLifecycleEvents() {
    }

    static void spawned(Entity entity, String reason) {
        dispatch(entity, "onSpawn", reason, null);
        activate(entity, "spawn");
    }

    static void loaded(Entity entity, EntityInstanceState state) {
        if (state.definition() != null && state.takePendingLoad()) {
            dispatch(entity, "onLoad", null, null);
        }
        if (state.definition() != null) {
            activate(entity, state.nextActivationReason());
        }
    }

    static void definitionMissing(Entity entity, EntityInstanceState state) {
        deactivate(entity, state, "definition_missing", "definition_restored");
        state.deactivate("definition_restored");
    }

    public static void chunkUnloaded(Chunk chunk) {
        if (chunk == null || chunk.worldObj == null || chunk.worldObj.multiplayerWorld) {
            return;
        }
        for (List entities : chunk.entities) {
            for (Object value : entities.toArray()) {
                if (value instanceof TypedEntity && !(value instanceof LuaEntityPart)) {
                    Entity entity = (Entity) value;
                    deactivate(entity, ((TypedEntity) entity).entityState(), "chunk_unload", "chunk_load");
                }
            }
        }
    }

    public static void simulationPaused(Entity entity) {
        if (entity instanceof TypedEntity && !(entity instanceof LuaEntityPart) && !entity.isDead) {
            deactivate(entity, ((TypedEntity) entity).entityState(), "simulation_paused", "simulation_resumed");
        }
    }

    static void died(Entity entity, EntityInstanceState state, Entity attacker) {
        if (state.markDeathNotified()) {
            state.removalReason("death");
            EntityTypeDefinition definition = state.definition();
            if (definition != null) {
                EntityLoot.dropLoot(entity);
                EntityPresentationEvents.sound(entity,
                        definition.sounds.get(EntitySoundsDefinition.Event.DEATH));
            }
            dispatch(entity, "onDeath", null, attacker);
        }
    }

    static void removed(Entity entity, EntityInstanceState state) {
        deactivate(entity, state, state.removalReason(), "chunk_load");
        if (state.markRemoveNotified()) {
            dispatch(entity, "onRemove", state.removalReason(), null);
        }
    }

    private static void activate(Entity entity, String reason) {
        if (entity.isDead || !(entity instanceof TypedEntity)) {
            return;
        }
        EntityInstanceState state = ((TypedEntity) entity).entityState();
        if (state.active() || state.definition() == null) {
            return;
        }
        state.activate();
        dispatch(entity, "onActivate", reason, null);
    }

    private static void deactivate(Entity entity, EntityInstanceState state, String reason, String nextReason) {
        EntitySensorManager sensors = state.sensorsIfPresent();
        if (sensors != null) {
            sensors.deactivate(reason);
        }
        EntityPresentationState presentation = state.presentationIfPresent();
        if (presentation != null) {
            presentation.deactivate();
        }
        if (!state.active()) {
            return;
        }
        state.deactivate(nextReason);
        dispatch(entity, "onDeactivate", reason, null);
    }

    private static void dispatch(Entity entity, String event, String reason, Entity attacker) {
        if (!(entity instanceof TypedEntity) || entity.worldObj == null || entity.worldObj.multiplayerWorld) {
            return;
        }
        EntityTypeDefinition definition = ((TypedEntity) entity).entityState().definition();
        if (definition == null) {
            return;
        }
        LuaValue callback = callback(definition, event);
        if (callback.isnil() || disabled(definition, event)) {
            return;
        }
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable context = new LuaTable();
            context.set("entity", LuaEntityActionAccess.create(scope, null, entity));
            context.set("world", LuaWorldActionAccess.create(scope, entity.worldObj,
                    (int) Math.floor(entity.posX), (int) Math.floor(entity.posY), (int) Math.floor(entity.posZ)));
            context.set("age", entity.ticksExisted);
            if (reason != null) {
                context.set("reason", reason);
            }
            if (attacker != null && !attacker.isDead && attacker.worldObj == entity.worldObj) {
                context.set("attacker", LuaEntityActionAccess.create(scope, null, attacker));
            }
            callback.call(context);
        } catch (RuntimeException error) {
            disable(definition, event);
            String message = definition.key + "." + event + " disabled after error: " + error.getMessage();
            String owner = EntityTypeRegistry.owner(definition.key);
            LuaScriptErrors.add(owner == null ? definition.key.toString() : owner, message);
            BetaMoonCommon.LOGGER.warning(message);
        }
    }

    private static LuaValue callback(EntityTypeDefinition definition, String event) {
        if ("onSpawn".equals(event)) {
            return definition.onSpawn;
        }
        if ("onLoad".equals(event)) {
            return definition.onLoad;
        }
        if ("onDeath".equals(event)) {
            return definition.onDeath;
        }
        if ("onActivate".equals(event)) {
            return definition.onActivate;
        }
        if ("onDeactivate".equals(event)) {
            return definition.onDeactivate;
        }
        return definition.onRemove;
    }

    private static boolean disabled(EntityTypeDefinition definition, String event) {
        Set<String> events = DISABLED.get(definition);
        return events != null && events.contains(event);
    }

    private static void disable(EntityTypeDefinition definition, String event) {
        DISABLED.computeIfAbsent(definition, ignored -> new HashSet<>()).add(event);
    }
}
