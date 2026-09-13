package betamoon.recipes;

import betamoon.luaapi.recipe.NativeRecipeDeclaration;
import betamoon.luaapi.resource.RecipeRegistryApi;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.List;
import java.util.Map;
import net.minecraft.src.IRecipe;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ModLoader;
import org.luaj.vm2.LuaValue;

/** Installs validated native recipes and records their reversible ownership. */
public final class NativeRecipeRegistration {
    private NativeRecipeRegistration() {
    }

    public static LuaValue register(NativeRecipeDeclaration definition) {
        switch (definition.kind) {
            case SHAPED:
                ModLoader.AddRecipe(definition.getOutput(), definition.getIngredients());
                return trackLatestCraftingRecipe();
            case SHAPELESS:
                ModLoader.AddShapelessRecipe(definition.getOutput(), definition.getIngredients());
                return trackLatestCraftingRecipe();
            case SMELTING:
                return registerSmelting(definition.inputId, definition.getOutput());
            default:
                throw new IllegalArgumentException("Unsupported native recipe kind: " + definition.kind);
        }
    }

    private static LuaValue registerSmelting(int inputId, ItemStack output) {
        final Map<Integer, ItemStack> smelting = NativeRecipeRegistries.smelting();
        final Integer key = new Integer(inputId);
        final ItemStack previous = smelting.get(key);
        ModLoader.AddSmelting(inputId, output);
        final Object installed = smelting.get(key);
        ScriptResourceTracker.trackOwned(installed);
        final LuaValue reference = RecipeRegistryApi.referenceForSmelting(inputId);
        NativeSmeltingRegistrations.track(inputId, previous, reference);
        return reference;
    }

    private static LuaValue trackLatestCraftingRecipe() {
        final List<IRecipe> recipes = NativeRecipeRegistries.crafting();
        if (recipes == null || recipes.isEmpty()) {
            return LuaValue.NIL;
        }
        final IRecipe recipe = recipes.get(recipes.size() - 1);
        ScriptResourceTracker.trackOwned(recipe);
        final LuaValue reference = RecipeRegistryApi.referenceFor(recipe);
        ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
            public void run() {
                RecipeRegistryApi.retire(reference);
                recipes.remove(recipe);
            }
        });
        return reference;
    }
}
