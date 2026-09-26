package betamoon.luaapi.entity;

import betamoon.data.DataField;
import betamoon.entity.LuaEntityPart;
import betamoon.entity.TypedEntity;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.World;
import org.luaj.vm2.LuaValue;

/** Converts stable saved references without coupling the persistence store to live entity handles. */
final class EntityDataLuaContext implements DataField.LuaContext {
    private final LuaCallbackScope scope;
    private final World world;

    EntityDataLuaContext(LuaCallbackScope scope, World world) {
        this.scope = scope;
        this.world = world;
    }

    @Override
    public DataField.ReferenceValue readReference(LuaValue value, String path) {
        if (value.isnil()) {
            return null;
        }
        if (value instanceof EntityDataReference) {
            return ((EntityDataReference) value).value(scope);
        }
        Entity entity = LuaEntityActionAccess.entityHandle(value, path);
        if (entity.isDead) {
            throw LuaDeclarationValues.error(path, "cannot save a removed entity");
        }
        if (entity instanceof EntityPlayer) {
            String username = ((EntityPlayer) entity).username;
            if (username == null || username.isEmpty()) {
                throw LuaDeclarationValues.error(path, "player has no stable name");
            }
            return new DataField.ReferenceValue("player", username);
        }
        if (entity instanceof TypedEntity && !(entity instanceof LuaEntityPart)) {
            return new DataField.ReferenceValue("entity",
                    ((TypedEntity) entity).entityState().identity());
        }
        throw LuaDeclarationValues.error(path,
                "expected a player or BetaMoon entity with a stable saved identity");
    }

    @Override
    public LuaValue writeReference(DataField.ReferenceValue value) {
        return new EntityDataReference(scope, world, value);
    }
}
