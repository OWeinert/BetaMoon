package betamoon.luaapi.item;

import net.minecraft.src.Item;

/** Exposes attack damage only on wrappers that support it. */
@FunctionalInterface
public interface ConfigurableAttackDamage {
    /** Updates the setting and returns the item for chaining. */
    Item setDamageValue(int damage);
}
