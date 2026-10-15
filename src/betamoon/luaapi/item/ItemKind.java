package betamoon.luaapi.item;

import net.minecraft.src.Item;
import org.luaj.vm2.LuaError;

/**
 * Native item families and the implementation expected when retaining a tool.
 */
enum ItemKind {
    ITEM(null), FOOD(null), ARMOR(null), PICKAXE(LuaPickaxeItem.class), AXE(LuaAxeItem.class), SHOVEL(
            LuaSpadeItem.class), HOE(LuaHoeItem.class), SWORD(LuaSwordItem.class);

    final Class<? extends Item> toolClass;

    ItemKind(Class<? extends Item> toolClass) {
        this.toolClass = toolClass;
    }

    boolean isTool() {
        return toolClass != null;
    }

    static ItemKind parse(String name) {
        for (ItemKind kind : values()) {
            if (kind.name().equalsIgnoreCase(name)) {
                return kind;
            }
        }
        throw new LuaError("Unknown item/tool type: " + name);
    }
}
