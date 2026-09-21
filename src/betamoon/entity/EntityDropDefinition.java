package betamoon.entity;

import betamoon.assets.AssetKey;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.Entity;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.id;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.length;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;

/** Immutable item-drop choices sampled whenever a typed entity emits its configured loot. */
public final class EntityDropDefinition {
    private final List<Entry> entries = new ArrayList<>();

    public EntityDropDefinition(LuaValue value) {
        if (value.isnil()) {
            return;
        }
        int count = length(value, "entity.drops");
        for (int index = 1; index <= count; index++) {
            LuaValue entry = value.get(index);
            String path = "entity.drops[" + index + "]";
            fields(entry, path, "item", "min", "max", "chance", "damage");
            int item = id(entry.get("item"), path + ".item");
            int minimum = entry.get("min").isnil() ? 1 : integer(entry.get("min"), path + ".min", 0, 64);
            int maximum = entry.get("max").isnil()
                    ? minimum : integer(entry.get("max"), path + ".max", minimum, 64);
            double chance = entry.get("chance").isnil() ? 1 : number(entry.get("chance"), path + ".chance");
            if (!Double.isFinite(chance) || chance < 0 || chance > 1) {
                throw error(path + ".chance", "expected 0..1");
            }
            int damage = entry.get("damage").isnil()
                    ? 0 : integer(entry.get("damage"), path + ".damage", 0, 32767);
            entries.add(new Entry(item, minimum, maximum, damage, chance));
        }
    }

    public void drop(Entity entity) {
        if (entity.worldObj == null || entity.worldObj.multiplayerWorld) {
            return;
        }
        for (Entry entry : entries) {
            if (entity.worldObj.rand.nextDouble() >= entry.chance) {
                continue;
            }
            int count = entry.minimum + entity.worldObj.rand.nextInt(entry.maximum - entry.minimum + 1);
            if (count > 0 && entry.item > 0 && entry.item < Item.itemsList.length
                    && Item.itemsList[entry.item] != null) {
                entity.entityDropItem(new ItemStack(entry.item, count, entry.damage), entity.height * 0.5F);
            }
        }
    }

    public void validateRegistered(AssetKey key, List<String> errors) {
        for (Entry entry : entries) {
            if (entry.item <= 0 || entry.item >= Item.itemsList.length || Item.itemsList[entry.item] == null) {
                errors.add("Entity drop item not registered (" + key + "): " + entry.item);
            }
        }
    }

    private static final class Entry {
        private final int item;
        private final int minimum;
        private final int maximum;
        private final int damage;
        private final double chance;

        private Entry(int item, int minimum, int maximum, int damage, double chance) {
            this.item = item;
            this.minimum = minimum;
            this.maximum = maximum;
            this.damage = damage;
            this.chance = chance;
        }
    }
}
