package betamoon.luaapi.item;

import net.minecraft.src.Item;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;
import static betamoon.luaapi.utils.LuaDeclarationValues.internalName;

/** Complete item declaration, read before changing native item state. */
final class ItemDeclaration {
    final int id;
    final String name;
    final String displayName;
    final ItemKind kind;
    final ItemDefinition callbacks;
    final ToolDeclaration tool;
    final ArmorDeclaration armor;
    final int healing;
    final boolean wolfFood;
    final Integer maxStackSize;
    final Integer maxDamage;
    final Boolean hasSubtypes;
    final Float efficiency;
    final Integer damage;
    final boolean full3D;
    final Integer iconX;
    final Integer iconY;
    final String texture;

    ItemDeclaration(LuaValue definition) {
        callbacks = new ItemDefinition(definition);
        id = required(definition, "id").checkint();
        name = internalName(definition);
        kind = ItemKind.parse(definition.get("type").optjstring("item"));
        String domain = kind.isTool() ? "Tool" : kind == ItemKind.ARMOR ? "Armor" : "Item";
        if (id < 256) {
            throw new LuaError(domain + ": id must be a shifted id (>= 256): " + id);
        }
        if (id >= Item.itemsList.length) {
            throw new LuaError(domain + ": id out of range: " + id);
        }
        displayName = definition.get("displayName").optjstring(name);
        tool = kind.isTool() ? new ToolDeclaration(definition) : null;
        armor = kind == ItemKind.ARMOR ? new ArmorDeclaration(definition) : null;
        LuaValue food = definition.get("food");
        healing = kind == ItemKind.FOOD
                ? (food.istable() ? food.get("healing").checkint() : required(definition, "healing").checkint())
                : 0;
        wolfFood = kind == ItemKind.FOOD && food.istable() && food.get("wolfFood").toboolean();
        maxStackSize = optionalInteger(definition.get("maxStackSize"));
        maxDamage = optionalInteger(definition.get("maxDamage"));
        LuaValue subtypes = definition.get("hasSubtypes");
        hasSubtypes = subtypes.isnil() ? null : Boolean.valueOf(subtypes.toboolean());
        LuaValue mining = definition.get("efficiency");
        efficiency = mining.isnil() ? null : Float.valueOf((float) mining.checkdouble());
        damage = optionalInteger(definition.get("damageVsEntity"));
        full3D = definition.get("full3D").toboolean();
        LuaValue icon = definition.get("icon");
        iconX = icon.istable() ? Integer.valueOf(icon.get("x").checkint()) : null;
        iconY = icon.istable() ? Integer.valueOf(icon.get("y").checkint()) : null;
        LuaValue image = definition.get("texture");
        texture = image.isstring() ? image.checkjstring() : null;
        validateCapabilities();
    }

    private void validateCapabilities() {
        if (kind.isTool() || kind == ItemKind.ARMOR) {
            unsupported(maxStackSize, "setMaxStackSize");
            unsupported(hasSubtypes, "setHasSubtypes");
        }
        if (kind == ItemKind.ARMOR) {
            unsupported(maxDamage, "setMaxDamage");
        }
        if (!kind.isTool()) {
            unsupported(efficiency, "setEfficiency");
            unsupported(damage, "setDamageVsEntity");
        } else {
            if (efficiency != null && !ConfigurableMiningEfficiency.class.isAssignableFrom(kind.toolClass)) {
                throw new LuaError("Tool: type does not support efficiency.");
            }
            if (damage != null && !ConfigurableAttackDamage.class.isAssignableFrom(kind.toolClass)) {
                throw new LuaError("Tool: type does not support damage.");
            }
        }
    }

    private static void unsupported(Object value, String method) {
        if (value != null) {
            throw new LuaError("Resource does not support " + method + ".");
        }
    }

    private static Integer optionalInteger(LuaValue value) {
        return value.isnil() ? null : Integer.valueOf((int) value.checkdouble());
    }
}
