package betamoon.luaapi.item;

import betamoon.wrappers.ItemAxeWrapper;
import betamoon.wrappers.ItemHoeWrapper;
import betamoon.wrappers.ItemPickaxeWrapper;
import betamoon.wrappers.ItemSpadeWrapper;
import betamoon.wrappers.ItemSwordWrapper;
import net.minecraft.src.Item;
import org.luaj.vm2.LuaError;

/** Native item families and the wrapper expected when retaining a tool. */
enum ItemKind {
    ITEM(null), FOOD(null), ARMOR(null), PICKAXE(ItemPickaxeWrapper.class), AXE(ItemAxeWrapper.class), SHOVEL(
            ItemSpadeWrapper.class), HOE(ItemHoeWrapper.class), SWORD(ItemSwordWrapper.class);

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
