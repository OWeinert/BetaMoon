package betamoon.luaapi.item;

import net.minecraft.src.Item;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** The existing read-only item reference available inside item-use events. */
final class ItemEventReference extends LuaTable {
    ItemEventReference(final Item item) {
        set("getId", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                return LuaValue.valueOf(item.shiftedIndex);
            }
        });
    }
}
