package betamoon.luaapi.item;

import betamoon.BetaMoonMain;
import betamoon.wrappers.ItemFoodWrapper;
import betamoon.resources.EnumTexAtlas;
import betamoon.luaapi.LuaApiUtils;
import betamoon.wrappers.ItemWrapper;
import betamoon.luamodloader.LuaContentRegistry;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ModLoader;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class ItemApi {
    private static final java.util.logging.Logger LOGGER = BetaMoonMain.LOGGER;
    /**
     * Utility class that installs item-related Lua bindings.
     */
    private ItemApi() {
    }
    
    public static void attach(LuaTable module) {
        module.set("createItem", new CreateItem());
    }

    public static LuaValue createHandle(ItemStack stack) {
        if (stack == null) {
            return LuaValue.NIL;
        }
        Item item = stack.getItem();
        if (item == null) {
            return LuaValue.NIL;
        }
        return new ReadOnlyItemHandle(item);
    }

    private static final class CreateItem extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            int base = (args.narg() >= 2 && args.arg(1).istable()) ? 2 : 1;
            int shiftedId = args.checkint(base);
            if (shiftedId < 256) {
                throw new LuaError("Item: id must be a shifted id (>= 256): " + shiftedId);
            }
            if (shiftedId >= Item.itemsList.length) {
                throw new LuaError("Item: id out of range: " + shiftedId);
            }
            String name = args.checkjstring(base + 1);
            int id = shiftedId - 256;
            LuaContentRegistry.Entry existing = LuaContentRegistry.find("item", shiftedId);
            if (existing != null) {
                if (!(existing.value instanceof Item)) throw new LuaError("Item: incompatible existing id: " + shiftedId);
                return new ItemHandle((Item) existing.value, existing);
            }
            try {
                ItemWrapper item = new ItemWrapper(id, name);
                item.setIconCoord(0, 0);
                LuaContentRegistry.Entry entry = LuaContentRegistry.remember("item", shiftedId, item, "item");
                return new ItemHandle(item, entry);
            } catch (RuntimeException e) {
                throw new LuaError("Item: " + String.valueOf(e.getMessage()));
            }
        }
    }

    private static final class ItemHandle extends LuaTable {
        private Item item;
        private final LuaContentRegistry.Entry contentEntry;
        private boolean foodItem;
        private int foodHeal;
        private boolean foodWolf;
        private boolean registered;

        private ItemHandle(Item item, LuaContentRegistry.Entry contentEntry) {
            this.item = item;
            this.contentEntry = contentEntry;
            this.registered = contentEntry.registered;
            this.foodItem = false;
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

        private boolean canMutate(String action) {
            return true;
        }
    }

    private static final class SetMaxStackSize extends VarArgFunction {
        private final ItemHandle handle;

        private SetMaxStackSize(ItemHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("setMaxStackSize")) {
                return handle;
            }
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
            if (!handle.canMutate("setMaxDamage")) {
                return handle;
            }
            int value = (int) LuaApiUtils.getNumberArg(args, 1);
            if (handle.item instanceof ItemWrapper) {
                ((ItemWrapper) handle.item).setMaxDamageValue(value);
            } else if (handle.item instanceof ItemFoodWrapper) {
                ((ItemFoodWrapper) handle.item).setMaxDamageValue(value);
            }
            return handle;
        }
    }

    private static final class SetHasSubtypes extends VarArgFunction {
        private final ItemHandle handle;

        private SetHasSubtypes(ItemHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("setHasSubtypes")) {
                return handle;
            }
            boolean value = args.arg(1).toboolean();
            if (handle.item instanceof ItemWrapper) {
                ((ItemWrapper) handle.item).setHasSubtypesValue(value);
            } else if (handle.item instanceof ItemFoodWrapper) {
                ((ItemFoodWrapper) handle.item).setHasSubtypesValue(value);
            }
            return handle;
        }
    }

    private static final class SetFull3D extends VarArgFunction {
        private final ItemHandle handle;

        private SetFull3D(ItemHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("setFull3D")) {
                return handle;
            }
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
            if (!handle.canMutate("setIconCoord")) {
                return handle;
            }
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
            if (!handle.canMutate("addTexture")) {
                return handle;
            }
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
            if (!handle.canMutate("setFood")) {
                return handle;
            }
            int base = (args.narg() >= 1 && args.arg(1).istable()) ? 2 : 1;
            int healAmount = args.checkint(base);
            boolean wolfFood = false;
            if (args.narg() >= base + 1 && !args.arg(base + 1).isnil()) {
                wolfFood = args.arg(base + 1).toboolean();
            }
            if (handle.registered && !(handle.item instanceof ItemFoodWrapper)) {
                throw new LuaError("Item: changing a registered normal item into food requires a restart.");
            }
            handle.foodItem = true;
            handle.foodHeal = healAmount;
            handle.foodWolf = wolfFood;
            handle.item.setMaxStackSize(1);
            if (handle.item instanceof ItemFoodWrapper) {
                ((ItemFoodWrapper) handle.item).setFoodValues(healAmount, wolfFood);
            }
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
            if (handle.registered && "food".equals(handle.contentEntry.kind) && !handle.foodItem) {
                throw new LuaError("Item: changing registered food into a normal item requires a restart.");
            }
            if (!handle.registered && handle.foodItem) {
                if (!"item".equals(handle.contentEntry.kind)) {
                    throw new LuaError("Item: incompatible hot-reload type for id " + handle.item.shiftedIndex);
                }
                int id = handle.item.shiftedIndex - 256;
                Item.itemsList[handle.item.shiftedIndex] = null;
                ItemFoodWrapper food = new ItemFoodWrapper(id, handle.foodHeal, handle.foodWolf);
                food.applyFrom((ItemWrapper) handle.item);
                itemToRegister = food;
                handle.item = food;
                LuaContentRegistry.replace(handle.contentEntry, food, "food");
            }
            if (args.narg() >= 1 && !args.arg(1).isnil()) {
                String displayName = LuaApiUtils.getStringArg(args, 1);
                ModLoader.AddName(itemToRegister, displayName);
            }
            handle.registered = true;
            handle.contentEntry.registered = true;
            return handle;
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

    private static final class ReadOnlyItemHandle extends LuaTable {
        private final Item item;

        private ReadOnlyItemHandle(Item item) {
            this.item = item;
            set("getId", new GetIdReadOnly(this));
        }
    }

    private static final class GetIdReadOnly extends VarArgFunction {
        private final ReadOnlyItemHandle handle;

        private GetIdReadOnly(ReadOnlyItemHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(handle.item.shiftedIndex);
        }
    }
}
