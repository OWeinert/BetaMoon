package betamoon.entity;

import net.minecraft.src.Item;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.id;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;

/** Immutable native item-stack pickup policy. */
public final class PickupDefinition {
    public final int itemId;
    public final int count;
    public final int damage;
    public final int delayTicks;

    public PickupDefinition(LuaValue value) {
        fields(value, "entity.pickup", "item", "count", "damage", "delayTicks");
        itemId = id(value.get("item"), "entity.pickup.item");
        if (itemId == 0 || itemId >= Item.itemsList.length || Item.itemsList[itemId] == null) {
            throw new IllegalArgumentException("entity.pickup.item must reference a registered item");
        }
        count = value.get("count").isnil() ? 1
                : integer(value.get("count"), "entity.pickup.count", 1, 64);
        damage = value.get("damage").isnil() ? 0
                : integer(value.get("damage"), "entity.pickup.damage", 0, 32767);
        delayTicks = value.get("delayTicks").isnil() ? 0
                : integer(value.get("delayTicks"), "entity.pickup.delayTicks", 0, 1200);
    }
}
