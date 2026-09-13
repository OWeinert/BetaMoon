package betamoon.luaapi.item;

import net.minecraft.src.Item;

/**
 * Exposes subtype configuration independently of the Minecraft item superclass.
 */
@FunctionalInterface
public interface ConfigurableItemSubtypes {
    /** Updates the setting and returns the item for chaining. */
    Item setHasSubtypesValue(boolean hasSubtypes);
}
