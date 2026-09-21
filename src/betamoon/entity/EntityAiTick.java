package betamoon.entity;

import betamoon.BetaMoonCommon;

import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptErrors;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import org.luaj.vm2.LuaTable;

/** Living decision callback, evaluated before the native movement step. */
final class EntityAiTick {
    private static final Set<EntityTypeDefinition> DISABLED = Collections.newSetFromMap(new WeakHashMap<>());

    private EntityAiTick() {
    }

    static void run(LuaLivingEntity entity, EntityTypeDefinition definition) {
        LivingDefinition policy = definition.living;
        if (policy.routine.isnil() || DISABLED.contains(definition) || entity.worldObj.multiplayerWorld
                || entity.ticksExisted % policy.routineIntervalTicks != 0) {
            return;
        }
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable context = new LuaTable();
            context.set("entity", LuaEntityActionAccess.create(scope, null, entity));
            context.set("world", LuaWorldActionAccess.create(scope, entity.worldObj, (int) Math.floor(entity.posX),
                    (int) Math.floor(entity.posY), (int) Math.floor(entity.posZ)));
            context.set("age", entity.ticksExisted);
            policy.routine.call(context);
        } catch (RuntimeException error) {
            if (DISABLED.add(definition)) {
                String message = definition.key + ".living.ai.routine disabled after error: " + error.getMessage();
                String owner = EntityTypeRegistry.owner(definition.key);
                LuaScriptErrors.add(owner == null ? definition.key.toString() : owner, message);
                BetaMoonCommon.LOGGER.warning(message);
            }
        }
    }
}
