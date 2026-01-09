package betamoon.luaapi;

import betamoon.wrappers.ItemFoodWrapper;
import betamoon.wrappers.ItemWrapper;
import net.minecraft.src.Item;
import net.minecraft.src.ModLoader;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

final class ItemApi {
    /**
     * Utility class that installs item-related Lua bindings.
     */
    private ItemApi() {
    }
    
    static void attach(LuaTable module) {
        module.set("createItem", new CreateItem());
    }

    private static final class CreateItem extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            int base = (args.narg() >= 1 && args.arg(1).istable()) ? 2 : 1;
            int id = args.checkint(base);
            try {
                ItemWrapper item = new ItemWrapper(id);
                item.setIconCoord(0, 0);
                return new ItemHandle(item);
            } catch (RuntimeException e) {
                throw new LuaError(e);
            }
        }
    }

    private static final class ItemHandle extends LuaTable {
        private final ItemWrapper item;
        private boolean foodItem;
        private int foodHeal;
        private boolean foodWolf;

        private ItemHandle(ItemWrapper item) {
            this.item = item;
            set("setItemName", new SetItemName(this));
            set("setMaxStackSize", new SetMaxStackSize(this));
            set("setMaxDamage", new SetMaxDamage(this));
            set("setHasSubtypes", new SetHasSubtypes(this));
            set("setFull3D", new SetFull3D(this));
            set("setIconCoord", new SetIconCoord(this));
            set("addTexture", new AddTexture(this));
            set("setFood", new SetFood(this));
            set("register", new RegisterItem(this));
            set("getId", new GetId(this));
        }
    }

    private static final class SetItemName extends VarArgFunction {
        private final ItemHandle handle;

        private SetItemName(ItemHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            String name = LuaApiUtils.getStringArg(args, 1);
            handle.item.setItemName(name);
            return handle;
        }
    }

    private static final class SetMaxStackSize extends VarArgFunction {
        private final ItemHandle handle;

        private SetMaxStackSize(ItemHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int value = (int) LuaApiUtils.getNumberArg(args, 1);
            handle.item.setMaxStackSize(value);
            return handle;
        }
    }

    private static final class SetMaxDamage extends VarArgFunction {
        private final ItemHandle handle;

        private SetMaxDamage(ItemHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int value = (int) LuaApiUtils.getNumberArg(args, 1);
            handle.item.setMaxDamageValue(value);
            return handle;
        }
    }

    private static final class SetHasSubtypes extends VarArgFunction {
        private final ItemHandle handle;

        private SetHasSubtypes(ItemHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            boolean value = args.arg(1).toboolean();
            handle.item.setHasSubtypesValue(value);
            return handle;
        }
    }

    private static final class SetFull3D extends VarArgFunction {
        private final ItemHandle handle;

        private SetFull3D(ItemHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.item.setFull3D();
            return handle;
        }
    }

    private static final class SetIconCoord extends VarArgFunction {
        private final ItemHandle handle;

        private SetIconCoord(ItemHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int base = (args.narg() >= 2 && args.arg(1).istable()) ? 2 : 1;
            int iconX = args.checkint(base);
            int iconY = args.checkint(base + 1);
            handle.item.setIconCoord(iconX, iconY);
            return handle;
        }
    }

    private static final class AddTexture extends VarArgFunction {
        private final ItemHandle handle;

        private AddTexture(ItemHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            String relativePath = LuaApiUtils.getStringArg(args, 1);
            int textureIndex = LuaApiUtils.registerTexture(EnumTexAtlas.ITEMS, relativePath);
            handle.item.setIconIndex(textureIndex);
            return handle;
        }
    }

    private static final class SetFood extends VarArgFunction {
        private final ItemHandle handle;

        private SetFood(ItemHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int base = (args.narg() >= 1 && args.arg(1).istable()) ? 2 : 1;
            int healAmount = args.checkint(base);
            boolean wolfFood = false;
            if (args.narg() >= base + 1 && !args.arg(base + 1).isnil()) {
                wolfFood = args.arg(base + 1).toboolean();
            }
            handle.foodItem = true;
            handle.foodHeal = healAmount;
            handle.foodWolf = wolfFood;
            return handle;
        }
    }

    private static final class RegisterItem extends VarArgFunction {
        private final ItemHandle handle;

        private RegisterItem(ItemHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            Item itemToRegister = handle.item;
            if (handle.foodItem) {
                int id = handle.item.shiftedIndex - 256;
                Item.itemsList[handle.item.shiftedIndex] = null;
                ItemFoodWrapper food = new ItemFoodWrapper(id, handle.foodHeal, handle.foodWolf);
                food.applyFrom(handle.item);
                itemToRegister = food;
            }
            if (args.narg() >= 1 && !args.arg(1).isnil()) {
                String displayName = LuaApiUtils.getStringArg(args, 1);
                ModLoader.AddName(itemToRegister, displayName);
            }
            return LuaValue.NIL;
        }
    }

    private static final class GetId extends VarArgFunction {
        private final ItemHandle handle;

        private GetId(ItemHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(handle.item.shiftedIndex);
        }
    }

}
