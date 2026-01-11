package betamoon.luaapi;

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

final class RecipeApi {
    /**
     * Utility class that installs recipe-related Lua bindings.
     */
    private RecipeApi() {
    }

    static void attach(LuaTable module) {
        module.set("addShapedRecipe", new AddShapedRecipe());
        module.set("addShapelessRecipe", new AddShapelessRecipe());
        module.set("addSmeltingRecipe", new AddSmeltingRecipe());
    }

    private static final class AddShapedRecipe extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            ItemStack output = readItemStack(args.arg(1), true, "output");
            LuaValue patternValue = args.arg(2);
            LuaValue keyTable = args.arg(3);

            if (!patternValue.istable()) {
                throw new LuaError("Shaped recipe pattern must be a table of strings.");
            }

            if (!keyTable.istable()) {
                throw new LuaError("Shaped recipe ingredients must be a table mapping characters.");
            }

            List recipe = new ArrayList();
            int rows = patternValue.length();
            if (rows != 2 && rows != 3) {
                throw new LuaError("Shaped recipe must have 2 or 3 rows.");
            }

            int width = -1;
            Set usedKeys = new HashSet();
            for (int i = 1; i <= rows; i++) {
                LuaValue rowValue = patternValue.get(i);
                if (!rowValue.isstring()) {
                    throw new LuaError("Shaped recipe row " + i + " must be a string.");
                }
                String row = rowValue.tojstring();
                if (row.length() != rows) {
                    throw new LuaError("Shaped recipe row " + i + " must be " + rows + " characters.");
                }
                // Keep the pattern strings as-is for ModLoader's AddRecipe format.
                if (width == -1) {
                    width = row.length();
                } else if (width != row.length()) {
                    throw new LuaError("Shaped recipe rows must be the same length.");
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
                    throw new LuaError("Shaped recipe ingredient key must be a string.");
                }
                String keyString = key.tojstring();
                if (keyString.length() != 1) {
                    throw new LuaError("Shaped recipe ingredient key must be a single character.");
                }
                Character keyChar = new Character(keyString.charAt(0));
                // ModLoader expects alternating character and ingredient entries after the pattern rows.
                recipe.add(keyChar);
                providedKeys.add(keyChar);
                recipe.add(readIngredient(value, "ingredient '" + keyString + "'"));
            }

            // Verify that every key in the pattern has a definition in the ingredient table.
            if (!usedKeys.isEmpty()) {
                for (java.util.Iterator it = usedKeys.iterator(); it.hasNext();) {
                    Character needed = (Character) it.next();
                    if (!providedKeys.contains(needed)) {
                        throw new LuaError("Missing shaped recipe ingredient for key '" + needed + "'.");
                    }
                }
            }
            
            ModLoader.AddRecipe(output, recipe.toArray(new Object[recipe.size()]));
            return LuaValue.NIL;
        }
    }

    private static final class AddShapelessRecipe extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            ItemStack output = readItemStack(args.arg(1), true, "output");
            LuaValue ingredients = args.arg(2);
            if (!ingredients.istable()) {
                throw new LuaError("Shapeless recipe ingredients must be a table.");
            }
            int count = ingredients.length();
            if (count < 1 || count > 9) {
                throw new LuaError("Shapeless recipe must have 1 to 9 ingredients. Found: " + count);
            }
            List recipe = new ArrayList();
            for (int i = 1; i <= count; i++) {
                LuaValue value = ingredients.get(i);
                recipe.add(readIngredient(value, "ingredient " + i));
            }
            ModLoader.AddShapelessRecipe(output, recipe.toArray(new Object[recipe.size()]));
            return LuaValue.NIL;
        }
    }

    private static final class AddSmeltingRecipe extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            int inputId = readItemId(args.arg(1), "input");
            ItemStack output = readItemStack(args.arg(2), true, "output");
            ModLoader.AddSmelting(inputId, output);
            return LuaValue.NIL;
        }
    }

    private static Object readIngredient(LuaValue value, String context) {
        return readItemStack(value, false, context);
    }

    private static ItemStack readItemStack(LuaValue value, boolean allowCount, String context) {
        if (value.isnumber()) {
            // Numeric ids are treated as a single item with zero damage.
            int id = value.checkint();
            return new ItemStack(id, 1, 0);
        }
        if (value.istable()) {
            // Accept either named fields (id/count/damage), handles with getId(), or positional (id, count, damage).
            LuaValue idValue = value.get("id");
            int id;
            if (!idValue.isnil()) {
                id = idValue.checkint();
            } else if (!value.get("getId").isnil()) {
                id = value.get("getId").call(value).checkint();
            } else {
                id = value.get(1).checkint();
            }
            int count = 1;
            int damage = 0;
            LuaValue countValue = value.get("count");
            if (!countValue.isnil()) {
                count = countValue.checkint();
            } else if (!value.get(2).isnil()) {
                count = value.get(2).checkint();
            }
            LuaValue damageValue = value.get("damage");
            if (!damageValue.isnil()) {
                damage = damageValue.checkint();
            } else if (!value.get(3).isnil()) {
                damage = value.get(3).checkint();
            }
            // Ingredient stacks ignore count; outputs keep it when allowCount is true.
            if (!allowCount) {
                count = 1;
            }
            return new ItemStack(id, count, damage);
        }
        throw new LuaError("Expected " + context + " to be a number or table.");
    }

    private static int readItemId(LuaValue value, String context) {
        if (value.isnumber()) {
            return value.checkint();
        }
        if (value.istable()) {
            LuaValue idValue = value.get("id");
            if (!idValue.isnil()) {
                return idValue.checkint();
            }
            LuaValue getter = value.get("getId");
            if (!getter.isnil()) {
                return getter.call(value).checkint();
            }
            return value.get(1).checkint();
        }
        throw new LuaError("Expected " + context + " to be a number or table.");
    }
}
