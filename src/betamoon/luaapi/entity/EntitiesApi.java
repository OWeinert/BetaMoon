package betamoon.luaapi.entity;

import betamoon.assets.AssetKey;
import betamoon.entity.EntityPresentationResources;
import betamoon.entity.EntityTypeDefinition;
import betamoon.luamodloader.ScriptEntityScope;
import java.io.IOException;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Typed entity registration and lookup; native construction belongs to world access. */
public final class EntitiesApi {
    private EntitiesApi() {
    }

    public static void attach(LuaTable module) {
        LuaTable entities = new LuaTable();
        entities.set("add", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                EntityTypeDefinition definition = EntityDeclaration.read(argument(args, entities));
                try {
                    EntityPresentationResources.validate(definition);
                    ScriptEntityScope.stage(definition);
                    return new EntityTypeReference(definition.key);
                } catch (IOException | IllegalArgumentException | IllegalStateException error) {
                    throw new LuaError("entities:add: " + error.getMessage());
                }
            }
        });
        entities.set("get", lookup(entities, false));
        entities.set("getRequired", lookup(entities, true));
        module.set("entities", entities);
    }

    private static VarArgFunction lookup(LuaTable entities, boolean required) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return find(argument(args, entities), required);
            }
        };
    }

    private static LuaValue find(LuaValue value, boolean required) {
        AssetKey key;
        try {
            key = AssetKey.parse(value.checkjstring());
        } catch (IllegalArgumentException error) {
            throw new LuaError("entities: " + error.getMessage());
        }
        if (ScriptEntityScope.findVisible(key) == null) {
            if (required) {
                throw new LuaError("Entity type not registered: " + key);
            }
            return LuaValue.NIL;
        }
        return new EntityTypeReference(key);
    }

    private static LuaValue argument(Varargs args, LuaValue receiver) {
        return args.arg(args.arg1() == receiver ? 2 : 1);
    }
}
