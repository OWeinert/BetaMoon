package betamoon.luaapi.item;

import betamoon.luamodloader.LuaContentRegistry;
import betamoon.resources.LuaTextureResources;
import betamoon.wrappers.ItemArmorWrapper;
import org.luaj.vm2.LuaError;

/** Native armor creation and model setup used by the declarative item API. */
final class ItemArmorApi {
    private ItemArmorApi() {
    }

    static ItemArmorWrapper createOrRetain(ItemDeclaration definition) {
        ArmorDeclaration armor = definition.armor;
        String signature = "armor:" + armor.material + ":" + armor.slot;
        LuaContentRegistry.Entry entry = LuaContentRegistry.find("item", definition.id);
        if (entry != null) {
            LuaContentRegistry.remember("item", definition.id, entry.value, signature);
            if (!(entry.value instanceof ItemArmorWrapper)) {
                throw new LuaError("Armor: changing the type of id " + definition.id + " requires a restart.");
            }
            return (ItemArmorWrapper) entry.value;
        }
        ItemArmorWrapper item = new ItemArmorWrapper(definition.id - 256, armor.material, armor.initialRenderIndex,
                armor.slot, definition.name);
        LuaContentRegistry.remember("item", definition.id, item, signature);
        return item;
    }

    static void applyModel(ItemArmorWrapper item, ArmorDeclaration definition) {
        if (definition.modelTexture != null) {
            item.setArmorTexture(LuaTextureResources.register(definition.modelTexture));
        } else {
            item.useVanillaArmorTexture();
        }
        if (definition.renderIndex != null) {
            item.setRenderIndex(definition.renderIndex);
        }
    }
}
