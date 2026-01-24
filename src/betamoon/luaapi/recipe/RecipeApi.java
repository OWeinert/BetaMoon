package betamoon.luaapi.recipe;

import betamoon.luaapi.LuaApiUtils;
import betamoon.recipes.RecipeModificationHandler;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ModLoader;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class RecipeApi {
    /**
     * Utility class that installs recipe-related Lua bindings.
     */
    private RecipeApi() {
    }

    public static void attach(LuaTable module) {
        module.set("addShapedRecipe", new AddShapedRecipe());
        module.set("addShapelessRecipe", new AddShapelessRecipe());
        module.set("addSmeltingRecipe", new AddSmeltingRecipe());
    }

    private static final class AddShapedRecipe extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            ItemStack output = LuaApiUtils.readItemStack(args.arg(1), true, "output");
            LuaValue patternValue = args.arg(2);
            LuaValue keyTable = args.arg(3);

            if (!patternValue.istable()) {
                throw new LuaError("Recipe: shaped recipe pattern must be a table of strings.");
            }

            if (!keyTable.istable()) {
                throw new LuaError("Recipe: shaped recipe ingredients must be a table mapping characters.");
            }

            List recipe = new ArrayList();
            int rows = patternValue.length();
            if (rows != 2 && rows != 3) {
                throw new LuaError("Recipe: shaped recipe must have 2 or 3 rows.");
            }

            int width = -1;
            Set usedKeys = new HashSet();
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

            Set providedKeys = new HashSet();
            LuaValue key = LuaValue.NIL;
            // Iterate the Lua table with next() because ingredient tables are keyed by character.
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
                // ModLoader expects alternating character and ingredient entries after the pattern rows.
                recipe.add(keyChar);
                providedKeys.add(keyChar);
                recipe.add(LuaApiUtils.readItemStack(value, false, "ingredient '" + keyString + "'"));
            }

            // Verify that every key in the pattern has a definition in the ingredient table.
            if (!usedKeys.isEmpty()) {
                for (java.util.Iterator it = usedKeys.iterator(); it.hasNext();) {
                    Character needed = (Character) it.next();
                    if (!providedKeys.contains(needed)) {
                        throw new LuaError("Recipe: missing shaped recipe ingredient for key '" + needed + "'.");
                    }
                }
            }
            
            ModLoader.AddRecipe(output, recipe.toArray(new Object[recipe.size()]));
            RecipeModificationHandler.addLatestCraftingRecipe();
            return LuaValue.NIL;
        }
    }

    private static final class AddShapelessRecipe extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            ItemStack output = LuaApiUtils.readItemStack(args.arg(1), true, "output");
            LuaValue ingredients = args.arg(2);
            if (!ingredients.istable()) {
                throw new LuaError("Recipe: shapeless recipe ingredients must be a table.");
            }
            int count = ingredients.length();
            if (count < 1 || count > 9) {
                throw new LuaError("Recipe: shapeless recipe must have 1 to 9 ingredients. Found: " + count);
            }
            List recipe = new ArrayList();
            for (int i = 1; i <= count; i++) {
                LuaValue value = ingredients.get(i);
                recipe.add(LuaApiUtils.readItemStack(value, false, "ingredient " + i));
            }
            ModLoader.AddShapelessRecipe(output, recipe.toArray(new Object[recipe.size()]));
            RecipeModificationHandler.addLatestCraftingRecipe();
            return LuaValue.NIL;
        }
    }

    private static final class AddSmeltingRecipe extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            int inputId = readItemId(args.arg(1), "input");
            ItemStack output = LuaApiUtils.readItemStack(args.arg(2), true, "output");
            ModLoader.AddSmelting(inputId, output);
            RecipeModificationHandler.addSmeltingRecipeEntry(inputId, output);
            return LuaValue.NIL;
        }
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
