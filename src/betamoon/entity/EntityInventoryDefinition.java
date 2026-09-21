package betamoon.entity;

import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.bool;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** Saved container capability for props and living entities. */
public final class EntityInventoryDefinition {
    public final int size;
    public final String title;
    public final boolean openOnInteract;
    public final boolean dropOnDeath;

    public EntityInventoryDefinition(LuaValue value, String fallbackTitle) {
        fields(value, "entity.inventory", "size", "title", "openOnInteract", "dropOnDeath");
        size = integer(value.get("size"), "entity.inventory.size", 9, 54);
        if (size % 9 != 0) {
            throw error("entity.inventory.size", "expected a multiple of 9");
        }
        title = value.get("title").isnil() ? fallbackTitle : string(value.get("title"), "entity.inventory.title");
        if (title.isEmpty() || title.length() > 64) {
            throw error("entity.inventory.title", "expected 1..64 characters");
        }
        openOnInteract = bool(value.get("openOnInteract"), "entity.inventory.openOnInteract", true);
        dropOnDeath = bool(value.get("dropOnDeath"), "entity.inventory.dropOnDeath", false);
    }
}
