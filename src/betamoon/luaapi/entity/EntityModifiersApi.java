package betamoon.luaapi.entity;

import betamoon.entity.modifier.EntityModifierDefinition;
import betamoon.entity.modifier.EntityModifierRuntime.Source;
import betamoon.entity.modifier.EntityModifierRuntime;
import betamoon.entity.modifier.EntityMovementRule;
import betamoon.entity.modifier.EntityResolvedRule;
import betamoon.entity.modifier.EntityResolvedRules;
import betamoon.entity.motion.EntityMotionRuntime;
import betamoon.entity.motion.EntityMovementSupport;
import betamoon.entity.motion.EntitySimulationThread;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luaapi.utils.LuaDetachedData;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.runtime.RuntimeEnvironment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Public entity modification facade; definitions, native runtime and callback
 * scopes remain separate.
 */
public final class EntityModifiersApi {
    private EntityModifiersApi() {
    }

    static void attach(LuaTable api, LuaCallbackScope scope, Entity entity) {
        api.set("modify", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireMutable();
                EntitySimulationThread.requireCurrent();
                String owner = requireOwner();
                EntityModifierDefinition definition = EntityModifierDeclarations.parse(argument(args, api, 1), owner);
                try {
                    if (!(entity instanceof EntityLiving)) {
                        throw new IllegalArgumentException(
                                "Entity does not use a supported native living movement path");
                    }
                    Source source = EntityModifierRuntime.attach(owner, (EntityLiving) entity, definition);
                    ScriptResourceTracker.track(source::close);
                    return new LuaModifierHandle(source);
                } catch (IllegalArgumentException | IllegalStateException error) {
                    throw LuaDeclarationValues.error("entity.modify", error.getMessage());
                }
            }
        });
        api.set("supportsModification", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireActive();
                EntitySimulationThread.requireCurrent();
                String path = LuaDeclarationValues.string(argument(args, api, 1), "entity.supportsModification.path");
                try {
                    return valueOf(EntityMovementSupport.supports(entity, path));
                } catch (IllegalArgumentException error) {
                    throw LuaDeclarationValues.error("entity.supportsModification", error.getMessage());
                }
            }
        });
        api.set("getModifications", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireActive();
                EntitySimulationThread.requireCurrent();
                LuaValue options = argument(args, api, 1);
                if (!options.isnil()) {
                    LuaDeclarationValues.fields(options, "entity.getModifications", "sources");
                }
                boolean includeSources = !options.isnil()
                        && LuaDeclarationValues.bool(options.get("sources"), "entity.getModifications.sources", false);
                return LuaDetachedData.copy(describe(entity, includeSources));
            }
        });
    }

    public static LuaValue registerLocal(LuaValue value) {
        EntitySimulationThread.requireCurrent();
        LuaCallbackScope.requireGameplayWrite();
        if (!RuntimeEnvironment.isClient()) {
            throw LuaDeclarationValues.error("players.modifyLocal", "unsupported_environment");
        }
        String owner = requireOwner();
        EntityModifierDefinition definition = EntityModifierDeclarations.parse(value, owner);
        try {
            Source source = EntityModifierRuntime.registerLocal(owner, definition);
            ScriptResourceTracker.track(source::close);
            return new LuaModifierHandle(source);
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw LuaDeclarationValues.error("players.modifyLocal", error.getMessage());
        }
    }

    private static String requireOwner() {
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) {
            throw LuaDeclarationValues.error("modifier", "declarations require a script owner");
        }
        return owner;
    }

    private static LuaValue argument(Varargs args, LuaTable receiver, int index) {
        return args.arg(index + (args.arg1() == receiver ? 1 : 0));
    }

    private static Map<String, Object> describe(Entity entity, boolean includeSources) {
        EntityResolvedRules resolved = EntityMotionRuntime.rules(entity);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supported", EntityMovementSupport.supports(entity, "onMotion"));
        Map<String, Object> values = new LinkedHashMap<>();
        List<String> capped = new ArrayList<>();
        for (Map.Entry<EntityMovementRule, EntityResolvedRule> entry : resolved.asMap().entrySet()) {
            put(values, entry.getKey().path(), entry.getValue().value());
            if (entry.getValue().isCapped()) {
                capped.add(entry.getKey().path());
            }
        }
        result.put("rules", values);
        result.put("capped", capped);
        if (includeSources) {
            List<Map<String, Object>> sources = new ArrayList<>();
            if (entity instanceof EntityLiving) {
                for (Source source : EntityModifierRuntime.sources((EntityLiving) entity)) {
                    Map<String, Object> status = new LinkedHashMap<>(source.status());
                    status.put("priority", source.definition().priority);
                    Map<String, Object> contributions = new LinkedHashMap<>();
                    for (Map.Entry<EntityMovementRule, Object> entry : source.definition().rules.asMap().entrySet()) {
                        put(contributions, entry.getKey().path(), entry.getValue());
                    }
                    status.put("rules", contributions);
                    sources.add(status);
                }
            }
            result.put("sources", sources);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void put(Map<String, Object> target, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = target;
        for (int i = 0; i < parts.length - 1; i++) {
            Object child = current.get(parts[i]);
            if (child == null) {
                child = new LinkedHashMap<String, Object>();
                current.put(parts[i], child);
            }
            current = (Map<String, Object>) child;
        }
        current.put(parts[parts.length - 1], value);
    }
}
