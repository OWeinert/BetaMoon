package betamoon.content;

/**
 * Validated, expandable content type. Known types are constants, while future
 * systems may introduce another valid type without changing this class.
 */
public final class ContentType {
    public static final ContentType BLOCK = new ContentType("block");
    public static final ContentType ITEM = new ContentType("item");
    public static final ContentType ENTITY = new ContentType("entity");
    public static final ContentType RECIPE = new ContentType("recipe");
    public static final ContentType RECIPE_TYPE = new ContentType("recipe_type");
    public static final ContentType RECIPE_MATCHER = new ContentType("recipe_matcher");
    public static final ContentType MODULE = new ContentType("module");
    public static final ContentType TOOL_MATERIAL = new ContentType("tool_material");
    public static final ContentType ARMOR_MATERIAL = new ContentType("armor_material");
    public static final ContentType TEAM = new ContentType("team");
    public static final ContentType SHARED = new ContentType("shared");

    private final String value;

    private ContentType(String value) {
        this.value = value;
    }

    public static ContentType of(String value) {
        ContentKey.validateType(value, value);
        if (BLOCK.value.equals(value)) {
            return BLOCK;
        }
        if (ITEM.value.equals(value)) {
            return ITEM;
        }
        if (ENTITY.value.equals(value)) {
            return ENTITY;
        }
        if (RECIPE.value.equals(value)) {
            return RECIPE;
        }
        if (RECIPE_TYPE.value.equals(value)) {
            return RECIPE_TYPE;
        }
        if (RECIPE_MATCHER.value.equals(value)) {
            return RECIPE_MATCHER;
        }
        if (MODULE.value.equals(value)) {
            return MODULE;
        }
        if (TOOL_MATERIAL.value.equals(value)) {
            return TOOL_MATERIAL;
        }
        if (ARMOR_MATERIAL.value.equals(value)) {
            return ARMOR_MATERIAL;
        }
        if (TEAM.value.equals(value)) {
            return TEAM;
        }
        if (SHARED.value.equals(value)) {
            return SHARED;
        }
        return new ContentType(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ContentType && value.equals(((ContentType) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
