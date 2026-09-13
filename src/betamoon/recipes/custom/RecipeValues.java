package betamoon.recipes.custom;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Exact-type parsing and detached values shared by recipe schemas and matching.
 */
public final class RecipeValues {
    private RecipeValues() {
    }

    public static LuaError error(String path, String message) {
        return new LuaError(path + ": " + message);
    }

    public static LuaValue table(LuaValue value, String path) {
        if (!value.istable()) {
            throw error(path, "expected a table");
        }
        return value;
    }

    public static String string(LuaValue value, String path) {
        if (value.type() != LuaValue.TSTRING) {
            throw error(path, "expected a string");
        }
        return value.tojstring();
    }

    public static int integer(LuaValue value, String path) {
        double n = number(value, path);
        if (n != Math.floor(n) || n < Integer.MIN_VALUE || n > Integer.MAX_VALUE) {
            throw error(path, "expected a 32-bit integer");
        }
        return (int) n;
    }

    public static double number(LuaValue value, String path) {
        if (value.type() != LuaValue.TNUMBER) {
            throw error(path, "expected a number");
        }
        double n = value.todouble();
        if (Double.isNaN(n) || Double.isInfinite(n)) {
            throw error(path, "expected a finite number");
        }
        return n;
    }

    public static boolean bool(LuaValue value, boolean fallback, String path) {
        if (value.isnil()) {
            return fallback;
        }
        if (!value.isboolean()) {
            throw error(path, "expected a boolean");
        }
        return value.toboolean();
    }

    public static List<String> keys(LuaValue value, String path) {
        table(value, path);
        List<String> keys = new ArrayList<String>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            key = value.next(key).arg1();
            if (key.isnil()) {
                break;
            }
            keys.add(string(key, path + " key"));
        }
        Collections.sort(keys);
        return keys;
    }

    public static void fields(LuaValue value, String path, String... allowed) {
        for (String key : keys(value, path)) {
            boolean found = false;
            for (String field : allowed) {
                if (field.equals(key)) {
                    found = true;
                }
            }
            if (!found) {
                throw error(path + "." + key, "unknown field (allowed: " + java.util.Arrays.toString(allowed) + ")");
            }
        }
    }

    public static LuaValue copy(LuaValue value) {
        return copy(value, 0);
    }

    private static LuaValue copy(LuaValue value, int depth) {
        if (depth > 24) {
            throw error("recipe value", "nested too deeply or cyclic");
        }
        if (!value.istable()) {
            return value;
        }
        LuaTable out = new LuaTable();
        LuaValue key = LuaValue.NIL;
        while (true) {
            org.luaj.vm2.Varargs pair = value.next(key);
            key = pair.arg1();
            if (key.isnil()) {
                return out;
            }
            out.set(key, copy(pair.arg(2), depth + 1));
        }
    }

    public static String canonical(LuaValue value) {
        if (!value.istable()) {
            return value.type() + ":" + value.tojstring().length() + ":" + value.tojstring();
        }
        List<String> entries = new ArrayList<String>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            org.luaj.vm2.Varargs pair = value.next(key);
            key = pair.arg1();
            if (key.isnil()) {
                break;
            }
            entries.add(canonical(key) + "=" + canonical(pair.arg(2)));
        }
        Collections.sort(entries);
        return "{" + entries.toString() + "}";
    }

    public static String fingerprint(LuaValue value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical(value).getBytes("UTF-8"));
            StringBuilder out = new StringBuilder("recipe-v1:");
            final char[] hex = "0123456789abcdef".toCharArray();
            for (byte b : digest) {
                out.append(hex[(b & 255) >>> 4]);
                out.append(hex[b & 15]);
            }
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static ItemStack stack(LuaValue value, boolean zero, String path) {
        LuaValue id = value;
        int count = 1;
        int damage = 0;
        if (value.istable()) {
            id = value.get("id");
            if (id.isnil()) {
                id = value.get(1);
            }
            LuaValue amount = value.get("count");
            if (amount.isnil()) {
                amount = value.get(2);
            }
            if (!amount.isnil()) {
                count = integer(amount, path + ".count");
            }
            LuaValue metadata = value.get("damage");
            if (metadata.isnil()) {
                metadata = value.get(3);
            }
            if (!metadata.isnil()) {
                damage = integer(metadata, path + ".damage");
            }
        }
        int itemId = integer(id, path + ".id");
        if (itemId < 0 || itemId >= Item.itemsList.length || Item.itemsList[itemId] == null) {
            throw error(path + ".id", "unknown item " + itemId);
        }
        if (count < (zero ? 0 : 1)) {
            throw error(path + ".count", "expected " + (zero ? "nonnegative" : "positive") + " integer");
        }
        if (damage < 0) {
            throw error(path + ".damage", "expected nonnegative integer");
        }
        return new ItemStack(itemId, count, damage);
    }

    public static LuaValue stack(ItemStack stack) {
        if (stack == null) {
            return LuaValue.NIL;
        }
        LuaTable out = new LuaTable();
        out.set("id", stack.itemID);
        out.set("count", stack.stackSize);
        out.set("damage", stack.getItemDamage());
        return out;
    }

    public static void fits(ItemStack stack, String path) {
        if (stack.stackSize > Math.min(64, stack.getMaxStackSize())) {
            throw error(path + ".count", "exceeds one stack's capacity");
        }
    }

    public static boolean same(ItemStack a, ItemStack b) {
        return a == b || a != null && b != null && a.isItemEqual(b) && a.stackSize == b.stackSize;
    }
}
