package betamoon.luaapi.event;

import betamoon.event.context.ItemUseEventCtx;
import betamoon.luaapi.item.ItemApi;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class LuaItemUseEventCtx extends LuaTable {
    private final ItemUseEventCtx context;

    public LuaItemUseEventCtx(ItemUseEventCtx context) {
        this.context = context;
        if (context != null && context.getItemStack() != null) {
            set("itemId", context.getItemStack().itemID);
            set("damage", context.getItemStack().getItemDamage());
            set("count", context.getItemStack().stackSize);
            set("item", ItemApi.createHandle(context.getItemStack()));
        }
    }

    private static final class GetItemId extends VarArgFunction {
        private final LuaItemUseEventCtx owner;

        private GetItemId(LuaItemUseEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null || owner.context.getItemStack() == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getItemStack().itemID);
        }
    }

    private static final class GetDamage extends VarArgFunction {
        private final LuaItemUseEventCtx owner;

        private GetDamage(LuaItemUseEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null || owner.context.getItemStack() == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getItemStack().getItemDamage());
        }
    }

    private static final class GetCount extends VarArgFunction {
        private final LuaItemUseEventCtx owner;

        private GetCount(LuaItemUseEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null || owner.context.getItemStack() == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getItemStack().stackSize);
        }
    }

    private static final class GetName extends VarArgFunction {
        private final LuaItemUseEventCtx owner;

        private GetName(LuaItemUseEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null || owner.context.getItemStack() == null) {
                return LuaValue.NIL;
            }
            net.minecraft.src.ItemStack stack = owner.context.getItemStack();
            net.minecraft.src.Item item = stack.getItem();
            if (item == null) {
                return LuaValue.NIL;
            }
            String key = item.getItemNameIS(stack);
            if (key == null) {
                key = item.getItemName();
            }
            if (key == null) {
                return LuaValue.NIL;
            }
            String name = net.minecraft.src.StatCollector.translateToLocal(key + ".name");
            if (name == null || "null.name".equals(name) || name.endsWith(".name")) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(name);
        }
    }

    private static final class GetHandle extends VarArgFunction {
        private final LuaItemUseEventCtx owner;

        private GetHandle(LuaItemUseEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null || owner.context.getItemStack() == null) {
                return LuaValue.NIL;
            }
            return ItemApi.createHandle(owner.context.getItemStack());
        }
    }
}
