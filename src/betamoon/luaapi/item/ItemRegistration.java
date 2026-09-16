package betamoon.luaapi.item;

import betamoon.BetaMoonMain;
import betamoon.client.render.ModelAppearanceSet;
import betamoon.client.render.ModelRenderingSupport;
import java.io.IOException;
import betamoon.luaapi.LuaApiUtils;
import betamoon.luamodloader.LuaContentRegistry;
import betamoon.resources.EnumTexAtlas;
import betamoon.wrappers.ItemArmorWrapper;
import betamoon.wrappers.ItemFoodWrapper;
import betamoon.wrappers.ItemWrapper;
import net.minecraft.src.Item;
import net.minecraft.src.ModLoader;
import org.luaj.vm2.LuaError;

/**
 * Applies parsed item properties and records registration without Lua dispatch.
 */
final class ItemRegistration {
    private ItemRegistration() {
    }

    static Item register(ItemDeclaration definition) {
        if (definition.appearance != null || definition.callbacks.visual.hasModels()) {
            ModelRenderingSupport.requireAvailable();
        }
        ModelAppearanceSet appearance;
        try {
            appearance = (definition.appearance != null || definition.callbacks.visual.hasModels())
                    ? new ModelAppearanceSet(definition.appearance, definition.callbacks.visual.appearances())
                    : null;
        } catch (IOException error) {
            throw new LuaError("Item appearance: " + error.getMessage());
        }
        try {
            return registerPrepared(definition, appearance);
        } catch (RuntimeException error) {
            if (appearance != null) {
                appearance.close();
            }
            throw error;
        }
    }

    private static Item registerPrepared(ItemDeclaration definition, ModelAppearanceSet appearance) {
        Item item = createOrRetain(definition);
        LuaContentRegistry.Entry entry = LuaContentRegistry.find("item", definition.id);
        prepareFood(item, entry, definition);
        applyProperties(item, definition);
        if (definition.armor != null) {
            ItemArmorApi.applyModel((ItemArmorWrapper) item, definition.armor);
        }
        if (definition.iconX != null) {
            item.setIconCoord(definition.iconX, definition.iconY);
        }
        if (definition.texture != null) {
            item.setIconIndex(LuaApiUtils.registerTexture(EnumTexAtlas.ITEMS, definition.texture));
        }
        item = finishFood(item, entry, definition);
        if (entry.registered && (definition.kind.isTool() || definition.kind == ItemKind.ARMOR)) {
            String label = definition.kind.isTool() ? "tool" : "armor";
            BetaMoonMain.LOGGER.warning("Ignored duplicate " + label + " register: id=" + definition.id);
        } else {
            ModLoader.AddName(item, definition.displayName);
            entry.registered = true;
        }
        ItemModelRegistry.install(definition.id, appearance);
        ItemCallbackRegistry.install(definition.id, definition.callbacks);
        return item;
    }

    private static Item createOrRetain(ItemDeclaration definition) {
        if (definition.kind.isTool()) {
            return ItemToolApi.createOrRetain(definition);
        }
        if (definition.kind == ItemKind.ARMOR) {
            return ItemArmorApi.createOrRetain(definition);
        }
        LuaContentRegistry.Entry existing = LuaContentRegistry.find("item", definition.id);
        if (existing != null) {
            if (!(existing.value instanceof Item)) {
                throw new LuaError("Item: incompatible existing id: " + definition.id);
            }
            return (Item) existing.value;
        }
        try {
            ItemWrapper item = new ItemWrapper(definition.id - 256, definition.name);
            item.setIconCoord(0, 0);
            LuaContentRegistry.remember("item", definition.id, item, "item");
            return item;
        } catch (RuntimeException exception) {
            throw new LuaError("Item: " + String.valueOf(exception.getMessage()));
        }
    }

    private static void prepareFood(Item item, LuaContentRegistry.Entry entry, ItemDeclaration definition) {
        if (definition.kind != ItemKind.FOOD) {
            if (entry.registered && "food".equals(entry.kind)) {
                throw new LuaError("Item: changing registered food into a normal item requires a restart.");
            }
            return;
        }
        if (entry.registered && !(item instanceof ItemFoodWrapper)) {
            throw new LuaError("Item: changing a registered normal item into food requires a restart.");
        }
        if (!entry.registered && !"item".equals(entry.kind)) {
            throw new LuaError("Item: incompatible hot-reload type for id " + definition.id);
        }
        item.setMaxStackSize(1);
        if (item instanceof ItemFoodWrapper) {
            ((ItemFoodWrapper) item).setFoodValues(definition.healing, definition.wolfFood);
        }
    }

    private static Item finishFood(Item item, LuaContentRegistry.Entry entry, ItemDeclaration definition) {
        if (entry.registered || definition.kind != ItemKind.FOOD) {
            return item;
        }
        Item.itemsList[definition.id] = null;
        ItemFoodWrapper food = new ItemFoodWrapper(definition.id - 256, definition.healing, definition.wolfFood);
        food.applyFrom((ItemWrapper) item);
        LuaContentRegistry.replace(entry, food, "food");
        return food;
    }

    private static void applyProperties(Item item, ItemDeclaration definition) {
        if (definition.maxStackSize != null) {
            item.setMaxStackSize(definition.maxStackSize);
        }
        boolean basicProperties = item instanceof ItemWrapper || item instanceof ItemFoodWrapper;
        if (definition.maxDamage != null && (definition.kind.isTool() || basicProperties)) {
            ((ConfigurableItemDurability) item).setMaxDamageValue(definition.maxDamage);
        }
        if (definition.hasSubtypes != null && basicProperties) {
            ((ConfigurableItemSubtypes) item).setHasSubtypesValue(definition.hasSubtypes);
        }
        if (definition.efficiency != null) {
            ((ConfigurableMiningEfficiency) item).setEfficiencyValue(definition.efficiency);
        }
        if (definition.damage != null) {
            ((ConfigurableAttackDamage) item).setDamageValue(definition.damage);
        }
        if (definition.full3D) {
            item.setFull3D();
        }
    }
}
