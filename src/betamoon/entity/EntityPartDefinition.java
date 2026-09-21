package betamoon.entity;

import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;

/** Optional damage and interaction shapes on one named visual part. */
public final class EntityPartDefinition {
    public final String name;
    public final boolean hittable;
    public final EntityShapeDefinition hitbox;
    public final EntityShapeDefinition interactionBox;
    public final LuaValue onInteract;
    public final LuaValue onDamage;

    public EntityPartDefinition(String name, LuaValue value) {
        this.name = name;
        String path = "entity.parts." + name;
        fields(value, path, "hitbox", "interactionBox", "onInteract", "onDamage");
        hitbox = value.get("hitbox").isnil() ? null
                : new EntityShapeDefinition(value.get("hitbox"), path + ".hitbox");
        interactionBox = value.get("interactionBox").isnil() ? null
                : new EntityShapeDefinition(value.get("interactionBox"), path + ".interactionBox");
        hittable = hitbox != null;
        onInteract = value.get("onInteract");
        onDamage = value.get("onDamage");
        if (!onInteract.isnil() && !onInteract.isfunction()) {
            throw error(path + ".onInteract", "expected a function");
        }
        if (!onDamage.isnil() && !onDamage.isfunction()) {
            throw error(path + ".onDamage", "expected a function");
        }
        if (hitbox == null && !onDamage.isnil()) {
            throw error(path + ".onDamage", "requires a hitbox");
        }
        if (hitbox == null && interactionBox == null && !onInteract.isnil()) {
            throw error(path + ".onInteract", "requires a hitbox or interactionBox");
        }
    }
}
