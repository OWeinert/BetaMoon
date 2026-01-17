package betamoon.luaapi.query;

import betamoon.luaapi.LuaApiUtils;
import betamoon.recipes.RecipeModificationHandler;
import betamoon.recipes.SmeltingRecipe;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.IRecipe;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ShapedRecipes;
import net.minecraft.src.ShapelessRecipes;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

final class QueryRecipeApi {
    private QueryRecipeApi() {
    }

    static LuaValue createHandle() {
        return new RecipeQueryHandle();
    }

    private static final class RecipeQueryHandle extends LuaTable {
        private Map recipeMap;

        private RecipeQueryHandle() {
            if (RecipeModificationHandler.getRecipeMap() == null) {
                RecipeModificationHandler.createRecipeMap();
            }
            recipeMap = new LinkedHashMap(RecipeModificationHandler.getRecipeMap());
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
            set("count", new CountRecipes(this));
            set("finishQuery", new FinishRecipeQuery(this));
        }

        private List toList() {
            return new ArrayList(recipeMap.values());
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
            handle.recipeMap = filterByTypes(handle.recipeMap, allowShaped, allowShapeless, allowSmelting);
            return handle;
        }
    }

    private static final class FilterRecipeShaped extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private FilterRecipeShaped(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.recipeMap = filterByTypes(handle.recipeMap, true, false, false);
            return handle;
        }
    }

    private static final class FilterRecipeShapeless extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private FilterRecipeShapeless(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.recipeMap = filterByTypes(handle.recipeMap, false, true, false);
            return handle;
        }
    }

    private static final class FilterRecipeSmelting extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private FilterRecipeSmelting(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.recipeMap = filterByTypes(handle.recipeMap, false, false, true);
            return handle;
        }
    }

    private static final class FilterRecipeOutput extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private FilterRecipeOutput(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ItemStack output = LuaApiUtils.readItemStack(LuaApiUtils.getVarArg(args, 1), true, "output");
            handle.recipeMap = filterByOutput(handle.recipeMap, output);
            return handle;
        }
    }

    private static final class FilterRecipeInput extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private FilterRecipeInput(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ItemStack input = LuaApiUtils.readItemStack(LuaApiUtils.getVarArg(args, 1), false, "input");
            handle.recipeMap = filterByInput(handle.recipeMap, input);
            return handle;
        }
    }

    private static final class FilterRecipeOutAndIn extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private FilterRecipeOutAndIn(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ItemStack output = LuaApiUtils.readItemStack(LuaApiUtils.getVarArg(args, 1), true, "output");
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
            handle.recipeMap = filterByOutputAndInputs(handle.recipeMap, output, inputs);
            return handle;
        }
    }

    private static final class GetShapedRecipe extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private GetShapedRecipe(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ShapedQuery query = parseShapedRecipe(LuaApiUtils.getVarArg(args, 1));
            Object match = findMatchingShaped(handle.recipeMap, query);
            if (match == null) {
                throw new LuaError("Query: shaped recipe not found.");
            }
            return LuaValue.userdataOf(match);
        }
    }

    private static final class GetShapelessRecipe extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private GetShapelessRecipe(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ShapelessQuery query = parseShapelessRecipe(LuaApiUtils.getVarArg(args, 1));
            Object match = findMatchingShapeless(handle.recipeMap, query);
            if (match == null) {
                throw new LuaError("Query: shapeless recipe not found.");
            }
            return LuaValue.userdataOf(match);
        }
    }

    private static final class GetSmeltingRecipe extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private GetSmeltingRecipe(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            SmeltingQuery query = parseSmeltingRecipe(LuaApiUtils.getVarArg(args, 1));
            Object match = findMatchingSmelting(handle.recipeMap, query);
            if (match == null) {
                throw new LuaError("Query: smelting recipe not found.");
            }
            return LuaValue.userdataOf(match);
        }
    }

    private static final class GetRecipeByName extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private GetRecipeByName(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            String name = LuaApiUtils.getStringArg(args, 1);
            Object recipe = handle.recipeMap.get(name);
            if (recipe == null) {
                throw new LuaError("Query: recipe not found: " + name);
            }
            return LuaValue.userdataOf(recipe);
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
            if (!(recipe instanceof IRecipe) && !(recipe instanceof SmeltingRecipe)) {
                throw new LuaError("Query: expected a recipe handle.");
            }
            if (!handle.recipeMap.containsValue(recipe)) {
                throw new LuaError("Query: recipe handle not present in registry.");
            }
            return LuaValue.userdataOf(recipe);
        }
    }

    private static final class FirstRecipe extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private FirstRecipe(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            List list = handle.toList();
            if (list.isEmpty()) {
                return LuaValue.NIL;
            }
            return LuaValue.userdataOf(list.get(0));
        }
    }

    private static final class LastRecipe extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private LastRecipe(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            List list = handle.toList();
            if (list.isEmpty()) {
                return LuaValue.NIL;
            }
            return LuaValue.userdataOf(list.get(list.size() - 1));
        }
    }

    private static final class GetRecipeAt extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private GetRecipeAt(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int index = (int) LuaApiUtils.getNumberArg(args, 1);
            List list = handle.toList();
            if (index < 1 || index > list.size()) {
                throw new LuaError("Query: recipe index out of bounds: " + index);
            }
            return LuaValue.userdataOf(list.get(index - 1));
        }
    }

    private static final class CountRecipes extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private CountRecipes(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(handle.recipeMap.size());
        }
    }

    private static final class FinishRecipeQuery extends VarArgFunction {
        private final RecipeQueryHandle handle;

        private FinishRecipeQuery(RecipeQueryHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            return new RecipeQueryResultHandle(handle.toList());
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
                throw new LuaError("Query: recipe index out of bounds: " + index);
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
                throw new LuaError("Query: expected exactly one recipe, found " + handle.recipes.size());
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

    private static Map filterByTypes(Map source, boolean allowShaped, boolean allowShapeless, boolean allowSmelting) {
        Map filtered = new LinkedHashMap();
        for (java.util.Iterator it = source.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            Object recipe = entry.getValue();
            if (recipe instanceof ShapedRecipes) {
                if (allowShaped) {
                    filtered.put(entry.getKey(), recipe);
                }
            } else if (recipe instanceof ShapelessRecipes) {
                if (allowShapeless) {
                    filtered.put(entry.getKey(), recipe);
                }
            } else if (recipe instanceof SmeltingRecipe) {
                if (allowSmelting) {
                    filtered.put(entry.getKey(), recipe);
                }
            }
        }
        return filtered;
    }

    private static Map filterByOutput(Map source, ItemStack output) {
        Map filtered = new LinkedHashMap();
        for (java.util.Iterator it = source.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            Object recipe = entry.getValue();
            ItemStack recipeOutput = getRecipeOutput(recipe);
            if (QueryCommon.matchesOutput(recipeOutput, output)) {
                filtered.put(entry.getKey(), recipe);
            }
        }
        return filtered;
    }

    private static Map filterByInput(Map source, ItemStack input) {
        Map filtered = new LinkedHashMap();
        for (java.util.Iterator it = source.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            Object recipe = entry.getValue();
            if (matchesInput(recipe, input)) {
                filtered.put(entry.getKey(), recipe);
            }
        }
        return filtered;
    }

    private static Map filterByOutputAndInputs(Map source, ItemStack output, List inputs) {
        Map filtered = new LinkedHashMap();
        boolean allowSmelting = inputs.size() == 1;
        for (java.util.Iterator it = source.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            Object recipe = entry.getValue();
            ItemStack recipeOutput = getRecipeOutput(recipe);
            if (!QueryCommon.matchesOutput(recipeOutput, output)) {
                continue;
            }
            if (recipe instanceof SmeltingRecipe) {
                if (!allowSmelting) {
                    continue;
                }
                SmeltingRecipe smelting = (SmeltingRecipe) recipe;
                ItemStack inputStack = (ItemStack) inputs.get(0);
                if (smelting.getInputId() == inputStack.itemID) {
                    filtered.put(entry.getKey(), recipe);
                }
                continue;
            }
            if (recipe instanceof IRecipe) {
                if (matchesInputs((IRecipe) recipe, inputs)) {
                    filtered.put(entry.getKey(), recipe);
                }
            }
        }
        return filtered;
    }

    private static boolean matchesInput(Object recipe, ItemStack target) {
        if (recipe instanceof SmeltingRecipe) {
            return ((SmeltingRecipe) recipe).getInputId() == target.itemID;
        }
        if (!(recipe instanceof IRecipe)) {
            return false;
        }
        IRecipe craft = (IRecipe) recipe;
        if (craft instanceof ShapedRecipes) {
            ItemStack[] items = QueryCommon.getShapedInputs((ShapedRecipes) craft);
            if (items == null) {
                return false;
            }
            for (int i = 0; i < items.length; i++) {
                ItemStack stack = items[i];
                if (stack != null && QueryCommon.matchesStack(stack, target)) {
                    return true;
                }
            }
            return false;
        }
        if (craft instanceof ShapelessRecipes) {
            List items = QueryCommon.getShapelessInputs((ShapelessRecipes) craft);
            if (items == null) {
                return false;
            }
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = QueryCommon.normalizeIngredient(items.get(i));
                if (stack != null && QueryCommon.matchesStack(stack, target)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesInputs(IRecipe recipe, List inputs) {
        List recipeInputs = collectRecipeInputs(recipe);
        if (recipeInputs == null) {
            return false;
        }
        List remaining = new ArrayList(recipeInputs);
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack target = (ItemStack) inputs.get(i);
            boolean matched = false;
            for (int j = 0; j < remaining.size(); j++) {
                ItemStack candidate = (ItemStack) remaining.get(j);
                if (candidate != null && QueryCommon.matchesStack(candidate, target)) {
                    remaining.remove(j);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private static List collectRecipeInputs(IRecipe recipe) {
        if (recipe instanceof ShapedRecipes) {
            ItemStack[] items = QueryCommon.getShapedInputs((ShapedRecipes) recipe);
            if (items == null) {
                return null;
            }
            List list = new ArrayList();
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null) {
                    list.add(items[i]);
                }
            }
            return list;
        }
        if (recipe instanceof ShapelessRecipes) {
            List items = QueryCommon.getShapelessInputs((ShapelessRecipes) recipe);
            if (items == null) {
                return null;
            }
            List list = new ArrayList();
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = QueryCommon.normalizeIngredient(items.get(i));
                if (stack != null) {
                    list.add(stack);
                }
            }
            return list;
        }
        return null;
    }

    private static ItemStack getRecipeOutput(Object recipe) {
        if (recipe instanceof SmeltingRecipe) {
            return ((SmeltingRecipe) recipe).getRecipeOutput();
        }
        if (recipe instanceof IRecipe) {
            return ((IRecipe) recipe).getRecipeOutput();
        }
        return null;
    }

    private static class ShapedQuery {
        private final ItemStack output;
        private final int width;
        private final int height;
        private final ItemStack[] grid;

        private ShapedQuery(ItemStack output, int width, int height, ItemStack[] grid) {
            this.output = output;
            this.width = width;
            this.height = height;
            this.grid = grid;
        }
    }

    private static class ShapelessQuery {
        private final ItemStack output;
        private final List inputs;

        private ShapelessQuery(ItemStack output, List inputs) {
            this.output = output;
            this.inputs = inputs;
        }
    }

    private static class SmeltingQuery {
        private final int inputId;
        private final ItemStack output;

        private SmeltingQuery(int inputId, ItemStack output) {
            this.inputId = inputId;
            this.output = output;
        }
    }

    private static ShapedQuery parseShapedRecipe(LuaValue table) {
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
                grid[j + (i - 1) * width] = LuaApiUtils.readItemStack(ingredientValue, false, "ingredient '" + keyString + "'");
            }
        }
        return new ShapedQuery(output, width, rows, grid);
    }

    private static ShapelessQuery parseShapelessRecipe(LuaValue table) {
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
        return new ShapelessQuery(output, ingredients);
    }

    private static SmeltingQuery parseSmeltingRecipe(LuaValue table) {
        if (!table.istable()) {
            throw new LuaError("Query: smelting recipe must be a table.");
        }
        int inputId = readItemId(getRecipeField(table, "input", 1), "input");
        ItemStack output = LuaApiUtils.readItemStack(getRecipeField(table, "output", 2), true, "output");
        return new SmeltingQuery(inputId, output);
    }

    private static LuaValue getRecipeField(LuaValue table, String name, int index) {
        LuaValue value = table.get(name);
        if (!value.isnil()) {
            return value;
        }
        return table.get(index);
    }

    private static Object findMatchingShaped(Map source, ShapedQuery query) {
        for (java.util.Iterator it = source.values().iterator(); it.hasNext();) {
            Object recipe = it.next();
            if (!(recipe instanceof ShapedRecipes)) {
                continue;
            }
            ShapedRecipes shaped = (ShapedRecipes) recipe;
            ItemStack output = shaped.getRecipeOutput();
            if (!QueryCommon.matchesOutput(output, query.output)) {
                continue;
            }
            ItemStack[] items = QueryCommon.getShapedInputs(shaped);
            if (items == null) {
                continue;
            }
            int[] dims = QueryCommon.getShapedDimensions(shaped, items.length);
            if (dims == null) {
                continue;
            }
            if (dims[0] != query.width || dims[1] != query.height) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; i < items.length; i++) {
                ItemStack expected = query.grid[i];
                ItemStack actual = items[i];
                if (expected == null && actual == null) {
                    continue;
                }
                if (expected == null || actual == null) {
                    matches = false;
                    break;
                }
                if (!QueryCommon.matchesStack(actual, expected)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return recipe;
            }
        }
        return null;
    }

    private static Object findMatchingShapeless(Map source, ShapelessQuery query) {
        for (java.util.Iterator it = source.values().iterator(); it.hasNext();) {
            Object recipe = it.next();
            if (!(recipe instanceof ShapelessRecipes)) {
                continue;
            }
            ShapelessRecipes shapeless = (ShapelessRecipes) recipe;
            ItemStack output = shapeless.getRecipeOutput();
            if (!QueryCommon.matchesOutput(output, query.output)) {
                continue;
            }
            List recipeInputs = collectRecipeInputs(shapeless);
            if (recipeInputs == null || recipeInputs.size() != query.inputs.size()) {
                continue;
            }
            if (matchesInputList(recipeInputs, query.inputs)) {
                return recipe;
            }
        }
        return null;
    }

    private static Object findMatchingSmelting(Map source, SmeltingQuery query) {
        for (java.util.Iterator it = source.values().iterator(); it.hasNext();) {
            Object recipe = it.next();
            if (!(recipe instanceof SmeltingRecipe)) {
                continue;
            }
            SmeltingRecipe smelting = (SmeltingRecipe) recipe;
            if (smelting.getInputId() != query.inputId) {
                continue;
            }
            if (!QueryCommon.matchesOutput(smelting.getRecipeOutput(), query.output)) {
                continue;
            }
            return recipe;
        }
        return null;
    }

    private static boolean matchesInputList(List recipeInputs, List desiredInputs) {
        List remaining = new ArrayList(recipeInputs);
        for (int i = 0; i < desiredInputs.size(); i++) {
            ItemStack target = (ItemStack) desiredInputs.get(i);
            boolean matched = false;
            for (int j = 0; j < remaining.size(); j++) {
                ItemStack candidate = (ItemStack) remaining.get(j);
                if (candidate != null && QueryCommon.matchesStack(candidate, target)) {
                    remaining.remove(j);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
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
}
