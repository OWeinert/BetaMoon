package betamoon.luaapi.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.id;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.length;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;

/** Declarative drop entries, sampled once when the engine requests drops. */
public final class BlockDropDefinition {
    public final boolean declared;
    private final List<Entry> entries = new ArrayList<Entry>();

    public BlockDropDefinition(LuaValue definition) {
        declared = !definition.isnil();
        if (!declared) {
            return;
        }
        int count = length(definition, "drops");
        for (int index = 1; index <= count; index++) {
            LuaValue entry = definition.get(index);
            String path = "drops[" + index + "]";
            fields(entry, path, "item", "min", "max", "chance", "damage");
            int item = id(entry.get("item"), path + ".item");
            int minimum = entry.get("min").isnil() ? 1 : integer(entry.get("min"), path + ".min", 0, 64);
            int maximum = entry.get("max").isnil() ? minimum : integer(entry.get("max"), path + ".max", minimum, 64);
            double chance = entry.get("chance").isnil() ? 1 : number(entry.get("chance"), path + ".chance");
            if (chance < 0 || chance > 1) {
                throw error(path + ".chance", "expected 0..1");
            }
            int damage = entry.get("damage").isnil() ? 0 : integer(entry.get("damage"), path + ".damage", 0, 32767);
            entries.add(new Entry(item, minimum, maximum, damage, chance));
        }
    }

    public List<ItemStack> sample(Random random, float engineChance) {
        List<ItemStack> drops = new ArrayList<ItemStack>();
        for (Entry entry : entries) {
            if (random.nextDouble() >= entry.chance * engineChance) {
                continue;
            }
            int count = entry.minimum + random.nextInt(entry.maximum - entry.minimum + 1);
            if (count > 0 && entry.item < Item.itemsList.length && Item.itemsList[entry.item] != null) {
                drops.add(new ItemStack(entry.item, count, entry.damage));
            }
        }
        return drops;
    }

    public void validateRegistered() {
        for (Entry entry : entries) {
            if (entry.item <= 0 || entry.item >= Item.itemsList.length || Item.itemsList[entry.item] == null) {
                throw error("getDrops", "unknown item ID " + entry.item);
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
