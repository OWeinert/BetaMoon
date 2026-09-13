package betamoon.luaapi.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.Block;
import net.minecraft.src.ItemStack;
import net.minecraft.src.World;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Captures the original block-drop stacks before spawning, so getDrops can inspect or replace them. */
public final class BlockDropOverrideCapture {
    private static final ThreadLocal<Collection> CURRENT = new ThreadLocal<Collection>();

    private BlockDropOverrideCapture() {
    }

    public static LuaValue original(Block block, World world, int x, int y, int z, int metadata) {
        Collection previous = CURRENT.get();
        Collection collection = new Collection(world, x, y, z);
        CURRENT.set(collection);
        try {
            block.dropBlockAsItemWithChance(world, x, y, z, metadata, 1.0F);
            LuaTable result = new LuaTable();
            for (ItemStack stack : collection.stacks) {
                LuaTable entry = new LuaTable();
                entry.set("item", stack.itemID);
                entry.set("damage", stack.getItemDamage());
                entry.set("min", stack.stackSize);
                entry.set("max", stack.stackSize);
                result.insert(0, entry);
            }
            return result;
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static int capture(World world, int x, int y, int z, ItemStack stack) {
        Collection collection = CURRENT.get();
        if (collection == null || collection.world != world || collection.x != x
                || collection.y != y || collection.z != z) {
            return 0;
        }
        collection.stacks.add(stack.copy());
        return 1;
    }

    public static void complete() {
    }

    private static final class Collection {
        private final World world;
        private final int x;
        private final int y;
        private final int z;
        private final List<ItemStack> stacks = new ArrayList<ItemStack>();

        private Collection(World world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
