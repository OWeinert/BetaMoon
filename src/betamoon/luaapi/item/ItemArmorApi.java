package betamoon.luaapi.item;

import betamoon.resources.EnumTexAtlas;
import betamoon.luaapi.material.ArmorMaterialApi;
import betamoon.luaapi.LuaApiUtils;
import betamoon.wrappers.ItemArmorWrapper;
import java.util.logging.Logger;
import net.minecraft.src.Item;
import net.minecraft.src.ModLoader;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class ItemArmorApi {
    private static final Logger LOGGER = Logger.getLogger("BetaMoon");
    /**
     * Utility class that installs armor-related Lua bindings.
     */
    private ItemArmorApi() {
    }

    public static void attach(LuaTable module) {
        module.set("createArmor", new CreateArmor());
    }

    private static final class CreateArmor extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            int base = (args.narg() >= 2 && args.arg(1).istable()) ? 2 : 1;
            int shiftedId = args.checkint(base);
            // Item ids from Lua are shifted (in-game id space).
            if (shiftedId < 256) {
                throw new LuaError("Armor: id must be a shifted id (>= 256): " + shiftedId);
            }
            if (shiftedId >= Item.itemsList.length) {
                throw new LuaError("Armor: id out of range: " + shiftedId);
            }
            LuaValue materialValue = args.arg(base + 1);
            int material;
            int renderIndex;
            // Accept either a custom material userdata or a raw material token.
            if (materialValue.isuserdata() && materialValue.touserdata() instanceof ArmorMaterialApi.ArmorMaterial) {
                ArmorMaterialApi.ArmorMaterial mat = (ArmorMaterialApi.ArmorMaterial) materialValue.touserdata();
                material = mat.level;
                renderIndex = mat.renderIndex;
            } else {
                // Default render index to 0 when using vanilla material identifiers.
                material = resolveArmorMaterial(materialValue);
                renderIndex = 0;
            }
            // Armor type is always the next argument after material.
            int armorType = resolveArmorType(args.arg(base + 2));
            String name = args.checkjstring(base + 3);
            ItemArmorWrapper armor = new ItemArmorWrapper(shiftedId - 256, material, renderIndex, armorType, name);
            return new ArmorHandle(armor);
        }
    }

    private static final class ArmorHandle extends LuaTable {
        private final ItemArmorWrapper armor;
        private boolean registered;

        private ArmorHandle(ItemArmorWrapper armor) {
            this.armor = armor;
            set("setFull3D", new SetFull3D(this));
            set("setIconCoord", new SetIconCoord(this));
            set("addTexture", new AddTexture(this));
            // TEMPORARILY DISABLED: setArmorTexture is disabled while armor texture loading
            // returns to the vanilla /armor/ lookup behavior.
            // set("setArmorTexture", new SetArmorTexture(this));
            set("setVanillaRenderIndex", new SetVanillaRenderIndex(this));
            set("register", new RegisterArmor(this));
            set("getId", new GetId(this));
        }

        private boolean canMutate(String action) {
            if (!registered) {
                return true;
            }
            LOGGER.warning("Ignored armor mutation after register: id=" + armor.shiftedIndex + " action=" + action);
            return false;
        }
    }

    private static final class SetFull3D extends VarArgFunction {
        private final ArmorHandle handle;

        private SetFull3D(ArmorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("setFull3D")) {
                return handle;
            }
            handle.armor.setFull3D();
            return handle;
        }
    }

    private static final class SetIconCoord extends VarArgFunction {
        private final ArmorHandle handle;

        private SetIconCoord(ArmorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("setIconCoord")) {
                return handle;
            }
            int base = (args.narg() >= 2 && args.arg(1).istable()) ? 2 : 1;
            int iconX = args.checkint(base);
            int iconY = args.checkint(base + 1);
            handle.armor.setIconCoord(iconX, iconY);
            return handle;
        }
    }

    private static final class AddTexture extends VarArgFunction {
        private final ArmorHandle handle;

        private AddTexture(ArmorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("addTexture")) {
                return handle;
            }
            String relativePath = LuaApiUtils.getStringArg(args, 1);
            int textureIndex = LuaApiUtils.registerTexture(EnumTexAtlas.ITEMS, relativePath);
            handle.armor.setIconIndex(textureIndex);
            return handle;
        }
    }

/*
    // TEMPORARILY DISABLED: setArmorTexture is disabled while armor textures
    // use the vanilla /armor/ lookup behavior.
    private static final class SetArmorTexture extends VarArgFunction {
        private final ArmorHandle handle;

        private SetArmorTexture(ArmorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("setArmorTexture")) {
                return handle;
            }
            String texture = LuaApiUtils.getStringArg(args, 1);
            handle.armor.setArmorTexture(texture);
            return handle;
        }
    }
*/

    private static final class SetVanillaRenderIndex extends VarArgFunction {
        private final ArmorHandle handle;

        private SetVanillaRenderIndex(ArmorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.canMutate("setVanillaRenderIndex")) {
                return handle;
            }
            LuaValue value = args.arg(1);
            if (value.istable()) {
                value = args.arg(2);
            }
            int index = resolveVanillaRenderIndex(value);
            handle.armor.setRenderIndex(index);
            return handle;
        }
    }

    private static final class RegisterArmor extends VarArgFunction {
        private final ArmorHandle handle;

        private RegisterArmor(ArmorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.registered) {
                LOGGER.warning("Ignored duplicate armor register: id=" + handle.armor.shiftedIndex);
                return handle;
            }
            if (args.narg() >= 1 && !args.arg(1).isnil()) {
                String displayName = LuaApiUtils.getStringArg(args, 1);
                ModLoader.AddName(handle.armor, displayName);
            }
            handle.registered = true;
            return handle;
        }
    }

    private static final class GetId extends VarArgFunction {
        private final ArmorHandle handle;

        private GetId(ArmorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(handle.armor.shiftedIndex);
        }
    }

    /**
     * Converts a Lua value to the armor slot index (helmet/chest/legs/boots).
     *
     * @param value string token from Lua
     * @return armor slot index 0-3
     */
    private static int resolveArmorType(LuaValue value) {
        // Only string tokens are accepted for armor slots.
        if (!value.isstring()) {
            throw new LuaError("Armor: type must be a string.");
        }
        String name = value.checkjstring().toLowerCase();
        // Normalize common names to the slot indices used by ItemArmor.
        if (name.equals("helmet") || name.equals("head")) return 0;
        if (name.equals("chestplate") || name.equals("chest")) return 1;
        if (name.equals("leggings") || name.equals("legs")) return 2;
        if (name.equals("boots") || name.equals("feet")) return 3;
        throw new LuaError("Armor: unknown armor type: " + name);
    }

    /**
     * Converts a Lua value to a vanilla armor render index (0-4).
     *
     * @param value number or material name from Lua
     * @return vanilla render index used by RenderPlayer
     */
    private static int resolveVanillaRenderIndex(LuaValue value) {
        // Numeric indices map directly to the vanilla render index list.
        if (value.isnumber()) {
            int index = value.checkint();
            if (index < 0 || index > 4) {
                throw new LuaError("Armor: vanilla render index must be between 0 and 4.");
            }
            return index;
        }
        if (value.isstring()) {
            String name = value.checkjstring().toLowerCase();
            // Map vanilla material names to their render index values.
            if (name.equals("leather") || name.equals("cloth")) return 0;
            if (name.equals("chain") || name.equals("chainmail")) return 1;
            if (name.equals("iron")) return 2;
            if (name.equals("diamond") || name.equals("emerald")) return 3;
            if (name.equals("gold") || name.equals("golden")) return 4;
            throw new LuaError("Armor: unknown vanilla armor material: " + name);
        }
        throw new LuaError("Armor: vanilla render index must be a number or string.");
    }

    /**
     * Converts a Lua value to the armor material index.
     *
     * @param value number or material name from Lua
     * @return armor material index
     */
    private static int resolveArmorMaterial(LuaValue value) {
        // Accept either numeric indices or canonical material names.
        if (value.isnumber()) {
            int material = value.checkint();
            if (material < 0) {
                throw new LuaError("Armor: material id must be 0 or higher.");
            }
            return material;
        }
        if (value.isstring()) {
            String name = value.checkjstring().toLowerCase();
            // Map known vanilla names to their material index.
            if (name.equals("leather") || name.equals("cloth")) return 0;
            if (name.equals("chain") || name.equals("chainmail")) return 1;
            if (name.equals("iron")) return 2;
            if (name.equals("diamond") || name.equals("emerald")) return 3;
            if (name.equals("gold") || name.equals("golden")) return 4;
            throw new LuaError("Armor: unknown armor material: " + name);
        }
        throw new LuaError("Armor: material must be a number or string.");
    }
}
