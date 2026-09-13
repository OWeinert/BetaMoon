package betamoon.recipes;

import net.minecraft.src.ShapedRecipes;
import net.minecraft.src.ShapelessRecipes;

/**
 * Native recipe families understood by BetaMoon inspection and Lua references.
 */
public enum NativeRecipeKind {
    SHAPED("shaped"), SHAPELESS("shapeless"), SMELTING("smelting"), UNKNOWN("unknown");

    private final String luaName;

    NativeRecipeKind(String luaName) {
        this.luaName = luaName;
    }

    public String getLuaName() {
        return luaName;
    }

    public static NativeRecipeKind of(Object recipe) {
        if (recipe instanceof ShapedRecipes) {
            return SHAPED;
        }
        if (recipe instanceof ShapelessRecipes) {
            return SHAPELESS;
        }
        if (recipe instanceof SmeltingRecipe) {
            return SMELTING;
        }
        return UNKNOWN;
    }
}
