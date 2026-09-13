package betamoon.luaapi.recipe;

import betamoon.recipes.NativeRecipeRegistration;
import betamoon.recipes.custom.CustomRecipes;
import betamoon.recipes.custom.RecipeTypes;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/**
 * Installs declarative recipe registration for native and custom recipe types.
 */
public final class RecipeApi {
    private RecipeApi() {
    }

    public static void attach(LuaTable module) {
        LuaTable recipes = module.get("recipes").checktable();
        recipes.set("add", new AddRecipe(recipes));
    }

    private static final class AddRecipe extends VarArgFunction {
        private final LuaTable service;

        private AddRecipe(LuaTable service) {
            this.service = service;
        }

        @Override
        public Varargs invoke(Varargs args) {
            LuaValue definition = args.arg(args.arg1() == service ? 2 : 1);
            if (!definition.istable()) {
                throw new LuaError("recipes:add expects a definition table.");
            }
            RecipeTypes.Type type = RecipeTypes.get(definition.get("type"), true);
            if (!type.builtin) {
                return CustomRecipes.add(definition).reference;
            }
            return NativeRecipeRegistration.register(parseNative(type.name, definition));
        }
    }

    private static NativeRecipeDeclaration parseNative(String type, LuaValue definition) {
        LuaValue output = required(definition, "output");
        switch (type) {
            case "minecraft:shaped":
                LuaValue pattern = required(definition, "pattern");
                LuaValue ingredients = definition.get("ingredients");
                if (ingredients.isnil()) {
                    ingredients = required(definition, "key");
                }
                return NativeRecipeDeclaration.shaped(output, pattern, ingredients);
            case "minecraft:shapeless":
                return NativeRecipeDeclaration.shapeless(output, required(definition, "ingredients"));
            case "minecraft:smelting":
                return NativeRecipeDeclaration.smelting(required(definition, "input"), output);
            default:
                throw new LuaError("Unknown recipe type: " + type);
        }
    }
}
