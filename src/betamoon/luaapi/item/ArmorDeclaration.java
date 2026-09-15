package betamoon.luaapi.item;

import betamoon.client.assets.AssetLocation;
import betamoon.luaapi.asset.AssetInputs;

import betamoon.luaapi.material.ArmorMaterialApi.ArmorMaterial;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/** Armor-specific material, slot and model settings, read before allocation. */
final class ArmorDeclaration {
    final int material;
    final int initialRenderIndex;
    final int slot;
    final AssetLocation modelTexture;
    final Integer renderIndex;

    ArmorDeclaration(LuaValue definition) {
        LuaValue value = required(definition, "material");
        if (value.isuserdata() && value.touserdata() instanceof ArmorMaterial) {
            ArmorMaterial custom = (ArmorMaterial) value.touserdata();
            material = custom.level;
            initialRenderIndex = custom.renderIndex;
        } else {
            material = resolveArmorMaterial(value);
            initialRenderIndex = 0;
        }
        slot = resolveArmorType(required(definition, "slot"));
        LuaValue texture = definition.get("modelTexture");
        LuaValue render = definition.get("renderIndex");
        if (!texture.isnil() && !render.isnil()) {
            throw new LuaError("Armor definition cannot use modelTexture and renderIndex together.");
        }
        modelTexture = texture.isnil() ? null : AssetInputs.texture(texture);
        renderIndex = render.isnil() ? null : Integer.valueOf(resolveVanillaRenderIndex(render));
    }

    private static int resolveArmorType(LuaValue value) {
        // Only string tokens are accepted for armor slots.
        if (!value.isstring()) {
            throw new LuaError("Armor: type must be a string.");
        }
        String name = value.checkjstring().toLowerCase();
        // Normalize common names to the slot indices used by ItemArmor.
        if (name.equals("helmet") || name.equals("head")) {
            return 0;
        }
        if (name.equals("chestplate") || name.equals("chest")) {
            return 1;
        }
        if (name.equals("leggings") || name.equals("legs")) {
            return 2;
        }
        if (name.equals("boots") || name.equals("feet")) {
            return 3;
        }
        throw new LuaError("Armor: unknown armor type: " + name);
    }

    /**
     * Converts a Lua value to a vanilla armor render index (0-4).
     *
     * @param value
     *            number or material name from Lua
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
            if (name.equals("leather") || name.equals("cloth")) {
                return 0;
            }
            if (name.equals("chain") || name.equals("chainmail")) {
                return 1;
            }
            if (name.equals("iron")) {
                return 2;
            }
            if (name.equals("diamond") || name.equals("emerald")) {
                return 3;
            }
            if (name.equals("gold") || name.equals("golden")) {
                return 4;
            }
            throw new LuaError("Armor: unknown vanilla armor material: " + name);
        }
        throw new LuaError("Armor: vanilla render index must be a number or string.");
    }

    /**
     * Converts a Lua value to the armor material index.
     *
     * @param value
     *            number or material name from Lua
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
            if (name.equals("leather") || name.equals("cloth")) {
                return 0;
            }
            if (name.equals("chain") || name.equals("chainmail")) {
                return 1;
            }
            if (name.equals("iron")) {
                return 2;
            }
            if (name.equals("diamond") || name.equals("emerald")) {
                return 3;
            }
            if (name.equals("gold") || name.equals("golden")) {
                return 4;
            }
            throw new LuaError("Armor: unknown armor material: " + name);
        }
        throw new LuaError("Armor: material must be a number or string.");
    }
}
