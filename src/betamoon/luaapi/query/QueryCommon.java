package betamoon.luaapi.query;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ShapedRecipes;
import net.minecraft.src.ShapelessRecipes;
import net.minecraft.src.StatCollector;
import org.luaj.vm2.LuaValue;

final class QueryCommon {
    private QueryCommon() {
    }

    static final class QueryEntry {
        final int id;
        final int damage;
        final String internalName;
        final String displayName;

        QueryEntry(int id, int damage, String internalName, String displayName) {
            this.id = id;
            this.damage = damage;
            this.internalName = internalName;
            this.displayName = displayName;
        }
    }

    static boolean matchesOutput(ItemStack recipeOutput, ItemStack target) {
        if (recipeOutput == null || target == null) {
            return false;
        }
        if (recipeOutput.itemID != target.itemID) {
            return false;
        }
        if (target.stackSize == 0) {
            return true;
        }
        return recipeOutput.stackSize == target.stackSize
            && recipeOutput.getItemDamage() == target.getItemDamage();
    }

    static boolean matchesStack(ItemStack stack, ItemStack target) {
        if (stack == null || target == null) {
            return false;
        }
        return stack.itemID == target.itemID && stack.getItemDamage() == target.getItemDamage();
    }

    static ItemStack normalizeIngredient(Object ingredient) {
        if (ingredient == null) {
            return null;
        }
        if (ingredient instanceof ItemStack) {
            return (ItemStack) ingredient;
        }
        if (ingredient instanceof Item) {
            return new ItemStack((Item) ingredient);
        }
        if (ingredient instanceof Block) {
            return new ItemStack((Block) ingredient);
        }
        return null;
    }

    static List getShapelessInputs(ShapelessRecipes recipe) {
        try {
            Field[] fields = ShapelessRecipes.class.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (List.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return (List) field.get(recipe);
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    static ItemStack[] getShapedInputs(ShapedRecipes recipe) {
        try {
            Field[] fields = ShapedRecipes.class.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (field.getType().isArray()
                    && ItemStack.class.equals(field.getType().getComponentType())) {
                    field.setAccessible(true);
                    return (ItemStack[]) field.get(recipe);
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    static int[] getShapedDimensions(ShapedRecipes recipe, int itemCount) {
        try {
            Field[] fields = ShapedRecipes.class.getDeclaredFields();
            int[] candidates = new int[3];
            int count = 0;
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (field.getType() == Integer.TYPE) {
                    field.setAccessible(true);
                    candidates[count++] = field.getInt(recipe);
                    if (count == candidates.length) {
                        break;
                    }
                }
            }
            for (int i = 0; i < count; i++) {
                for (int j = 0; j < count; j++) {
                    if (i == j) {
                        continue;
                    }
                    int width = candidates[i];
                    int height = candidates[j];
                    if (width > 0 && height > 0 && width * height == itemCount) {
                        return new int[] { width, height };
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    static int readDamageFromHandle(LuaValue handle) {
        if (handle.istable()) {
            LuaValue getter = handle.get("getDamage");
            if (!getter.isnil()) {
                return getter.call(handle).checkint();
            }
        }
        return 0;
    }

    static List buildBlockEntries() {
        List entries = new ArrayList();
        for (int id = 0; id < Block.blocksList.length && id <= 255; id++) {
            Block block = Block.blocksList[id];
            if (block == null) {
                continue;
            }
            addEntriesForId(entries, id);
        }
        return entries;
    }

    static List buildItemEntries() {
        List entries = new ArrayList();
        for (int id = 256; id < Item.itemsList.length; id++) {
            Item item = Item.itemsList[id];
            if (item == null) {
                continue;
            }
            addEntriesForId(entries, id);
        }
        return entries;
    }

    private static void addEntriesForId(List entries, int id) {
        String baseInternal = resolveInternalName(id, 0);
        String baseDisplay = resolveDisplayName(id, 0);
        entries.add(new QueryEntry(id, 0, baseInternal, baseDisplay));
        Item item = id >= 0 && id < Item.itemsList.length ? Item.itemsList[id] : null;
        if (item == null || !item.getHasSubtypes()) {
            return;
        }
        Set seen = new HashSet();
        if (baseInternal != null && baseDisplay != null) {
            seen.add(baseInternal + "|" + baseDisplay);
        }
        for (int damage = 1; damage <= 15; damage++) {
            try {
                String internal = resolveInternalName(id, damage);
                String display = resolveDisplayName(id, damage);
                if (internal == null || display == null) {
                    continue;
                }
                if ("unknown".equals(internal) || isUnknownDisplayName(display)) {
                    continue;
                }
                if (internal.equals(baseInternal) && display.equals(baseDisplay)) {
                    continue;
                }
                String signature = internal + "|" + display;
                if (seen.contains(signature)) {
                    continue;
                }
                seen.add(signature);
                entries.add(new QueryEntry(id, damage, internal, display));
            } catch (RuntimeException e) {
                if (e instanceof ArrayIndexOutOfBoundsException) {
                    break;
                }
            }
        }
    }

    private static String resolveInternalName(int id, int damage) {
        Item item = id >= 0 && id < Item.itemsList.length ? Item.itemsList[id] : null;
        ItemStack stack = null;
        if (item != null) {
            stack = new ItemStack(id, 1, damage);
            String name = stack == null ? item.getItemName() : item.getItemNameIS(stack);
            if (name != null) {
                return name;
            }
        }
        Block block = id >= 0 && id < Block.blocksList.length ? Block.blocksList[id] : null;
        if (block != null && block.getBlockName() != null) {
            return block.getBlockName();
        }
        return "unknown";
    }

    private static String resolveDisplayName(int id, int damage) {
        Item item = id >= 0 && id < Item.itemsList.length ? Item.itemsList[id] : null;
        ItemStack stack = null;
        if (item != null) {
            stack = new ItemStack(id, 1, damage);
            String key = stack == null ? item.getItemName() : item.getItemNameIS(stack);
            if (key != null) {
                return StatCollector.translateToLocal(key + ".name");
            }
        }
        Block block = id >= 0 && id < Block.blocksList.length ? Block.blocksList[id] : null;
        if (block != null) {
            return block.translateBlockName();
        }
        return "Unknown";
    }

    private static boolean isUnknownDisplayName(String value) {
        return value == null || "null.name".equals(value) || "Unknown".equals(value)
            || value.endsWith(".name");
    }

    static QueryEntry findById(List entries, int id, int damage) {
        for (int i = 0; i < entries.size(); i++) {
            QueryEntry entry = (QueryEntry) entries.get(i);
            if (entry.id == id && entry.damage == damage) {
                return entry;
            }
        }
        return null;
    }

    static QueryEntry findFirstById(List entries, int id) {
        for (int i = 0; i < entries.size(); i++) {
            QueryEntry entry = (QueryEntry) entries.get(i);
            if (entry.id == id) {
                return entry;
            }
        }
        return null;
    }
}
