package betamoon.luaapi.resource;

import betamoon.luaapi.LuaApiUtils;
import betamoon.recipes.RecipeModificationHandler;
import betamoon.recipes.SmeltingRecipe;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import betamoon.luamodloader.ScriptResourceTracker;
import net.minecraft.src.CraftingManager;
import net.minecraft.src.IRecipe;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ShapedRecipes;
import net.minecraft.src.ShapelessRecipes;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Adds direct recipe lookup, criteria queries, and reversible recipe overrides. */
public final class RecipeRegistryApi {
    private RecipeRegistryApi() {
    }

    public static void attach(LuaTable root) {
        LuaTable recipes = (LuaTable) root.get("recipes");
        recipes.set("get", new Get(recipes, true));
        recipes.set("getRequired", new Get(recipes, false));
        recipes.set("find", new Find(recipes, 0));
        recipes.set("first", new Find(recipes, 1));
        recipes.set("one", new Find(recipes, 2));
    }

    private static final class Get extends VarArgFunction {
        private final LuaTable service;
        private final boolean optional;
        private Get(LuaTable service, boolean optional) { this.service = service; this.optional = optional; }
        public Varargs invoke(Varargs args) {
            String key = args.arg(args.arg1() == service ? 2 : 1).checkjstring();
            RecipeModificationHandler.createRecipeMap();
            Object recipe = RecipeModificationHandler.getRecipeByKey(key);
            if (recipe == null && !optional) throw new LuaError("Recipe '" + key + "' was not found.");
            return recipe == null ? LuaValue.NIL : new RecipeReference(key, recipe);
        }
    }

    private static final class Find extends VarArgFunction {
        private final LuaTable service;
        private final int mode;
        private Find(LuaTable service, int mode) { this.service = service; this.mode = mode; }
        public Varargs invoke(Varargs args) {
            LuaValue criteria = args.arg(args.arg1() == service ? 2 : 1);
            if (criteria.isnil()) criteria = new LuaTable();
            if (!criteria.istable()) throw new LuaError("recipe criteria must be a table.");
            List values = findRecipes(criteria);
            if (mode == 1) return values.isEmpty() ? LuaValue.NIL : (LuaValue) values.get(0);
            if (mode == 2) {
                if (values.isEmpty()) return LuaValue.NIL;
                if (values.size() != 1) throw new LuaError("Expected exactly one recipe, found " + values.size() + ".");
                return (LuaValue) values.get(0);
            }
            return new LuaResultList(values, new LuaResultList.BulkOverride() {
                public LuaValue apply(LuaValue reference, LuaValue definition, int index) {
                    return ((RecipeReference) reference).override(definition);
                }
            });
        }
    }

    private static List findRecipes(LuaValue criteria) {
        RecipeModificationHandler.createRecipeMap();
        Map all = RecipeModificationHandler.getRecipeMap();
        Set outputMatches = matchingRecipes(criteria.get("output"), true);
        LuaValue inputValue = criteria.get("input");
        Set inputMatches = matchingRecipes(inputValue, false);
        List out = new ArrayList();
        for (Object object : all.entrySet()) {
            Map.Entry entry = (Map.Entry) object;
            Object recipe = entry.getValue();
            if (!matchesType(recipe, criteria.get("type"))) continue;
            if (outputMatches != null && !outputMatches.contains(recipe)) continue;
            if (inputMatches != null && !inputMatches.contains(recipe)) continue;
            out.add(new RecipeReference((String) entry.getKey(), recipe));
        }
        return out;
    }

    private static Set matchingRecipes(LuaValue stackValue, boolean output) {
        if (stackValue.isnil()) return null;
        ItemStack stack = LuaApiUtils.readItemStack(stackValue, output, output ? "output" : "input");
        Map matches = output ? RecipeModificationHandler.filterRecipesByOutput(stack)
            : RecipeModificationHandler.filterRecipesByInput(stack);
        return new HashSet(matches.values());
    }

    private static boolean matchesType(Object recipe, LuaValue expected) {
        if (expected.isnil()) return true;
        String actual = typeOf(recipe);
        if (expected.isstring()) return actual.equals(expected.tojstring().toLowerCase());
        if (!expected.istable()) throw new LuaError("recipe type must be a string or list.");
        for (int i = 1; i <= expected.length(); i++) {
            if (actual.equals(expected.get(i).checkjstring().toLowerCase())) return true;
        }
        return false;
    }

    private static String typeOf(Object recipe) {
        if (recipe instanceof ShapedRecipes) return "shaped";
        if (recipe instanceof ShapelessRecipes) return "shapeless";
        if (recipe instanceof SmeltingRecipe) return "smelting";
        return "unknown";
    }

    /** Stable Lua view over one concrete recipe registration. */
    private static final class RecipeReference extends LuaTable {
        private final String key;
        private final Object recipe;

        private RecipeReference(String key, Object recipe) {
            this.key = key;
            this.recipe = recipe;
            set("key", LuaValue.valueOf(key));
            set("type", LuaValue.valueOf(typeOf(recipe)));
            set("output", stackTable(((IRecipe) recipe).getRecipeOutput()));
            String owner = ScriptResourceTracker.findOwner(recipe instanceof SmeltingRecipe
                ? ((SmeltingRecipe) recipe).getOutput() : recipe);
            set("owner", LuaValue.valueOf(owner == null ? "minecraft" : owner));
            set("exists", LuaValue.TRUE);
            set("override", new Apply(this));
            set("disable", new Disable(this));
        }

        private LuaValue override(LuaValue definition) {
            if (!definition.istable()) throw new LuaError("recipe override expects a table.");
            String inactiveReason = checkConditions(definition.get("when"));
            if (inactiveReason != null) {
                LuaTable inactive = new LuaTable();
                inactive.set("target", this);
                inactive.set("active", LuaValue.FALSE);
                inactive.set("reason", LuaValue.valueOf(inactiveReason));
                return inactive;
            }
            LuaValue changes = definition.get("changes");
            if (changes.isnil()) changes = definition;
            int priority = definition.get("priority").optint(0);
            List layers = new ArrayList();
            LuaValue output = changes.get("output");
            if (!output.isnil()) {
                final ItemStack stack = LuaApiUtils.readItemStack(output, true, "recipe output");
                layers.add(OverrideManager.apply("recipe:" + key, recipe, "output", stack, priority,
                    outputAdapter(key, recipe)));
                set("output", stackTable(stack));
            }
            LuaValue enabled = changes.get("enabled");
            if (!enabled.isnil()) {
                layers.add(OverrideManager.apply("recipe:" + key, recipe, "enabled",
                    Boolean.valueOf(enabled.toboolean()), priority, enabledAdapter(recipe)));
            }
            LuaTable handle = new LuaTable();
            handle.set("target", this);
            handle.set("active", LuaValue.TRUE);
            handle.set("remove", new Remove(layers, handle));
            return handle;
        }

        /** Evaluates the intentionally small declarative condition language. */
        private String checkConditions(LuaValue when) {
            if (when.isnil()) return null;
            if (!when.istable()) throw new LuaError("recipe override when must be a table.");
            LuaValue owner = when.get("owner");
            if (!owner.isnil() && !owner.tojstring().equals(get("owner").tojstring())) {
                return "target owner is '" + get("owner").tojstring() + "', expected '" + owner.tojstring() + "'";
            }
            LuaValue type = when.get("type");
            if (!type.isnil() && !type.tojstring().equalsIgnoreCase(get("type").tojstring())) {
                return "recipe type did not match the expected value";
            }
            LuaValue properties = when.get("properties");
            if (properties.istable()) {
                LuaValue enabled = properties.get("enabled");
                if (!enabled.isnil() && enabled.toboolean() != isEnabled(recipe)) {
                    return "property 'enabled' did not match the expected value";
                }
                LuaValue output = properties.get("output");
                if (!output.isnil()) {
                    ItemStack expected = LuaApiUtils.readItemStack(output, true, "conditional recipe output");
                    if (!sameStack(((IRecipe) recipe).getRecipeOutput(), expected)) {
                        return "property 'output' did not match the expected value";
                    }
                }
            }
            return null;
        }
    }

    private static final class Apply extends VarArgFunction {
        private final RecipeReference reference;
        private Apply(RecipeReference reference) { this.reference = reference; }
        public Varargs invoke(Varargs args) {
            return reference.override(args.arg(args.arg1() == reference ? 2 : 1));
        }
    }

    private static final class Disable extends VarArgFunction {
        private final RecipeReference reference;
        private Disable(RecipeReference reference) { this.reference = reference; }
        public Varargs invoke(Varargs args) {
            LuaTable definition = new LuaTable();
            definition.set("enabled", LuaValue.FALSE);
            return reference.override(definition);
        }
    }

    private static final class Remove extends VarArgFunction {
        private final List layers;
        private final LuaTable handle;
        private Remove(List layers, LuaTable handle) { this.layers = layers; this.handle = handle; }
        public Varargs invoke(Varargs args) {
            if (!handle.get("active").toboolean()) return LuaValue.NIL;
            for (int i = layers.size() - 1; i >= 0; i--) ((OverrideManager.Layer) layers.get(i)).remove();
            handle.set("active", LuaValue.FALSE);
            return LuaValue.NIL;
        }
    }

    private static OverrideManager.PropertyAdapter outputAdapter(final String key, final Object recipe) {
        return new OverrideManager.PropertyAdapter() {
            public Object read(Object target) {
                ItemStack output = ((IRecipe) target).getRecipeOutput();
                return output == null ? null : output.copy();
            }
            public void write(Object target, Object value) {
                if (!RecipeModificationHandler.setRecipeOutput(target, (ItemStack) value)) {
                    throw new IllegalStateException("Recipe output could not be updated: " + key);
                }
            }
        };
    }

    private static OverrideManager.PropertyAdapter enabledAdapter(final Object recipe) {
        final List crafting = CraftingManager.getInstance().getRecipeList();
        final int originalIndex = crafting.indexOf(recipe);
        return new OverrideManager.PropertyAdapter() {
            public Object read(Object target) {
                if (target instanceof SmeltingRecipe) {
                    return Boolean.valueOf(((SmeltingRecipe) target).isRegistered());
                }
                return Boolean.valueOf(crafting.contains(target));
            }
            public void write(Object target, Object value) {
                boolean enabled = ((Boolean) value).booleanValue();
                if (target instanceof SmeltingRecipe) {
                    SmeltingRecipe smelting = (SmeltingRecipe) target;
                    if (enabled) smelting.setOutput(smelting.getOutput());
                    else smelting.removeFromFurnace();
                    return;
                }
                if (!enabled) crafting.remove(target);
                else if (!crafting.contains(target)) crafting.add(Math.min(originalIndex, crafting.size()), target);
            }
        };
    }

    private static LuaValue stackTable(ItemStack stack) {
        if (stack == null) return LuaValue.NIL;
        LuaTable out = new LuaTable();
        out.set("id", stack.itemID);
        out.set("count", stack.stackSize);
        out.set("damage", stack.getItemDamage());
        return out;
    }

    private static boolean isEnabled(Object recipe) {
        if (recipe instanceof SmeltingRecipe) return ((SmeltingRecipe) recipe).isRegistered();
        return CraftingManager.getInstance().getRecipeList().contains(recipe);
    }

    private static boolean sameStack(ItemStack left, ItemStack right) {
        return left == right || left != null && right != null && left.itemID == right.itemID
            && left.stackSize == right.stackSize && left.getItemDamage() == right.getItemDamage();
    }
}
