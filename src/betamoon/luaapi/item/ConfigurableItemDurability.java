package betamoon.luaapi.item;

import net.minecraft.src.Item;

/** Exposes the maximum damage setting of a configurable item. */
@FunctionalInterface
public interface ConfigurableItemDurability {
    /** Updates the setting and returns the item for chaining. */
    Item setMaxDamageValue(int maxDamage);
}
