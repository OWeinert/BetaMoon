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
import net.minecraft.src.Entity;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Isolated dispatch for reusable timer and state-transition callbacks. */
final class EntityBehaviorEvents {
    private static final Map<EntityTypeDefinition, Set<String>> DISABLED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private EntityBehaviorEvents() {
    }

    static void timer(Entity entity, EntityTypeDefinition type, String name, boolean repeating) {
        LuaTable extra = new LuaTable();
        extra.set("name", name);
        extra.set("repeating", LuaValue.valueOf(repeating));
        dispatch(entity, type, "onTimer", type.behavior.onTimer, extra);
    }

    static void stateEnter(Entity entity, EntityTypeDefinition type, String previous, String current) {
        dispatch(entity, type, "onStateEnter", type.behavior.onStateEnter, transition(previous, current));
    }

    static void stateExit(Entity entity, EntityTypeDefinition type, String previous, String current) {
        dispatch(entity, type, "onStateExit", type.behavior.onStateExit, transition(previous, current));
    }

    private static LuaTable transition(String previous, String current) {
        LuaTable extra = new LuaTable();
        if (previous != null && !previous.isEmpty()) {
            extra.set("previous", previous);
        }
        extra.set("state", current);
        return extra;
    }

    private static void dispatch(Entity entity, EntityTypeDefinition type, String event, LuaValue callback,
            LuaTable extra) {
        if (callback.isnil() || disabled(type, event) || entity.worldObj == null || entity.worldObj.multiplayerWorld) {
            return;
        }
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            extra.set("entity", LuaEntityActionAccess.create(scope, null, entity));
            extra.set("world", LuaWorldActionAccess.create(scope, entity.worldObj,
                    (int) Math.floor(entity.posX), (int) Math.floor(entity.posY), (int) Math.floor(entity.posZ)));
            extra.set("age", entity.ticksExisted);
            callback.call(extra);
        } catch (RuntimeException error) {
            if (DISABLED.computeIfAbsent(type, ignored -> new HashSet<>()).add(event)) {
                String message = type.key + ".behavior." + event + " disabled after error: " + error.getMessage();
                String owner = EntityTypeRegistry.owner(type.key);
                LuaScriptErrors.add(owner == null ? type.key.toString() : owner, message);
                BetaMoonCommon.LOGGER.warning(message);
            }
        }
    }

    private static boolean disabled(EntityTypeDefinition type, String event) {
        Set<String> events = DISABLED.get(type);
        return events != null && events.contains(event);
    }
}
