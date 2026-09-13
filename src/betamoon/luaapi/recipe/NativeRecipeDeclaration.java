package betamoon.luaapi.recipe;

import betamoon.luaapi.LuaApiUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Validated native recipe data; construction does not register a recipe. */
public final class NativeRecipeDeclaration {
    public enum Kind {
        SHAPED, SHAPELESS, SMELTING
    }

    public final Kind kind;
    public final int inputId;
    private final ItemStack output;
    private final Object[] ingredients;

    private NativeRecipeDeclaration(Kind kind, int inputId, ItemStack output, Object[] ingredients) {
        this.kind = kind;
        this.inputId = inputId;
        this.output = output.copy();
        this.ingredients = copyIngredients(ingredients);
    }

    public ItemStack getOutput() {
        return output.copy();
    }

    public Object[] getIngredients() {
        return copyIngredients(ingredients);
    }

    private static Object[] copyIngredients(Object[] source) {
        Object[] result = source.clone();
        for (int i = 0; i < result.length; i++) {
            if (result[i] instanceof ItemStack) {
                result[i] = ((ItemStack) result[i]).copy();
            }
        }
        return result;
    }

    public static NativeRecipeDeclaration shaped(LuaValue outputValue, LuaValue patternValue, LuaValue keyTable) {
        ItemStack output = LuaApiUtils.readItemStack(outputValue, true, "output");

        if (!patternValue.istable()) {
            throw new LuaError("Recipe: shaped recipe pattern must be a table of strings.");
        }

        if (!keyTable.istable()) {
            throw new LuaError("Recipe: shaped recipe ingredients must be a table mapping characters.");
        }

        List<Object> recipe = new ArrayList<>();
        int rows = patternValue.length();
        if (rows != 2 && rows != 3) {
            throw new LuaError("Recipe: shaped recipe must have 2 or 3 rows.");
        }

        int width = -1;
        Set<Character> usedKeys = new HashSet<>();
        for (int i = 1; i <= rows; i++) {
            LuaValue rowValue = patternValue.get(i);
            if (!rowValue.isstring()) {
                throw new LuaError("Recipe: shaped recipe row " + i + " must be a string.");
            }
            String row = rowValue.tojstring();
            if (row.length() != rows) {
                throw new LuaError("Recipe: shaped recipe row " + i + " must be " + rows + " characters.");
            }
            // Keep the pattern strings as-is for ModLoader's AddRecipe format.
            if (width == -1) {
                width = row.length();
            } else if (width != row.length()) {
                throw new LuaError("Recipe: shaped recipe rows must be the same length.");
            }
            recipe.add(row);
            // Track each non-space key used in the pattern for later validation.
            for (int j = 0; j < row.length(); j++) {
                char keyChar = row.charAt(j);
                if (keyChar != ' ') {
                    usedKeys.add(new Character(keyChar));
                }
            }
        }

        Set<Character> providedKeys = new HashSet<>();
        LuaValue key = LuaValue.NIL;
        // Iterate the Lua table with next() because ingredient tables are keyed by
        // character.
        while (true) {
            Varargs next = keyTable.next(key);
            key = next.arg1();
            if (key.isnil()) {
                break;
            }
            LuaValue value = next.arg(2);
            if (!key.isstring()) {
                throw new LuaError("Recipe: shaped recipe ingredient key must be a string.");
            }
            String keyString = key.tojstring();
            if (keyString.length() != 1) {
                throw new LuaError("Recipe: shaped recipe ingredient key must be a single character.");
            }
            Character keyChar = new Character(keyString.charAt(0));
            // ModLoader expects alternating character and ingredient entries after the
            // pattern rows.
            recipe.add(keyChar);
            providedKeys.add(keyChar);
            recipe.add(LuaApiUtils.readItemStack(value, false, "ingredient '" + keyString + "'"));
        }

        // Verify that every key in the pattern has a definition in the ingredient
        // table.
        if (!usedKeys.isEmpty()) {
            for (Iterator<Character> it = usedKeys.iterator(); it.hasNext();) {
                Character needed = it.next();
                if (!providedKeys.contains(needed)) {
                    throw new LuaError("Recipe: missing shaped recipe ingredient for key '" + needed + "'.");
                }
            }
        }

        return new NativeRecipeDeclaration(Kind.SHAPED, -1, output, recipe.toArray());
    }

    public static NativeRecipeDeclaration shapeless(LuaValue outputValue, LuaValue ingredients) {
        ItemStack output = LuaApiUtils.readItemStack(outputValue, true, "output");
        if (!ingredients.istable()) {
            throw new LuaError("Recipe: shapeless recipe ingredients must be a table.");
        }
        int count = ingredients.length();
        if (count < 1 || count > 9) {
            throw new LuaError("Recipe: shapeless recipe must have 1 to 9 ingredients. Found: " + count);
        }
        List<Object> recipe = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            LuaValue value = ingredients.get(i);
            recipe.add(LuaApiUtils.readItemStack(value, false, "ingredient " + i));
        }
        return new NativeRecipeDeclaration(Kind.SHAPELESS, -1, output, recipe.toArray());
    }

    public static NativeRecipeDeclaration smelting(LuaValue input, LuaValue output) {
        int inputId = readItemId(input, "input");
        ItemStack stack = LuaApiUtils.readItemStack(output, true, "output");
        return new NativeRecipeDeclaration(Kind.SMELTING, inputId, stack, new Object[0]);
    }

    private static int readItemId(LuaValue value, String context) {
        if (value.isnumber()) {
            return value.checkint();
        }
        if (value.istable()) {
            LuaValue idValue = value.get("id");
            if (!idValue.isnil()) {
                return LuaApiUtils.resolveItemId(idValue);
            }
            LuaValue getter = value.get("getId");
            if (!getter.isnil()) {
                return LuaApiUtils.resolveItemId(getter.call(value));
            }
            return LuaApiUtils.resolveItemId(value.get(1));
        }
        throw new LuaError("Recipe: expected " + context + " to be a number or table.");
    }
}
