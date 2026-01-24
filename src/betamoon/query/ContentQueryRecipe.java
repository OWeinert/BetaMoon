package betamoon.query;

import betamoon.BetaMoonMain;
import betamoon.recipes.RecipeModificationHandler;
import betamoon.recipes.SmeltingRecipe;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.src.FurnaceRecipes;
import net.minecraft.src.IRecipe;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ShapedRecipes;
import net.minecraft.src.ShapelessRecipes;

public final class ContentQueryRecipe extends ContentQuery<RecipeEntry> {
    private static final java.util.logging.Logger LOGGER = BetaMoonMain.LOGGER;

    @Override
    protected List<RecipeEntry> createInitialState() {
        RecipeModificationHandler.createRecipeMap();
        return listFromMap(RecipeModificationHandler.getRecipeMap());
    }

    @Override
    protected boolean isEmpty(List<RecipeEntry> state) {
        return state == null || state.isEmpty();
    }

    @Override
    protected String getQueryType() {
        return "recipe";
    }

    @Override
    protected QueryStepResult<List<RecipeEntry>> selectFirst(List<RecipeEntry> state) {
        if (state.isEmpty()) {
            return QueryStepResult.failure(emptyQueryMessage());
        }
        return QueryStepResult.success(wrapSingle(state.get(0)));
    }

    @Override
    protected QueryStepResult<List<RecipeEntry>> selectLast(List<RecipeEntry> state) {
        if (state.isEmpty()) {
            return QueryStepResult.failure(emptyQueryMessage());
        }
        return QueryStepResult.success(wrapSingle(state.get(state.size() - 1)));
    }

    @Override
    protected QueryStepResult<List<RecipeEntry>> selectByIndex(List<RecipeEntry> state, int index) {
        if (index < 1 || index > state.size()) {
            return QueryStepResult.failure("Query: recipe index out of bounds: " + index);
        }
        return QueryStepResult.success(wrapSingle(state.get(index - 1)));
    }

    public ContentQueryRecipe failStep(String name, String detail, String message) {
        addFailureStep(name, detail, message);
        return this;
    }

    public ContentQueryRecipe filterTypes(final boolean allowShaped, final boolean allowShapeless,
        final boolean allowSmelting) {
        addFilterStep("filterTypes", formatTypes(allowShaped, allowShapeless, allowSmelting),
            (List<RecipeEntry> state) -> QueryStepResult.success(
                filterByTypesList(state, allowShaped, allowShapeless, allowSmelting)));
        return this;
    }

    public ContentQueryRecipe filterShaped() {
        return filterTypes(true, false, false);
    }

    public ContentQueryRecipe filterShapeless() {
        return filterTypes(false, true, false);
    }

    public ContentQueryRecipe filterSmelting() {
        return filterTypes(false, false, true);
    }

    public ContentQueryRecipe filterOutput(final ItemStack output) {
        return filterOutput(output, null);
    }

    public ContentQueryRecipe filterOutput(final ItemStack output, final String detail) {
        String label = detail == null || detail.length() == 0 ? RecipeQueryFormat.stackLabel(output) : detail;
        addFilterStep("filterOutput", label, (List<RecipeEntry> state) -> {
            if (output == null) {
                return QueryStepResult.failure("Query: expected output itemstack.");
            }
            return QueryStepResult.success(filterEntries(state, entry -> {
                ItemStack recipeOutput = RecipeQueryUtils.getRecipeOutput(entry.getRecipe());
                return RecipeQueryUtils.matchesOutput(recipeOutput, output);
            }));
        });
        return this;
    }

    public ContentQueryRecipe filterInput(final ItemStack input) {
        return filterInput(input, null);
    }

    public ContentQueryRecipe filterInput(final ItemStack input, final String detail) {
        String label = detail == null || detail.length() == 0 ? RecipeQueryFormat.stackLabel(input) : detail;
        addFilterStep("filterInput", label, (List<RecipeEntry> state) -> {
            if (input == null) {
                return QueryStepResult.failure("Query: expected input itemstack.");
            }
            return QueryStepResult.success(filterEntries(state,
                entry -> RecipeQueryUtils.matchesInput(entry.getRecipe(), input)));
        });
        return this;
    }

    public ContentQueryRecipe filterOutAndIn(final ItemStack output, final List inputs) {
        return filterOutAndIn(output, inputs, null);
    }

    public ContentQueryRecipe filterOutAndIn(final ItemStack output, final List inputs, final String detail) {
        String label = detail == null || detail.length() == 0
            ? RecipeQueryFormat.stackLabel(output) + ", " + RecipeQueryFormat.formatInputs(inputs)
            : detail;
        addFilterStep("filterOutAndIn", label, (List<RecipeEntry> state) -> {
            if (output == null) {
                return QueryStepResult.failure("Query: expected output itemstack.");
            }
            if (inputs == null || inputs.isEmpty()) {
                return QueryStepResult.failure("Query: filterOutAndIn expects input list.");
            }
            return QueryStepResult.success(filterEntries(state, entry -> {
                Object recipe = entry.getRecipe();
                ItemStack recipeOutput = RecipeQueryUtils.getRecipeOutput(recipe);
                if (!RecipeQueryUtils.matchesOutput(recipeOutput, output)) {
                    return false;
                }
                if (recipe instanceof SmeltingRecipe) {
                    if (inputs.size() != 1) {
                        return false;
                    }
                    SmeltingRecipe smelting = (SmeltingRecipe) recipe;
                    ItemStack inputStack = (ItemStack) inputs.get(0);
                    return smelting.getInputId() == inputStack.itemID;
                }
                return recipe instanceof IRecipe && RecipeQueryUtils.matchesInputs((IRecipe) recipe, inputs);
            }));
        });
        return this;
    }

    public ContentQueryRecipe getShaped(final ShapedQuery query) {
        addSingleStep("getShaped", RecipeQueryFormat.formatGrid(query == null ? null : query.grid,
            query == null ? 0 : query.width, query == null ? 0 : query.height),
            (List<RecipeEntry> state) -> {
            if (query == null) {
                return QueryStepResult.failure("Query: shaped recipe must be a table.");
            }
            Map sourceMap = mapFromList(state);
            Object match = RecipeQueryMatcher.findMatchingShaped(sourceMap, query);
            if (match == null) {
                RecipeQueryFormat.logShapedQueryFailure(LOGGER, sourceMap, query);
                return QueryStepResult.failure("Query: shaped recipe not found.");
            }
            return QueryStepResult.success(wrapSingle(findEntryByRecipe(state, match)));
        });
        return this;
    }

    public ContentQueryRecipe getShapeless(final ShapelessQuery query) {
        addSingleStep("getShapeless", RecipeQueryFormat.formatInputs(query == null ? null : query.inputs),
            (List<RecipeEntry> state) -> {
            if (query == null) {
                return QueryStepResult.failure("Query: shapeless recipe must be a table.");
            }
            Object match = RecipeQueryMatcher.findMatchingShapeless(mapFromList(state), query);
            if (match == null) {
                RecipeQueryFormat.logShapelessQueryFailure(LOGGER, mapFromList(state), query);
                return QueryStepResult.failure("Query: shapeless recipe not found.");
            }
            return QueryStepResult.success(wrapSingle(findEntryByRecipe(state, match)));
        });
        return this;
    }

    public ContentQueryRecipe getSmelting(final SmeltingQuery query) {
        addSingleStep("getSmelting", query == null ? "null" : String.valueOf(query.inputId),
            (List<RecipeEntry> state) -> {
            if (query == null) {
                return QueryStepResult.failure("Query: smelting recipe must be a table.");
            }
            Object match = RecipeQueryMatcher.findMatchingSmelting(mapFromList(state), query);
            if (match == null) {
                RecipeQueryFormat.logSmeltingQueryFailure(LOGGER, query);
                return QueryStepResult.failure("Query: smelting recipe not found.");
            }
            return QueryStepResult.success(wrapSingle(findEntryByRecipe(state, match)));
        });
        return this;
    }

    public ContentQueryRecipe getByName(final String name) {
        addSingleStep("getByName", quote(name), (List<RecipeEntry> state) -> {
            Map sourceMap = mapFromList(state);
            Object recipe = sourceMap.get(name);
            if (recipe == null) {
                recipe = findRecipeByAlternateName(sourceMap, name);
                if (recipe == null) {
                    logRecipeNameFailure(sourceMap, name);
                    return QueryStepResult.failure("Query: recipe not found: " + name);
                }
            }
            RecipeEntry entry = findEntryByKey(state, name);
            if (entry == null) {
                entry = findEntryByRecipe(state, recipe);
            }
            return QueryStepResult.success(wrapSingle(entry));
        });
        return this;
    }

    public ContentQueryRecipe fromHandle(final Object recipe) {
        addSingleStep("fromHandle", null, (List<RecipeEntry> state) -> {
            if (!(recipe instanceof IRecipe) && !(recipe instanceof SmeltingRecipe)) {
                return QueryStepResult.failure("Query: expected a recipe handle.");
            }
            RecipeEntry entry = findEntryByRecipe(state, recipe);
            if (entry == null) {
                return QueryStepResult.failure("Query: recipe handle not present in registry.");
            }
            return QueryStepResult.success(wrapSingle(entry));
        });
        return this;
    }


    private static RecipeEntry findEntryByRecipe(List<RecipeEntry> source, Object recipe) {
        if (source == null) {
            return null;
        }
        for (int i = 0; i < source.size(); i++) {
            RecipeEntry entry = source.get(i);
            if (entry.getRecipe() == recipe) {
                return entry;
            }
        }
        if (recipe == null) {
            return null;
        }
        return new RecipeEntry("query/result", recipe);
    }

    private static RecipeEntry findEntryByKey(List<RecipeEntry> source, String key) {
        if (source == null || key == null) {
            return null;
        }
        for (int i = 0; i < source.size(); i++) {
            RecipeEntry entry = source.get(i);
            if (key.equals(entry.getKey())) {
                return entry;
            }
        }
        return null;
    }

    private static List<RecipeEntry> listFromMap(Map source) {
        List<RecipeEntry> list = new ArrayList<RecipeEntry>();
        if (source == null) {
            return list;
        }
        for (java.util.Iterator it = source.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            list.add(new RecipeEntry((String) entry.getKey(), entry.getValue()));
        }
        return list;
    }

    private static Map mapFromList(List<RecipeEntry> source) {
        Map map = new LinkedHashMap();
        if (source == null) {
            return map;
        }
        for (int i = 0; i < source.size(); i++) {
            RecipeEntry entry = source.get(i);
            map.put(entry.getKey(), entry.getRecipe());
        }
        return map;
    }

    private static String formatTypes(boolean allowShaped, boolean allowShapeless, boolean allowSmelting) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        if (allowShaped) {
            count++;
        }
        if (allowShapeless) {
            count++;
        }
        if (allowSmelting) {
            count++;
        }
        if (count == 1) {
            if (allowShaped) {
                return "\"shaped\"";
            }
            if (allowShapeless) {
                return "\"shapeless\"";
            }
            return "\"smelting\"";
        }
        builder.append("{");
        if (allowShaped) {
            builder.append("\"shaped\"");
        }
        if (allowShapeless) {
            if (builder.length() > 1) {
                builder.append(", ");
            }
            builder.append("\"shapeless\"");
        }
        if (allowSmelting) {
            if (builder.length() > 1) {
                builder.append(", ");
            }
            builder.append("\"smelting\"");
        }
        builder.append("}");
        return builder.toString();
    }

    private static List<RecipeEntry> filterByTypesList(List<RecipeEntry> source, boolean allowShaped,
        boolean allowShapeless, boolean allowSmelting) {
        return filterEntries(source, entry -> {
            Object recipe = entry.getRecipe();
            if (recipe instanceof ShapedRecipes) {
                return allowShaped;
            }
            if (recipe instanceof ShapelessRecipes) {
                return allowShapeless;
            }
            return recipe instanceof SmeltingRecipe && allowSmelting;
        });
    }

    private static List<RecipeEntry> filterEntries(List<RecipeEntry> source, Predicate<RecipeEntry> predicate) {
        List<RecipeEntry> filtered = new ArrayList<RecipeEntry>();
        if (source == null) {
            return filtered;
        }
        for (int i = 0; i < source.size(); i++) {
            RecipeEntry entry = source.get(i);
            if (predicate.test(entry)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private static Object findRecipeByAlternateName(Map recipeMap, String name) {
        if (recipeMap == null || name == null) {
            return null;
        }
        int slash = name.indexOf('/');
        if (slash <= 0 || slash == name.length() - 1) {
            return null;
        }
        String type = name.substring(0, slash);
        String remainder = name.substring(slash + 1);
        int underscore = remainder.lastIndexOf('_');
        if (underscore <= 0 || underscore == remainder.length() - 1) {
            return null;
        }
        String itemToken = remainder.substring(0, underscore);
        String countToken = remainder.substring(underscore + 1);
        String[] candidates = buildRecipeKeyCandidates(type, itemToken, countToken);
        for (int i = 0; i < candidates.length; i++) {
            Object recipe = recipeMap.get(candidates[i]);
            if (recipe != null) {
                return recipe;
            }
        }
        if ("smelting".equals(type)) {
            Object recipe = findSmeltingByOutputName(itemToken, countToken);
            if (recipe != null) {
                return recipe;
            }
        }
        return null;
    }

    private static void logRecipeNameFailure(Map recipeMap, String name) {
        if (recipeMap == null || name == null) {
            return;
        }
        int slash = name.indexOf('/');
        String type = slash > 0 ? name.substring(0, slash) : "";
        StringBuilder builder = new StringBuilder();
        builder.append("Query: recipe name not found. Requested=")
            .append(name)
            .append(" keys=");
        int count = 0;
        for (java.util.Iterator it = recipeMap.keySet().iterator(); it.hasNext();) {
            Object key = it.next();
            if (!(key instanceof String)) {
                continue;
            }
            String keyStr = (String) key;
            if (type.length() > 0 && !keyStr.startsWith(type + "/")) {
                continue;
            }
            if (count > 0) {
                builder.append(", ");
            }
            builder.append(keyStr);
            count++;
            if (count >= 50) {
                builder.append(", ...");
                break;
            }
        }
        LOGGER.warning(builder.toString());
    }

    private static String[] buildRecipeKeyCandidates(String type, String itemToken, String countToken) {
        List keys = new ArrayList();
        keys.add(type + "/" + itemToken + "_" + countToken);
        String noPrefix = stripPrefix(itemToken);
        if (noPrefix != null) {
            keys.add(type + "/" + noPrefix + "_" + countToken);
            if (!noPrefix.equals(itemToken)) {
                keys.add(type + "/item." + noPrefix + "_" + countToken);
                keys.add(type + "/tile." + noPrefix + "_" + countToken);
            }
        }
        Integer resolvedId = resolveItemOrBlockId(itemToken);
        if (resolvedId == null && noPrefix != null) {
            resolvedId = resolveItemOrBlockId(noPrefix);
        }
        if (resolvedId != null) {
            keys.add(type + "/" + resolvedId.intValue() + "_" + countToken);
            String resolvedName = resolveInternalName(resolvedId.intValue());
            if (resolvedName != null) {
                keys.add(type + "/" + resolvedName + "_" + countToken);
            }
        }
        String[] result = new String[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            result[i] = (String) keys.get(i);
        }
        return result;
    }

    private static String stripPrefix(String token) {
        if (token == null) {
            return null;
        }
        if (token.startsWith("item.") || token.startsWith("tile.")) {
            return token.substring(5);
        }
        return token;
    }

    private static Integer resolveItemOrBlockId(String token) {
        if (token == null || token.length() == 0) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(token));
        } catch (NumberFormatException ignored) {
        }
        for (int i = 0; i < net.minecraft.src.Item.itemsList.length; i++) {
            net.minecraft.src.Item item = net.minecraft.src.Item.itemsList[i];
            if (item == null) {
                continue;
            }
            String name = item.getItemName();
            if (token.equals(name)) {
                return Integer.valueOf(i);
            }
            if (token.startsWith("item.") && name != null && token.equals(name)) {
                return Integer.valueOf(i);
            }
            if (!token.startsWith("item.") && name != null && ("item." + token).equals(name)) {
                return Integer.valueOf(i);
            }
        }
        for (int i = 0; i < net.minecraft.src.Block.blocksList.length; i++) {
            net.minecraft.src.Block block = net.minecraft.src.Block.blocksList[i];
            if (block == null) {
                continue;
            }
            String name = block.getBlockName();
            if (token.equals(name)) {
                return Integer.valueOf(i);
            }
            if (!token.startsWith("tile.") && name != null && ("tile." + token).equals(name)) {
                return Integer.valueOf(i);
            }
        }
        return null;
    }

    private static Object findSmeltingByOutputName(String itemToken, String countToken) {
        Integer outputId = resolveItemOrBlockId(itemToken);
        if (outputId == null) {
            String stripped = stripPrefix(itemToken);
            if (stripped != null) {
                outputId = resolveItemOrBlockId(stripped);
            }
        }
        if (outputId == null) {
            return null;
        }
        int outputCount = 1;
        try {
            outputCount = Integer.parseInt(countToken);
        } catch (NumberFormatException ignored) {
        }
        Map smelting = FurnaceRecipes.smelting().getSmeltingList();
        for (java.util.Iterator it = smelting.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            ItemStack output = (ItemStack) entry.getValue();
            if (output == null) {
                continue;
            }
            if (output.itemID != outputId.intValue()) {
                continue;
            }
            if (output.stackSize != outputCount) {
                continue;
            }
            return new SmeltingRecipe(smelting, entry);
        }
        return null;
    }

    private static String resolveInternalName(int id) {
        if (id >= 0 && id < net.minecraft.src.Item.itemsList.length) {
            net.minecraft.src.Item item = net.minecraft.src.Item.itemsList[id];
            if (item != null && item.getItemName() != null) {
                return item.getItemName();
            }
        }
        if (id >= 0 && id < net.minecraft.src.Block.blocksList.length) {
            net.minecraft.src.Block block = net.minecraft.src.Block.blocksList[id];
            if (block != null && block.getBlockName() != null) {
                return block.getBlockName();
            }
        }
        return null;
    }

    public static final class ShapedQuery {
        public final ItemStack output;
        public final int width;
        public final int height;
        public final ItemStack[] grid;

        public ShapedQuery(ItemStack output, int width, int height, ItemStack[] grid) {
            this.output = output;
            this.width = width;
            this.height = height;
            this.grid = grid;
        }
    }

    public static final class ShapelessQuery {
        public final ItemStack output;
        public final List inputs;

        public ShapelessQuery(ItemStack output, List inputs) {
            this.output = output;
            this.inputs = inputs;
        }
    }

    public static final class SmeltingQuery {
        public final int inputId;
        public final ItemStack output;

        public SmeltingQuery(int inputId, ItemStack output) {
            this.inputId = inputId;
            this.output = output;
        }
    }
}
