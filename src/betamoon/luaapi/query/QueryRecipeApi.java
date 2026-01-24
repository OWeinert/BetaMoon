package betamoon.luaapi.query;

import betamoon.luaapi.LuaApiUtils;
import betamoon.query.ContentQuery;
import betamoon.query.ContentQueryRecipe;
import betamoon.query.QueryExecutionResult;
import betamoon.query.RecipeEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

final class QueryRecipeApi {
    private QueryRecipeApi() {
    }

    static LuaValue createHandle() {
        return new RecipeQueryHandle(new ContentQueryRecipe());
    }

    private static final class RecipeQueryHandle extends LuaTable {
        private final ContentQueryRecipe query;

        private RecipeQueryHandle(ContentQueryRecipe query) {
            this.query = query;
            set("filterTypes", new FilterRecipeTypes(this));
            set("filterShaped", new FilterRecipeShaped(this));
            set("filterShapeless", new FilterRecipeShapeless(this));
            set("filterSmelting", new FilterRecipeSmelting(this));
            set("filterOutput", new FilterRecipeOutput(this));
            set("filterInput", new FilterRecipeInput(this));
            set("filterOutAndIn", new FilterRecipeOutAndIn(this));
            set("getShaped", new GetShapedRecipe(this));
            set("getShapeless", new GetShapelessRecipe(this));
            set("getSmelting", new GetSmeltingRecipe(this));
            set("getByName", new GetRecipeByName(this));
            set("fromHandle", new GetRecipeFromHandle(this));
            set("first", new FirstRecipe(this));
            set("last", new LastRecipe(this));
            set("get", new GetRecipeAt(this));
            set("finishQuery", new FinishRecipeQuery(this));
        }
    }

    private static final class FilterRecipeTypes extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private FilterRecipeTypes(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            LuaValue typesValue = LuaApiUtils.getVarArg(args, 1);
            boolean allowShaped = false;
            boolean allowShapeless = false;
            boolean allowSmelting = false;
            try {
                if (typesValue.isstring()) {
                    boolean[] flags = parseRecipeType(typesValue.tojstring());
                    allowShaped = flags[0];
                    allowShapeless = flags[1];
                    allowSmelting = flags[2];
                } else if (typesValue.istable()) {
                    int length = typesValue.length();
                    if (length < 1 || length > 3) {
                        throw new LuaError("Query: filterTypes expects 1 to 3 recipe types.");
                    }
                    for (int i = 1; i <= length; i++) {
                        boolean[] flags = parseRecipeType(typesValue.get(i).checkjstring());
                        allowShaped = allowShaped || flags[0];
                        allowShapeless = allowShapeless || flags[1];
                        allowSmelting = allowSmelting || flags[2];
                    }
                } else {
                    throw new LuaError("Query: filterTypes expects a string or table.");
                }
                handle.query.filterTypes(allowShaped, allowShapeless, allowSmelting);
            } catch (LuaError e) {
                handle.query.failStep("filterTypes", null, e.getMessage());
            }
            return handle;
        }
    }

    private static final class FilterRecipeShaped extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private FilterRecipeShaped(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.query.filterShaped();
            return handle;
        }
    }

    private static final class FilterRecipeShapeless extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private FilterRecipeShapeless(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.query.filterShapeless();
            return handle;
        }
    }

    private static final class FilterRecipeSmelting extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private FilterRecipeSmelting(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.query.filterSmelting();
            return handle;
        }
    }

    private static final class FilterRecipeOutput extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private FilterRecipeOutput(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            try {
                LuaValue outputValue = LuaApiUtils.getVarArg(args, 1);
                ItemStack output = LuaApiUtils.readItemStack(outputValue, true, "output");
                handle.query.filterOutput(output, formatStackValue(outputValue));
            } catch (LuaError e) {
                handle.query.failStep("filterOutput", null, e.getMessage());
            }
            return handle;
        }
    }

    private static final class FilterRecipeInput extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private FilterRecipeInput(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            try {
                LuaValue inputValue = LuaApiUtils.getVarArg(args, 1);
                ItemStack input = LuaApiUtils.readItemStack(inputValue, false, "input");
                handle.query.filterInput(input, formatInputValue(inputValue));
            } catch (LuaError e) {
                handle.query.failStep("filterInput", null, e.getMessage());
            }
            return handle;
        }
    }

    private static final class FilterRecipeOutAndIn extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private FilterRecipeOutAndIn(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            try {
                LuaValue outputValue = LuaApiUtils.getVarArg(args, 1);
                ItemStack output = LuaApiUtils.readItemStack(outputValue, true, "output");
                LuaValue inputsValue = LuaApiUtils.getVarArg(args, 2);
                if (!inputsValue.istable()) {
                    throw new LuaError("Query: filterOutAndIn expects a table of input item stacks.");
                }
                int count = inputsValue.length();
                if (count < 1 || count > 9) {
                    throw new LuaError("Query: filterOutAndIn input list must have 1 to 9 entries.");
                }
                List inputs = new ArrayList();
                for (int i = 1; i <= count; i++) {
                    inputs.add(LuaApiUtils.readItemStack(inputsValue.get(i), false, "input " + i));
                }
                handle.query.filterOutAndIn(output, inputs, formatOutAndInValue(outputValue, inputsValue));
            } catch (LuaError e) {
                handle.query.failStep("filterOutAndIn", null, e.getMessage());
            }
            return handle;
        }
    }

    private static final class GetShapedRecipe extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private GetShapedRecipe(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            try {
                ContentQueryRecipe.ShapedQuery query = parseShapedRecipe(LuaApiUtils.getVarArg(args, 1));
                handle.query.getShaped(query);
            } catch (LuaError e) {
                handle.query.failStep("getShaped", null, e.getMessage());
            }
            return handle;
        }
    }

    private static final class GetShapelessRecipe extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private GetShapelessRecipe(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            try {
                ContentQueryRecipe.ShapelessQuery query = parseShapelessRecipe(LuaApiUtils.getVarArg(args, 1));
                handle.query.getShapeless(query);
            } catch (LuaError e) {
                handle.query.failStep("getShapeless", null, e.getMessage());
            }
            return handle;
        }
    }

    private static final class GetSmeltingRecipe extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private GetSmeltingRecipe(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            try {
                ContentQueryRecipe.SmeltingQuery query = parseSmeltingRecipe(LuaApiUtils.getVarArg(args, 1));
                handle.query.getSmelting(query);
            } catch (LuaError e) {
                handle.query.failStep("getSmelting", null, e.getMessage());
            }
            return handle;
        }
    }

    private static final class GetRecipeByName extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private GetRecipeByName(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            String name = LuaApiUtils.getStringArg(args, 1);
            handle.query.getByName(name);
            return handle;
        }
    }

    private static final class GetRecipeFromHandle extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private GetRecipeFromHandle(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            LuaValue value = LuaApiUtils.getVarArg(args, 1);
            Object recipe = value.isuserdata() ? value.touserdata() : null;
            handle.query.fromHandle(recipe);
            return handle;
        }
    }

    private static final class FirstRecipe extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private FirstRecipe(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.query.first();
            return handle;
        }
    }

    private static final class LastRecipe extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private LastRecipe(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.query.last();
            return handle;
        }
    }

    private static final class GetRecipeAt extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private GetRecipeAt(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int index = (int) LuaApiUtils.getNumberArg(args, 1);
            handle.query.get(index);
            return handle;
        }
    }

    private static final class FinishRecipeQuery extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private FinishRecipeQuery(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            QueryExecutionResult<List<RecipeEntry>> result = handle.query.execute();
            if (result.isFailure()) {
                return QueryApiUtils.pushWarning(result.getFailure());
            }
            if (result.getWarning() != null) {
                QueryApiUtils.pushWarning(result.getWarning());
            }
            List<RecipeEntry> entries = result.getState();
            ContentQuery.ResultMode mode = result.getResultMode();
            if (mode == ContentQuery.ResultMode.SINGLE) {
                if (entries.isEmpty()) {
                    return LuaValue.NIL;
                }
                Object recipe = extractSingleRecipe(entries);
                if (recipe == null) {
                    return QueryApiUtils.pushNil("Query: expected exactly one recipe, found " + entries.size());
                }
                return LuaValue.userdataOf(recipe);
            }
            return new RecipeQueryResultHandle(extractRecipes(entries));
        }
    }

    private static final class RecipeQueryResultHandle extends LuaTable {
        private final List recipes;

        private RecipeQueryResultHandle(List recipes) {
            this.recipes = recipes;
            set("first", new FirstRecipeResult(this));
            set("last", new LastRecipeResult(this));
            set("get", new GetRecipeResultAt(this));
            set("count", new CountRecipeResults(this));
            set("ensureOne", new EnsureOneRecipeResult(this));
        }
    }

    private static final class FirstRecipeResult extends VarArgFunction {
        private final RecipeQueryResultHandle handle;

        private FirstRecipeResult(RecipeQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.recipes.isEmpty()) {
                return LuaValue.NIL;
            }
            return LuaValue.userdataOf(handle.recipes.get(0));
        }
    }

    private static final class LastRecipeResult extends VarArgFunction {
        private final RecipeQueryResultHandle handle;

        private LastRecipeResult(RecipeQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.recipes.isEmpty()) {
                return LuaValue.NIL;
            }
            return LuaValue.userdataOf(handle.recipes.get(handle.recipes.size() - 1));
        }
    }

    private static final class GetRecipeResultAt extends VarArgFunction {
        private final RecipeQueryResultHandle handle;

        private GetRecipeResultAt(RecipeQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int index = (int) LuaApiUtils.getNumberArg(args, 1);
            if (index < 1 || index > handle.recipes.size()) {
                return QueryApiUtils.pushNil("Query: recipe index out of bounds: " + index);
            }
            return LuaValue.userdataOf(handle.recipes.get(index - 1));
        }
    }

    private static final class CountRecipeResults extends VarArgFunction {
        private final RecipeQueryResultHandle handle;

        private CountRecipeResults(RecipeQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(handle.recipes.size());
        }
    }

    private static final class EnsureOneRecipeResult extends VarArgFunction {
        private final RecipeQueryResultHandle handle;

        private EnsureOneRecipeResult(RecipeQueryResultHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (handle.recipes.size() != 1) {
                return QueryApiUtils.pushNil("Query: expected exactly one recipe, found " + handle.recipes.size());
            }
            return handle;
        }
    }

    private static boolean[] parseRecipeType(String type) {
        String value = type == null ? "" : type.trim().toLowerCase();
        if ("shaped".equals(value)) {
            return new boolean[] { true, false, false };
        }
        if ("shapeless".equals(value)) {
            return new boolean[] { false, true, false };
        }
        if ("smelting".equals(value)) {
            return new boolean[] { false, false, true };
        }
        throw new LuaError("Query: unknown recipe type: " + type);
    }

    private static ContentQueryRecipe.ShapedQuery parseShapedRecipe(LuaValue table) {
        if (!table.istable()) {
            throw new LuaError("Query: shaped recipe must be a table.");
        }
        ItemStack output = LuaApiUtils.readItemStack(getRecipeField(table, "output", 1), true, "output");
        LuaValue patternValue = getRecipeField(table, "pattern", 2);
        LuaValue keyTable = getRecipeField(table, "key", 3);
        if (!patternValue.istable()) {
            throw new LuaError("Query: shaped recipe pattern must be a table of strings.");
        }
        if (!keyTable.istable()) {
            throw new LuaError("Query: shaped recipe ingredients must be a table mapping characters.");
        }
        int rows = patternValue.length();
        if (rows != 2 && rows != 3) {
            throw new LuaError("Query: shaped recipe must have 2 or 3 rows.");
        }
        int width = -1;
        ItemStack[] grid = null;
        for (int i = 1; i <= rows; i++) {
            LuaValue rowValue = patternValue.get(i);
            if (!rowValue.isstring()) {
                throw new LuaError("Query: shaped recipe row " + i + " must be a string.");
            }
            String row = rowValue.tojstring();
            if (row.length() != rows) {
                throw new LuaError("Query: shaped recipe row " + i + " must be " + rows + " characters.");
            }
            if (width == -1) {
                width = row.length();
                grid = new ItemStack[width * rows];
            } else if (width != row.length()) {
                throw new LuaError("Query: shaped recipe rows must be the same length.");
            }
            for (int j = 0; j < row.length(); j++) {
                char keyChar = row.charAt(j);
                if (keyChar == ' ') {
                    continue;
                }
                String keyString = String.valueOf(keyChar);
                LuaValue ingredientValue = keyTable.get(keyString);
                if (ingredientValue.isnil()) {
                    throw new LuaError("Query: missing shaped recipe ingredient for key '" + keyChar + "'.");
                }
                grid[j + (i - 1) * width] = LuaApiUtils.readItemStack(ingredientValue, false,
                    "ingredient '" + keyString + "'");
            }
        }
        return new ContentQueryRecipe.ShapedQuery(output, width, rows, grid);
    }

    private static ContentQueryRecipe.ShapelessQuery parseShapelessRecipe(LuaValue table) {
        if (!table.istable()) {
            throw new LuaError("Query: shapeless recipe must be a table.");
        }
        ItemStack output = LuaApiUtils.readItemStack(getRecipeField(table, "output", 1), true, "output");
        LuaValue ingredientsValue = getRecipeField(table, "ingredients", 2);
        if (!ingredientsValue.istable()) {
            throw new LuaError("Query: shapeless recipe ingredients must be a table.");
        }
        int count = ingredientsValue.length();
        if (count < 1 || count > 9) {
            throw new LuaError("Query: shapeless recipe must have 1 to 9 ingredients.");
        }
        List ingredients = new ArrayList();
        for (int i = 1; i <= count; i++) {
            ingredients.add(LuaApiUtils.readItemStack(ingredientsValue.get(i), false, "ingredient " + i));
        }
        return new ContentQueryRecipe.ShapelessQuery(output, ingredients);
    }

    private static ContentQueryRecipe.SmeltingQuery parseSmeltingRecipe(LuaValue table) {
        if (!table.istable()) {
            throw new LuaError("Query: smelting recipe must be a table.");
        }
        int inputId = readItemId(getRecipeField(table, "input", 1), "input");
        ItemStack output = LuaApiUtils.readItemStack(getRecipeField(table, "output", 2), true, "output");
        return new ContentQueryRecipe.SmeltingQuery(inputId, output);
    }

    private static LuaValue getRecipeField(LuaValue table, String name, int index) {
        LuaValue value = table.get(name);
        if (!value.isnil()) {
            return value;
        }
        return table.get(index);
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
        throw new LuaError("Query: expected " + context + " to be a number or table.");
    }

    private static String formatInputValue(LuaValue value) {
        if (value == null || value.isnil()) {
            return "nil";
        }
        if (value.isnumber()) {
            return String.valueOf(value.checkint());
        }
        if (!value.istable()) {
            return value.tojstring();
        }
        StringBuilder builder = new StringBuilder();
        builder.append("{ ");
        boolean first = true;
        LuaValue idValue = value.get("id");
        if (!idValue.isnil()) {
            builder.append("id = ").append(idValue.checkint());
            first = false;
        }
        LuaValue countValue = value.get("count");
        if (!countValue.isnil()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append("count = ").append(countValue.checkint());
            first = false;
        }
        LuaValue damageValue = value.get("damage");
        if (!damageValue.isnil()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append("damage = ").append(damageValue.checkint());
            first = false;
        }
        if (first) {
            LuaValue positional = value.get(1);
            if (!positional.isnil()) {
                builder.append("id = ").append(LuaApiUtils.resolveItemId(positional));
                first = false;
            }
            LuaValue positionalCount = value.get(2);
            if (!positionalCount.isnil()) {
                if (!first) {
                    builder.append(", ");
                }
                builder.append("count = ").append(positionalCount.checkint());
                first = false;
            }
        }
        builder.append(" }");
        if (first) {
            return value.tojstring();
        }
        return builder.toString();
    }

    private static String formatStackValue(LuaValue value) {
        if (value == null || value.isnil()) {
            return "nil";
        }
        if (value.isnumber()) {
            return String.valueOf(value.checkint());
        }
        if (!value.istable()) {
            return value.tojstring();
        }
        StringBuilder builder = new StringBuilder();
        builder.append("{ ");
        boolean first = true;
        LuaValue idValue = value.get("id");
        if (!idValue.isnil()) {
            builder.append("id = ").append(idValue.checkint());
            first = false;
        }
        LuaValue countValue = value.get("count");
        if (!countValue.isnil()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append("count = ").append(countValue.checkint());
            first = false;
        }
        LuaValue damageValue = value.get("damage");
        if (!damageValue.isnil()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append("damage = ").append(damageValue.checkint());
            first = false;
        }
        if (first) {
            LuaValue positional = value.get(1);
            if (!positional.isnil()) {
                builder.append("id = ").append(LuaApiUtils.resolveItemId(positional));
                first = false;
            }
            LuaValue positionalCount = value.get(2);
            if (!positionalCount.isnil()) {
                if (!first) {
                    builder.append(", ");
                }
                builder.append("count = ").append(positionalCount.checkint());
                first = false;
            }
        }
        builder.append(" }");
        if (first) {
            return value.tojstring();
        }
        return builder.toString();
    }

    private static String formatOutAndInValue(LuaValue outputValue, LuaValue inputsValue) {
        StringBuilder builder = new StringBuilder();
        builder.append(formatStackValue(outputValue));
        builder.append(", ");
        builder.append(formatInputListValue(inputsValue));
        return builder.toString();
    }

    private static String formatInputListValue(LuaValue value) {
        if (value == null || value.isnil()) {
            return "nil";
        }
        if (!value.istable()) {
            return value.tojstring();
        }
        int count = value.length();
        StringBuilder builder = new StringBuilder();
        builder.append("{ ");
        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                builder.append(", ");
            }
            builder.append(formatStackValue(value.get(i)));
        }
        builder.append(" }");
        return builder.toString();
    }

    private static Object extractSingleRecipe(List<RecipeEntry> entries) {
        if (entries == null || entries.size() != 1) {
            return null;
        }
        return entries.get(0).getRecipe();
    }

    private static List extractRecipes(List<RecipeEntry> entries) {
        List recipes = new ArrayList();
        if (entries == null) {
            return recipes;
        }
        for (int i = 0; i < entries.size(); i++) {
            RecipeEntry entry = entries.get(i);
            recipes.add(entry.getRecipe());
        }
        return recipes;
    }

    
}
