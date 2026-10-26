package betamoon.entity;

import betamoon.interop.runtime.NativeEventAccess;
import betamoon.luaapi.interop.NativeCallbackTable;

import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.ScriptExecution;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.src.Entity;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Isolates independently configurable AI decisions and expires their live
 * handles.
 */
final class EntityAiStageEvents {
    interface ResultReader<T> {
        T read(LuaValue value);
    }

    private static final Map<EntityTypeDefinition, Set<String>> DISABLED = Collections
            .synchronizedMap(new WeakHashMap<>());

    private EntityAiStageEvents() {
    }

    static <T> T call(LuaLivingEntity entity, EntityTypeDefinition definition, String name,
            EntityAiStageDefinition stage, String navigationStatus, ResultReader<T> reader, T fallback) {
        if (entity.worldObj == null || entity.worldObj.multiplayerWorld || disabled(definition, name)
                || entity.ticksExisted % stage.intervalTicks != 0) {
            return fallback;
        }
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            NativeEventAccess.configure(scope, entity.worldObj, entity);
            LuaTable context = new NativeCallbackTable(scope);
            context.set("entity", LuaEntityActionAccess.create(scope, null, entity));
            context.set("world", LuaWorldActionAccess.create(scope, entity.worldObj, (int) Math.floor(entity.posX),
                    (int) Math.floor(entity.posY), (int) Math.floor(entity.posZ), entity));
            context.set("age", entity.ticksExisted);
            context.set("navigationStatus", navigationStatus);
            Entity target = entity.getTarget();
            if (target != null && !target.isDead) {
                context.set("target", LuaEntityActionAccess.create(scope, null, target));
            }
            return reader.read(ScriptExecution
                    .invoke(EntityTypeRegistry.owner(definition.key), "entity.ai." + name, stage.routine, context)
                    .arg1());
        } catch (RuntimeException error) {
            if (DISABLED.computeIfAbsent(definition, ignored -> new HashSet<>()).add(name)) {
                String message = definition.key + ".living.ai.stages." + name + ".routine disabled after error: "
                        + error.getMessage();
                String owner = EntityTypeRegistry.owner(definition.key);
                LuaScriptErrors.add(owner == null ? definition.key.toString() : owner, message,
                        LuaScriptErrors.Outcome.CALLBACK_DISABLED, error);
            }
            return fallback;
        }
    }

    static boolean disabled(EntityTypeDefinition definition, String name) {
        Set<String> stages = DISABLED.get(definition);
        return stages != null && stages.contains(name);
    }
}
