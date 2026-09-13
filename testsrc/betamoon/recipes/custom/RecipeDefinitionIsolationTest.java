package betamoon.recipes.custom;

import java.util.Collections;
import java.util.Map;
import net.minecraft.src.Block;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Verifies compiled recipe snapshots independently of mutable Lua and Java
 * views.
 */
public final class RecipeDefinitionIsolationTest {
    private RecipeDefinitionIsolationTest() {
    }

    public static void run() {
        require(Block.stone != null, "Vanilla item definitions must be available");
        Globals lua = JsePlatform.standardGlobals();
        LuaValue schema = lua.load("return {name='quality:isolation', ingredients={input={}}, outputs={output={}}, "
                + "data={duration={type='integer', default=20}}, context={heat={type='integer'}}}").call();
        RecipeTypes.Type type = new RecipeTypes.Type(schema, "quality.lua", false);
        LuaValue declaration = lua.load("return {input={item=1, count=2, remainder={output='output', "
                + "stack={id=1, count=1}}}, output={id=1, count=1}, conditions={heat={min=5}}}").call();
        RecipeDefinition recipe = new RecipeDefinition(type, declaration, "recipe");
        String fingerprint = recipe.fingerprint;
        Map<String, ItemStack> inputs = Collections.singletonMap("input", new ItemStack(1, 2, 0));

        declaration.get("conditions").get("heat").set("min", 100);
        recipe.getConditions().get("heat").set("min", 100);
        LuaValue context = lua.load("return {heat=10}").call();
        require(recipe.matchesContext(context), "Condition snapshots must not change matching");
        recipe.getValue().get("ingredients").get("input").set("count", 100);
        recipe.getData().set("duration", 100);
        require(recipe.getData().get("duration").toint() == 20, "Recipe data must remain independent");

        recipe.getOutput("output").stackSize = 64;
        recipe.getOutputs().get("output").stackSize = 64;
        RecipeDefinition.Ingredient ingredient = recipe.ingredients.get("input");
        ingredient.getAlternatives().get(0).itemID = 2;
        ingredient.getRemainder().stackSize = 64;
        require(recipe.matches(inputs), "Alternative snapshots must not change ingredient matching");
        require(recipe.getOutput("output").stackSize == 1 && ingredient.getRemainder().stackSize == 1,
                "Output and remainder snapshots must not change production quantities");
        require(fingerprint.equals(RecipeValues.fingerprint(recipe.getValue())),
                "Definition fingerprint must continue to describe its effective values");

        schema.get("data").get("duration").set("default", 100);
        type.data.get("duration").getSchema().set("default", 100);
        type.getDefinition().get("data").get("duration").set("default", 100);
        require(type.data.get("duration").getSchema().get("default").toint() == 20,
                "Schema getters and constructor inputs must not mutate retained type defaults");
        expectReadOnly(() -> recipe.ingredients.clear());
        expectReadOnly(() -> recipe.getOutputs().clear());
        expectReadOnly(() -> ingredient.getAlternatives().clear());
        expectReadOnly(() -> type.ingredients.clear());
        expectReadOnly(() -> type.outputs.clear());
        expectReadOnly(() -> type.data.clear());
        expectReadOnly(() -> type.context.clear());
    }

    private static void expectReadOnly(Runnable mutation) {
        try {
            mutation.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Compiled recipe view allowed mutation");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
