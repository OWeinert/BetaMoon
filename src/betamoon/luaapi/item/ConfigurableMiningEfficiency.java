package betamoon.luaapi.item;

import net.minecraft.src.Item;

/** Exposes mining efficiency only on wrappers that support it. */
@FunctionalInterface
public interface ConfigurableMiningEfficiency {
    /** Updates the setting and returns the item for chaining. */
    Item setEfficiencyValue(float efficiency);
}
