package betamoon.luaapi.item;

import net.minecraft.src.Item;
import betamoon.luamodloader.LuaContentRegistry;
import betamoon.luaapi.resource.ResourceApi;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaValue;

/** Declarative entry point for items, food, tools and armor. */
public final class ItemApi {
    private ItemApi() {
    }

    /** Preserves the read-only item handle exposed by item-use events. */
    public static LuaValue createHandle(ItemStack stack) {
        return stack == null || stack.getItem() == null ? LuaValue.NIL : new ItemEventReference(stack.getItem());
    }

    public static Item add(LuaValue definition) {
        Item item;
        if ("fluid_bucket".equals(definition.get("type").optjstring(""))) {
            item = FluidBucketRegistration.register(definition);
        } else {
            item = ItemRegistration.register(new ItemDeclaration(definition));
        }
        LuaValue key = definition.get("key");
        LuaContentRegistry.recordDeclaredKey("item", item.shiftedIndex, key.isstring() ? key.tojstring() : null);
        ResourceApi.invalidateNameIndexes();
        return item;
    }
}
