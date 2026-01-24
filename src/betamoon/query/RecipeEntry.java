package betamoon.query;

public final class RecipeEntry {
    private final String key;
    private final Object recipe;

    public RecipeEntry(String key, Object recipe) {
        this.key = key;
        this.recipe = recipe;
    }

    public String getKey() {
        return key;
    }

    public Object getRecipe() {
        return recipe;
    }
}
