package betamoon.entity;

import betamoon.BetaMoonCommon;

import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptErrors;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.src.Entity;
import org.luaj.vm2.LuaTable;

/** Server-side behavior callback with per-definition failure isolation. */
final class EntityTick {
    private static final Set<EntityTypeDefinition> DISABLED = Collections.newSetFromMap(new WeakHashMap<>());

    private EntityTick() {
    }

    static void run(Entity entity, EntityTypeDefinition definition) {
        if (definition.onTick.isnil() || DISABLED.contains(definition) || entity.worldObj == null
                || entity.worldObj.multiplayerWorld || entity.ticksExisted % definition.tickInterval != 0) {
            return;
        }
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable context = new LuaTable();
            context.set("entity", LuaEntityActionAccess.create(scope, null, entity));
            context.set("world", LuaWorldActionAccess.create(scope, entity.worldObj, (int) Math.floor(entity.posX),
                    (int) Math.floor(entity.posY), (int) Math.floor(entity.posZ)));
            context.set("age", entity.ticksExisted);
            definition.onTick.call(context);
        } catch (RuntimeException error) {
            if (DISABLED.add(definition)) {
                String message = definition.key + ".onTick disabled after error: " + error.getMessage();
                String owner = EntityTypeRegistry.owner(definition.key);
                LuaScriptErrors.add(owner == null ? definition.key.toString() : owner, message);
                BetaMoonCommon.LOGGER.warning(message);
            }
        }
    }
}
