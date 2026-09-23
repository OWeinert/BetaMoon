package betamoon.debug;

import betamoon.luaapi.BetaMoonModule;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.recipes.RecipeModificationHandler;
import betamoon.recipes.custom.CustomRecipes;
import betamoon.recipes.custom.RecipeTypes;
import java.lang.reflect.Method;
import net.minecraft.src.Block;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Verifies the human-readable recipe and recipe-type debug formats.
 */
public final class DebugExportFormatterTest {
    private static final String OWNER = "debug_export_formatter.lua";

    private DebugExportFormatterTest() {
    }

    public static void main(String[] args) throws Exception {
        require(Block.stone != null, "Vanilla items were not initialized");
        Method setOwner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        setOwner.setAccessible(true);
        try {
            registerFixtures(setOwner);
            verifyRecipeFormatting();
            verifyRecipeTypeFormatting();
            verifyBuiltInTypeFormatting();
            RecipeModificationHandler.createRecipeMap();
            DebugExportSystemTest.verifyLiveCatalogRun();
        } finally {
            ScriptResourceTracker.unload(OWNER);
            setOwner.invoke(null, new Object[]{null});
        }
        DebugExportSystemTest.main(new String[0]);
        System.out.println("Recipe and recipe-type debug formatting checks passed.");
    }

    private static void registerFixtures(Method setOwner) throws Exception {
        setOwner.invoke(null, OWNER);
        Globals globals = JsePlatform.standardGlobals();
        new BetaMoonModule().call(LuaValue.NIL, globals);
        globals.load("local alloying = betamoon.recipeTypes:add({\n" + "    name = 'debugformat:alloying',\n"
                + "    displayName = 'Debug Alloying',\n" + "    ingredients = {\n"
                + "        base = { type = 'item' },\n" + "        additive = { type = 'item' },\n"
                + "        mold = { type = 'item', optional = true, consume = false }\n" + "    },\n"
                + "    outputs = {\n" + "        result = { type = 'item' },\n"
                + "        slag = { type = 'item', optional = true }\n"
                + "    },\n" + "    primaryOutput = 'result',\n"
                + "    data = {\n" + "        duration = { type = 'integer', default = 160, min = 1 },\n"
                + "        label = { type = 'string' }\n" + "    },\n" + "    context = {\n"
                + "        heat = { type = 'number', min = 0, max = 10 },\n"
                + "        powered = { type = 'boolean' }\n" + "    }\n" + "})\n" + "betamoon.recipes:add({\n"
                + "    key = 'debugformat:first', type = alloying,\n" + "    ingredients = {\n"
                + "        base = { anyOf = { 1, 4 }, count = 4,\n"
                + "            remainder = { output = 'slag', stack = { id = 4, count = 1 } } },\n"
                + "        additive = { item = 263, damage = 'any' },\n" + "        mold = 280\n" + "    },\n"
                + "    outputs = { result = { id = 266, count = 3 }, slag = 4 },\n"
                + "    data = { duration = 200, label = 'hot' },\n"
                + "    conditions = { heat = { min = 2, max = 5 }, powered = true }\n" + "})\n"
                + "betamoon.recipes:add({\n" + "    key = 'debugformat:second', type = alloying,\n"
                + "    ingredients = { base = 1, additive = 263 },\n" + "    output = { id = 266, count = 3 },\n"
                + "    data = { duration = 160, label = 'cold' },\n" + "    priority = 5, enabled = false\n" + "})",
                "debug export fixtures").call();
        globals.load("local fabrication = betamoon.recipeTypes:add({\n" + "    name = 'debugformat:fabrication',\n"
                + "    ingredients = {\n" + "        materials = { type = 'item_pool', allowExtra = true },\n"
                + "        layout = { type = 'item_grid', width = 2, height = 2, placement = 'fixed',\n"
                + "            transformations = { 'mirror_horizontal' }, emptyCells = 'ignored' }\n" + "    },\n"
                + "    outputs = { result = {}, byproducts = { type = 'item_output_pool', optional = true } },\n"
                + "    primaryOutput = 'result'\n" + "})\n"
                + "betamoon.recipes:add({ key = 'debugformat:fabrication_recipe', type = fabrication,\n"
                + "    ingredients = { materials = { 263, { item = 265, count = 2 } },\n"
                + "        layout = { pattern = { 'X ', 'XX' }, key = { X = 4 } } },\n"
                + "    outputs = { result = 266, byproducts = { 3, { id = 4, count = 2 } } }\n" + "})",
                "generalized debug export fixtures").call();
    }

    private static void verifyRecipeFormatting() {
        CustomRecipes.Entry first = requireRecipe("debugformat:first");
        CustomRecipes.Entry second = requireRecipe("debugformat:second");
        DebugCustomRecipeFormatter formatter = new DebugCustomRecipeFormatter();
        String firstText = formatter.format(first);
        String secondText = formatter.format(second);

        require(firstText.startsWith("debugformat:alloying/item.ingotGold_3 : "),
                "Custom recipe heading does not identify its type and primary output");
        require(firstText.contains("[item = [item.ingotGold / 266 /"),
                "Primary output does not use the vanilla stack representation");
        require(firstText.contains("\"base\": one of"), "Ingredient alternatives are not labeled");
        require(firstText.contains(" / 263:* / "), "Wildcard ingredient damage is not explicit");
        require(firstText.contains("remainder -> \"slag\":"), "Ingredient remainder is not described");
        require(firstText.contains("additional outputs:\n        \"slag\":"),
                "Additional output is not separated from the primary output");
        require(firstText.contains("conditions:\n        heat = { min = 2, max = 5 }"),
                "Conditions are not formatted as readable values");
        require(firstText.contains("key = \"debugformat:first\""), "Recipe key is missing");
        require(firstText.contains("owner = \"" + OWNER + "\""), "Recipe owner is missing");
        require(!firstText.contains("[4:"), "Internal canonical Lua serialization leaked into the export");
        require(!firstText.contains("priority = 0") && !firstText.contains("enabled = true"),
                "Default recipe state should be omitted");

        require(secondText.startsWith("debugformat:alloying/item.ingotGold_3_1 : "),
                "Duplicate human-readable headings are not disambiguated");
        require(secondText.contains("priority = 5") && secondText.contains("enabled = false"),
                "Non-default recipe state is missing");

        String fabrication = formatter.format(requireRecipe("debugformat:fabrication_recipe"));
        require(fabrication.contains("\"materials\":\n            [1]:"),
                "Item-pool requirements are not formatted as a Lua-style list");
        require(fabrication.contains("\"layout\":\n            pattern = {\n                \"X \""),
                "Grid patterns are not formatted as Lua-style rows");
        require(fabrication.contains("key:\n                [\"X\"]:"),
                "Grid ingredient keys are not formatted by pattern character");
        require(fabrication.contains("\"byproducts\":\n            [1]:") && fabrication.contains("            [2]:"),
                "Pooled outputs are not formatted as a readable list");
    }

    private static void verifyRecipeTypeFormatting() {
        RecipeTypes.Type type = RecipeTypes.get(LuaValue.valueOf("debugformat:alloying"), true);
        String text = DebugRecipeTypeFormatter.format(type);
        require(text.startsWith("debugformat:alloying : \"Debug Alloying\""),
                "Recipe type heading is missing its display name");
        require(text.contains("\"mold\": item [optional, retained]"), "Ingredient role semantics are incomplete");
        require(text.contains("\"result\": item [required, primary]"), "Primary output is not marked on its role");
        require(count(text, "primary") == 1, "Primary output is marked more than once");
        require(!text.contains("primary output"), "A duplicate primary-output field was emitted");
        require(text.contains("duration: integer [default = 160, min = 1]"),
                "Data defaults and bounds are incomplete");
        require(text.contains("label: string [required]"), "Required data field is not marked");
        require(text.contains("heat: number [min = 0, max = 10]"), "Context bounds are incomplete");
        require(!text.contains("built-in"), "Implementation origin leaked into the type export");

        String fabrication = formatType("debugformat:fabrication");
        require(fabrication.contains("\"materials\": { item, ... } [required, consumed, allow extra = true]"),
                "Item-pool role options are incomplete");
        require(fabrication.contains("{ pattern = { \"row\", ... }, key = { [\"X\"] = item, ... } }"),
                "Item-grid role syntax is not Lua-shaped");
        require(fabrication.contains("grid = 2x2, placement = fixed, empty cells = ignored"),
                "Item-grid role constraints are incomplete");
        require(fabrication.contains("\"byproducts\": { item, ... } [optional]"),
                "Pooled output role syntax is incomplete");
    }

    private static void verifyBuiltInTypeFormatting() {
        String shaped = formatType("shaped");
        require(shaped.contains("inputs:\n        \"pattern\": { \"row\", ... } [required, 2x2 or 3x3 grid]\n"
                + "        \"ingredients\": { [\"X\"] = item, ... } [required, one entry per pattern character]"),
                "Shaped type does not describe its required pattern and character map");
        require(count(shaped, "primary") == 1, "Shaped primary output is not marked exactly once");

        String shapeless = formatType("shapeless");
        require(shapeless.contains("inputs:\n        \"ingredients\": { item, ... } [required, 1 to 9 entries]"),
                "Shapeless type does not describe its required item list");
        require(count(shapeless, "primary") == 1, "Shapeless primary output is not marked exactly once");
        require(!shaped.contains("list<") && !shaped.contains("map<") && !shapeless.contains("list<"),
                "Generic collection notation leaked into the recipe type export");

        String text = formatType("smelting");
        require(text.contains("inputs:\n        \"input\": item [required]"),
                "Smelting type does not describe its required item input");
        require(text.startsWith("minecraft:smelting : \"smelting\""), "Built-in type is not exported");
        require(text.contains("owner = \"minecraft\""), "Built-in type owner is missing");
        require(!text.contains("built-in"), "Built-in type has a redundant origin marker");
        require(count(text, "primary") == 1, "Built-in primary output is not marked exactly once");
    }

    private static String formatType(String name) {
        RecipeTypes.Type type = RecipeTypes.get(LuaValue.valueOf(name), true);
        return DebugRecipeTypeFormatter.format(type);
    }

    private static CustomRecipes.Entry requireRecipe(String key) {
        CustomRecipes.Entry entry = CustomRecipes.get(key);
        if (entry == null) {
            throw new AssertionError("Missing test recipe " + key);
        }
        return entry;
    }

    private static int count(String text, String needle) {
        int result = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            result++;
            offset += needle.length();
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
