package betamoon.query;

import betamoon.recipes.SmeltingRecipe;
import java.util.List;
import java.util.Map;
import net.minecraft.src.CraftingManager;
import net.minecraft.src.FurnaceRecipes;
import net.minecraft.src.InventoryCrafting;
import net.minecraft.src.IRecipe;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ShapedRecipes;
import net.minecraft.src.ShapelessRecipes;

final class RecipeQueryMatcher {
    private RecipeQueryMatcher() {
    }

    static Object findMatchingShaped(Map source, ContentQueryRecipe.ShapedQuery query) {
        List recipeList = CraftingManager.getInstance().getRecipeList();
        Object match = findMatchingShapedInList(recipeList, query);
        if (match != null) {
            return match;
        }
        for (java.util.Iterator it = source.values().iterator(); it.hasNext();) {
            Object recipe = it.next();
            if (!(recipe instanceof ShapedRecipes)) {
                continue;
            }
            ShapedRecipes shaped = (ShapedRecipes) recipe;
            ItemStack output = shaped.getRecipeOutput();
            if (!RecipeQueryUtils.matchesOutput(output, query.output)) {
                continue;
            }
            if (matchesUsingGrid(shaped, query)) {
                return recipe;
            }
            ItemStack[] items = RecipeQueryUtils.getShapedInputs(shaped);
            if (items == null) {
                continue;
            }
            int[] dims = RecipeQueryUtils.getShapedDimensions(shaped, items.length);
            int actualWidth = dims != null ? dims[0] : RecipeQueryUtils.inferGridWidth(items.length);
            int actualHeight = dims != null ? dims[1] : RecipeQueryUtils.inferGridHeight(items.length, actualWidth);
            if (actualWidth <= 0 || actualHeight <= 0) {
                if (items.length != query.grid.length) {
                    continue;
                }
                actualWidth = query.width;
                actualHeight = query.height;
            }
            if (matchesShapedGrid(items, actualWidth, actualHeight, query.grid, query.width, query.height)) {
                return recipe;
            }
        }
        return null;
    }

    static Object findMatchingShapeless(Map source, ContentQueryRecipe.ShapelessQuery query) {
        List recipeList = CraftingManager.getInstance().getRecipeList();
        Object match = findMatchingShapelessInList(recipeList, query);
        if (match != null) {
            return match;
        }
        for (java.util.Iterator it = source.values().iterator(); it.hasNext();) {
            Object recipe = it.next();
            if (!(recipe instanceof ShapelessRecipes)) {
                continue;
            }
            ShapelessRecipes shapeless = (ShapelessRecipes) recipe;
            ItemStack output = shapeless.getRecipeOutput();
            if (!RecipeQueryUtils.matchesOutput(output, query.output)) {
                continue;
            }
            if (matchesShapelessUsingGrid(shapeless, query)) {
                return recipe;
            }
            List recipeInputs = RecipeQueryUtils.collectRecipeInputs(shapeless);
            if (recipeInputs == null || recipeInputs.size() != query.inputs.size()) {
                continue;
            }
            if (RecipeQueryUtils.matchesInputList(recipeInputs, query.inputs)) {
                return recipe;
            }
        }
        return null;
    }

    static Object findMatchingSmelting(Map source, ContentQueryRecipe.SmeltingQuery query) {
        Object liveMatch = findMatchingSmeltingInList(query);
        if (liveMatch != null) {
            return liveMatch;
        }
        for (java.util.Iterator it = source.values().iterator(); it.hasNext();) {
            Object recipe = it.next();
            if (!(recipe instanceof SmeltingRecipe)) {
                continue;
            }
            SmeltingRecipe smelting = (SmeltingRecipe) recipe;
            if (smelting.getInputId() != query.inputId) {
                continue;
            }
            if (!RecipeQueryUtils.matchesSmeltingOutput(smelting.getRecipeOutput(), query.output)) {
                continue;
            }
            return recipe;
        }
        return null;
    }

    private static Object findMatchingShapedInList(List recipeList, ContentQueryRecipe.ShapedQuery query) {
        if (recipeList == null) {
            return null;
        }
        for (int i = 0; i < recipeList.size(); i++) {
            Object recipe = recipeList.get(i);
            if (!(recipe instanceof ShapedRecipes)) {
                continue;
            }
            ShapedRecipes shaped = (ShapedRecipes) recipe;
            ItemStack output = shaped.getRecipeOutput();
            if (!RecipeQueryUtils.matchesOutput(output, query.output)) {
                continue;
            }
            if (matchesUsingGrid(shaped, query)) {
                return recipe;
            }
        }
        return null;
    }

    private static Object findMatchingShapelessInList(List recipeList, ContentQueryRecipe.ShapelessQuery query) {
        if (recipeList == null || query == null) {
            return null;
        }
        for (int i = 0; i < recipeList.size(); i++) {
            Object recipe = recipeList.get(i);
            if (!(recipe instanceof ShapelessRecipes)) {
                continue;
            }
            ShapelessRecipes shapeless = (ShapelessRecipes) recipe;
            ItemStack output = shapeless.getRecipeOutput();
            if (!RecipeQueryUtils.matchesOutput(output, query.output)) {
                continue;
            }
            if (matchesShapelessUsingGrid(shapeless, query)) {
                return recipe;
            }
        }
        return null;
    }

    private static Object findMatchingSmeltingInList(ContentQueryRecipe.SmeltingQuery query) {
        if (query == null) {
            return null;
        }
        Map smelting = FurnaceRecipes.smelting().getSmeltingList();
        for (java.util.Iterator it = smelting.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            Object key = entry.getKey();
            if (!(key instanceof Integer)) {
                continue;
            }
            int inputId = ((Integer) key).intValue();
            if (inputId != query.inputId) {
                continue;
            }
            ItemStack output = (ItemStack) entry.getValue();
            if (RecipeQueryUtils.matchesSmeltingOutput(output, query.output)) {
                return new SmeltingRecipe(smelting, entry);
            }
        }
        return null;
    }

    private static boolean matchesUsingGrid(IRecipe recipe, ContentQueryRecipe.ShapedQuery query) {
        InventoryCrafting grid = buildCraftingGrid(query, query.width, query.height, false);
        if (grid != null && safeMatches(recipe, grid)) {
            return true;
        }
        InventoryCrafting padded = buildCraftingGrid(query, 3, 3, true);
        return padded != null && safeMatches(recipe, padded);
    }

    private static boolean matchesShapelessUsingGrid(IRecipe recipe, ContentQueryRecipe.ShapelessQuery query) {
        InventoryCrafting grid = buildCraftingGrid(query);
        return grid != null && safeMatches(recipe, grid);
    }

    private static boolean safeMatches(IRecipe recipe, InventoryCrafting grid) {
        try {
            return recipe.matches(grid);
        } catch (Throwable t) {
            return false;
        }
    }

    private static InventoryCrafting buildCraftingGrid(ContentQueryRecipe.ShapedQuery query, int width, int height,
        boolean padTopLeft) {
        if (query == null || query.grid == null || width <= 0 || height <= 0) {
            return null;
        }
        if (width < query.width || height < query.height) {
            return null;
        }
        InventoryCrafting craftingGrid = new InventoryCrafting(null, width, height);
        ItemStack[] stacks = new ItemStack[width * height];
        for (int y = 0; y < query.height; y++) {
            for (int x = 0; x < query.width; x++) {
                int sourceIndex = x + y * query.width;
                int targetIndex = x + y * width;
                if (padTopLeft && (x >= width || y >= height)) {
                    continue;
                }
                if (sourceIndex >= 0 && sourceIndex < query.grid.length
                    && targetIndex >= 0 && targetIndex < stacks.length) {
                    stacks[targetIndex] = query.grid[sourceIndex];
                }
            }
        }
        if (!setInventoryStacks(craftingGrid, stacks)) {
            return null;
        }
        return craftingGrid;
    }

    private static InventoryCrafting buildCraftingGrid(ContentQueryRecipe.ShapelessQuery query) {
        if (query == null || query.inputs == null || query.inputs.isEmpty() || query.inputs.size() > 9) {
            return null;
        }
        InventoryCrafting craftingGrid = new InventoryCrafting(null, 3, 3);
        ItemStack[] stacks = new ItemStack[9];
        for (int i = 0; i < query.inputs.size() && i < stacks.length; i++) {
            stacks[i] = (ItemStack) query.inputs.get(i);
        }
        if (!setInventoryStacks(craftingGrid, stacks)) {
            return null;
        }
        return craftingGrid;
    }

    private static boolean setInventoryStacks(InventoryCrafting grid, ItemStack[] stacks) {
        try {
            java.lang.reflect.Field[] fields = InventoryCrafting.class.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                java.lang.reflect.Field field = fields[i];
                if (field.getType() != ItemStack[].class) {
                    continue;
                }
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }
                field.set(grid, stacks);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private static boolean matchesShapedGrid(ItemStack[] actual, int actualWidth, int actualHeight,
        ItemStack[] expected, int expectedWidth, int expectedHeight) {
        if (actual == null || expected == null) {
            return false;
        }
        Grid actualGrid = trimGrid(actual, actualWidth, actualHeight);
        Grid expectedGrid = trimGrid(expected, expectedWidth, expectedHeight);
        if (actualGrid.width != expectedGrid.width || actualGrid.height != expectedGrid.height) {
            return false;
        }
        ItemStack[] actualItems = actualGrid.items;
        ItemStack[] expectedItems = expectedGrid.items;
        if (actualItems.length != expectedItems.length) {
            return false;
        }
        for (int i = 0; i < actualItems.length; i++) {
            ItemStack expectedStack = expectedItems[i];
            ItemStack actualStack = actualItems[i];
            boolean expectedEmpty = isEmptyStack(expectedStack);
            boolean actualEmpty = isEmptyStack(actualStack);
            if (expectedEmpty && actualEmpty) {
                continue;
            }
            if (expectedEmpty || actualEmpty) {
                return false;
            }
            if (!RecipeQueryUtils.matchesStack(actualStack, expectedStack)) {
                return false;
            }
        }
        return true;
    }

    private static final class Grid {
        private final int width;
        private final int height;
        private final ItemStack[] items;

        private Grid(int width, int height, ItemStack[] items) {
            this.width = width;
            this.height = height;
            this.items = items;
        }
    }

    private static Grid trimGrid(ItemStack[] grid, int width, int height) {
        if (grid == null || width <= 0 || height <= 0) {
            return new Grid(0, 0, new ItemStack[0]);
        }
        int top = 0;
        int bottom = height - 1;
        int left = 0;
        int right = width - 1;
        while (top <= bottom && isRowEmpty(grid, width, top)) {
            top++;
        }
        while (bottom >= top && isRowEmpty(grid, width, bottom)) {
            bottom--;
        }
        while (left <= right && isColEmpty(grid, width, height, left)) {
            left++;
        }
        while (right >= left && isColEmpty(grid, width, height, right)) {
            right--;
        }
        if (top > bottom || left > right) {
            return new Grid(0, 0, new ItemStack[0]);
        }
        int newWidth = right - left + 1;
        int newHeight = bottom - top + 1;
        ItemStack[] trimmed = new ItemStack[newWidth * newHeight];
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                trimmed[x + y * newWidth] = grid[(left + x) + (top + y) * width];
            }
        }
        return new Grid(newWidth, newHeight, trimmed);
    }

    private static boolean isRowEmpty(ItemStack[] grid, int width, int row) {
        int offset = row * width;
        for (int x = 0; x < width; x++) {
            if (!isEmptyStack(grid[offset + x])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isColEmpty(ItemStack[] grid, int width, int height, int col) {
        for (int y = 0; y < height; y++) {
            if (!isEmptyStack(grid[col + y * width])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isEmptyStack(ItemStack stack) {
        return stack == null || stack.itemID == 0;
    }
}
