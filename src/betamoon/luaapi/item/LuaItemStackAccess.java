package betamoon.luaapi.item;

import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;

/** Narrow live access for a single callback invocation. */
public final class LuaItemStackAccess {
    private LuaItemStackAccess() {
    }

    public static LuaTable create(LuaCallbackScope scope, EntityPlayer player, final ItemStack stack) {

        final LuaTable api = new LuaTable();
        api.set("id", stack.itemID);
        api.set("getCount", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                return valueOf(stack.stackSize);
            }
        });
        api.set("getDamage", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                return valueOf(stack.getItemDamage());
            }
        });
        api.set("consume", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                int count = integer(argument(a, api, 1), "consume", 1, 32767);
                if (stack.stackSize < count) {
                    return FALSE;
                }
                stack.stackSize -= count;
                return TRUE;
            }
        });
        api.set("damage", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                if (player == null) {
                    throw LuaDeclarationValues.error("stack.damage", "requires a player");
                }
                stack.damageItem(integer(argument(a, api, 1), "damage", 0, 32767), player);
                return NIL;
            }
        });
        return api;

    }

    private static LuaValue argument(Varargs args, LuaValue receiver, int index) {
        return args.arg(index + (args.arg1() == receiver ? 1 : 0));
    }

    private static int coordinate(LuaValue value) {
        return integer(value, "coordinate", -30000000, 30000000);
    }
}
