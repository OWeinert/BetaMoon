package betamoon.luaapi.entity;

import betamoon.entity.EntityDataField;
import betamoon.entity.LuaEntityPart;
import betamoon.entity.TypedEntity;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import net.minecraft.src.Entity;
import net.minecraft.src.World;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Stable saved identity that can resolve to a live scoped handle while its target is loaded. */
final class EntityDataReference extends LuaTable {
    private final LuaCallbackScope scope;
    private final World world;
    private final EntityDataField.ReferenceValue value;

    EntityDataReference(LuaCallbackScope scope, World world, EntityDataField.ReferenceValue value) {
        this.scope = scope;
        this.world = world;
        this.value = value;
        set("kind", value.kind);
        set("identity", value.identity);
        set("token", value.token());
        set("resolve", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                Entity entity = resolve();
                return entity == null ? NIL : LuaEntityActionAccess.create(scope, null, entity);
            }
        });
        set("isLoaded", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                return valueOf(resolve() != null);
            }
        });
    }

    EntityDataField.ReferenceValue value(LuaCallbackScope expectedScope) {
        if (scope != expectedScope) {
            throw LuaDeclarationValues.error("entity.data", "reference belongs to another callback");
        }
        scope.requireActive();
        return value;
    }

    private Entity resolve() {
        if (world == null) {
            return null;
        }
        if ("player".equals(value.kind)) {
            Entity player = world.getPlayerEntityByName(value.identity);
            return player == null || player.isDead ? null : player;
        }
        for (Object candidate : world.loadedEntityList) {
            if (candidate instanceof TypedEntity && !(candidate instanceof LuaEntityPart)) {
                Entity entity = (Entity) candidate;
                if (!entity.isDead && value.identity.equals(((TypedEntity) candidate).entityState().identity())) {
                    return entity;
                }
            }
        }
        return null;
    }
}
