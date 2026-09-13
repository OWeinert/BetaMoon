package betamoon.luaapi.resource;

import betamoon.luaapi.LuaApiUtils;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.recipes.NativeRecipeInspector;
import betamoon.recipes.NativeRecipeKind;
import betamoon.recipes.NativeRecipeRegistries;
import betamoon.recipes.RecipeModificationHandler;
import betamoon.recipes.SmeltingRecipe;
import betamoon.recipes.custom.CustomRecipes;
import betamoon.recipes.custom.RecipeTypes;
import betamoon.recipes.custom.RecipeValues;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.FurnaceRecipes;
import net.minecraft.src.IRecipe;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Adds direct recipe lookup, criteria queries, and reversible recipe overrides.
 */
public final class RecipeRegistryApi {
    private static final Map<Object, RecipeReference> NATIVE = new LinkedHashMap<Object, RecipeReference>();
    private static long nextIdentity;
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
        private Get(LuaTable service, boolean optional) {
            this.service = service;
            this.optional = optional;
        }

        public Varargs invoke(Varargs args) {
            String key = args.arg(args.arg1() == service ? 2 : 1).checkjstring();
            CustomRecipes.Entry custom = CustomRecipes.get(key);
            if (custom != null) {
                return custom.reference;
            }
            RecipeModificationHandler.createRecipeMap();
            IRecipe recipe = RecipeModificationHandler.getRecipeByKey(key);
            for (RecipeReference reference : NATIVE.values()) {
                if (reference.key.equals(key) && reference.exists()) {
                    return reference;
                }
            }
            if (recipe == null && !optional) {
                throw new LuaError("Recipe '" + key + "' was not found.");
            }
            return recipe == null ? LuaValue.NIL : reference(key, recipe);
        }
    }

    private static final class Find extends VarArgFunction {
        private final LuaTable service;
        private final int mode;
        private Find(LuaTable service, int mode) {
            this.service = service;
            this.mode = mode;
        }

        public Varargs invoke(Varargs args) {
            LuaValue criteria = args.arg(args.arg1() == service ? 2 : 1);
            if (criteria.isnil()) {
                criteria = new LuaTable();
            }
            if (!criteria.istable()) {
                throw new LuaError("recipe criteria must be a table.");
            }
            List<LuaValue> values = findRecipes(criteria);
            if (mode == 1) {
                return values.isEmpty() ? LuaValue.NIL : (LuaValue) values.get(0);
            }
            if (mode == 2) {
                if (values.isEmpty()) {
                    return LuaValue.NIL;
                }
                if (values.size() != 1) {
                    throw new LuaError("Expected exactly one recipe, found " + values.size() + ".");
                }
                return (LuaValue) values.get(0);
            }
            return new LuaResultList(values, new LuaResultList.BulkOverride() {
                public LuaValue apply(LuaValue reference, LuaValue definition, int index) {
                    return ((RecipeTarget) reference).override(definition);
                }
            });
        }
    }

    private static List<LuaValue> findRecipes(LuaValue criteria) {
        CustomRecipes.validateQuery(criteria);
        RecipeModificationHandler.createRecipeMap();
        Map<String, IRecipe> all = RecipeModificationHandler.getRecipeMap();
        List<LuaValue> out = new ArrayList<>();
        Set<RecipeReference> seen = new HashSet<RecipeReference>();
        for (Map.Entry<String, IRecipe> entry : all.entrySet()) {
            IRecipe recipe = entry.getValue();
            RecipeReference reference = reference(entry.getKey(), recipe);
            seen.add(reference);
            if (nativeMatches(reference, criteria)) {
                out.add(reference);
            }
        }
        for (RecipeReference reference : NATIVE.values()) {
            if (!seen.contains(reference) && reference.exists() && nativeMatches(reference, criteria)) {
                out.add(reference);
            }
        }
        for (CustomRecipes.Entry entry : CustomRecipes.all()) {
            if (CustomRecipes.matchesQuery(entry, criteria)) {
                out.add(entry.reference);
            }
        }
        return out;
    }

    private static boolean nativeMatches(RecipeReference reference, LuaValue query) {
        IRecipe recipe = reference.recipe;
        if (!matchesType(recipe, query.get("type"))) {
            return false;
        }
        if (isEnabled(recipe) != RecipeValues.bool(query.get("enabled"), true, "recipe query.enabled")) {
            return false;
        }
        if (!query.get("owner").isnil() && !reference.get("owner").raweq(query.get("owner"))) {
            return false;
        }
        if (!query.get("data").isnil() && !RecipeValues.keys(query.get("data"), "recipe query.data").isEmpty()) {
            return false;
        }
        if (!query.get("output").isnil()) {
            LuaValue criterion = query.get("output");
            boolean matches = CustomRecipes.anyDamage(criterion)
                    ? CustomRecipes.outputMatches(((IRecipe) recipe).getRecipeOutput(), criterion)
                    : RecipeModificationHandler.matchesOutput(((IRecipe) recipe).getRecipeOutput(),
                            CustomRecipes.queryStack(criterion));
            if (!matches) {
                return false;
            }
        }
        if (!query.get("input").isnil() && !nativeInputMatches(recipe, query.get("input"))) {
            return false;
        }
        for (String map : new String[]{"ingredients", "outputs"}) {
            if (!query.get(map).isnil()) {
                for (String role : RecipeValues.keys(query.get(map), "recipe query." + map)) {
                    if (!(recipe instanceof SmeltingRecipe)
                            || !role.equals(map.equals("ingredients") ? "input" : "output")) {
                        return false;
                    }
                    LuaValue criterion = query.get(map).get(role);
                    if (map.equals("outputs")) {
                        if (!CustomRecipes.outputMatches(((IRecipe) recipe).getRecipeOutput(), criterion)) {
                            return false;
                        }
                    } else if (!nativeInputMatches(recipe, criterion)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean nativeInputMatches(IRecipe recipe, LuaValue criterion) {
        ItemStack target = CustomRecipes.queryStack(criterion);
        if (!CustomRecipes.anyDamage(criterion) || recipe instanceof SmeltingRecipe) {
            return RecipeModificationHandler.matchesInput(recipe, target);
        }
        List<ItemStack> inputs = NativeRecipeInspector.collectInputs(recipe);
        if (inputs != null) {
            for (Object object : inputs) {
                ItemStack stack = NativeRecipeInspector.normalizeIngredient(object);
                if (stack != null && stack.itemID == target.itemID) {
                    return true;
                }
            }
        }
        return false;
    }

    private static RecipeReference reference(String key, IRecipe recipe) {
        Object identity = NativeRecipeInspector.identity(recipe);
        RecipeReference old = NATIVE.get(identity);
        if (old != null && !old.retired && NativeRecipeInspector.representsSameRegistration(old.recipe, recipe)) {
            return old;
        }
        if (old != null && !(recipe instanceof SmeltingRecipe)) {
            old.retire();
        }
        RecipeReference reference = new RecipeReference(key, recipe);
        NATIVE.put(identity, reference);
        return reference;
    }

    /**
     * Resolves the exact native registration, including when outputs are
     * duplicated.
     */
    public static LuaValue referenceFor(IRecipe recipe) {
        RecipeModificationHandler.createRecipeMap();
        for (Map.Entry<String, IRecipe> entry : RecipeModificationHandler.getRecipeMap().entrySet()) {
            if (entry.getValue() == recipe) {
                return reference(entry.getKey(), recipe);
            }
        }
        throw new LuaError("Native recipe registration was not found");
    }

    public static LuaValue referenceForSmelting(int input) {
        ItemStack output = FurnaceRecipes.smelting().getSmeltingResult(input);
        RecipeReference existing = NATIVE.get(Integer.valueOf(input));
        if (existing != null && !existing.retired && ((SmeltingRecipe) existing.recipe).getOutput() == output) {
            return existing;
        }
        RecipeModificationHandler.createRecipeMap();
        for (Map.Entry<String, IRecipe> entry : RecipeModificationHandler.getRecipeMap().entrySet()) {
            if (entry.getValue() instanceof SmeltingRecipe
                    && ((SmeltingRecipe) entry.getValue()).getInputId() == input) {
                return reference(entry.getKey(), entry.getValue());
            }
        }
        return LuaValue.NIL;
    }

    public static void retire(LuaValue reference) {
        if (reference instanceof RecipeReference) {
            ((RecipeReference) reference).retire();
        }
    }

    public static ItemStack nativeOutput(LuaValue reference) {
        return ((SmeltingRecipe) ((RecipeReference) reference).recipe).getOutput();
    }

    public static boolean nativeDisabled(LuaValue reference) {
        return ((RecipeReference) reference).disabled;
    }

    public static void restoreNative(LuaValue reference) {
        RecipeReference nativeRef = (RecipeReference) reference;
        NATIVE.put(Integer.valueOf(((SmeltingRecipe) nativeRef.recipe).getInputId()), nativeRef);
    }

    private static boolean matchesType(IRecipe recipe, LuaValue expected) {
        if (expected.isnil()) {
            return true;
        }
        String actual = NativeRecipeInspector.kind(recipe).getLuaName();
        if (expected.type() == LuaValue.TSTRING && expected.tojstring().equalsIgnoreCase("unknown")) {
            return actual.equals("unknown");
        }
        if (expected.type() == LuaValue.TSTRING || expected instanceof RecipeTypes.Reference) {
            String name = RecipeTypes.name(expected);
            return ("minecraft:" + actual).equals(name);
        }
        if (!expected.istable()) {
            throw new LuaError("recipe type must be a string or list.");
        }
        for (int i = 1; i <= expected.length(); i++) {
            if (matchesType(recipe, expected.get(i))) {
                return true;
            }
        }
        return false;
    }

    /** Stable Lua view over one concrete recipe registration. */
    private static final class RecipeReference extends LuaTable implements RecipeTarget {
        private final String key;
        private final IRecipe recipe;
        private final String identity = "native-recipe:" + nextIdentity++;
        private boolean retired;
        private boolean disabled;
        private final List<LuaTable> handles = new ArrayList<LuaTable>();

        private RecipeReference(String key, IRecipe recipe) {
            this.key = key;
            this.recipe = recipe;
            set("key", LuaValue.valueOf(key));
            set("type", LuaValue.valueOf(NativeRecipeInspector.kind(recipe).getLuaName()));
            set("output", stackTable(NativeRecipeInspector.output(recipe)));
            String owner = ScriptResourceTracker.findOwner(NativeRecipeInspector.ownershipTarget(recipe));
            set("owner", LuaValue.valueOf(owner == null ? "minecraft" : owner));
            set("exists", LuaValue.TRUE);
            set("override", new Apply(this));
            set("disable", new Disable(this));
        }

        private boolean exists() {
            return !retired && (disabled || isEnabled(recipe));
        }

        private void retire() {
            retired = true;
            Object cacheKey = NativeRecipeInspector.identity(recipe);
            if (NATIVE.get(cacheKey) == this) {
                NATIVE.remove(cacheKey);
            }
            for (LuaTable handle : handles) {
                handle.set("active", LuaValue.FALSE);
                handle.set("reason", "target registration was removed");
            }
            handles.clear();
        }

        public LuaValue get(String key) {
            return get(LuaValue.valueOf(key));
        }

        public LuaValue get(LuaValue key) {
            String property = key.tojstring();
            if (property.equals("exists")) {
                return LuaValue.valueOf(exists());
            }
            if (property.equals("enabled")) {
                return LuaValue.valueOf(exists() && isEnabled(recipe));
            }
            if (property.equals("output")) {
                return stackTable(NativeRecipeInspector.output(recipe));
            }
            NativeRecipeKind kind = NativeRecipeInspector.kind(recipe);
            if (property.equals("recipeType") && kind != NativeRecipeKind.UNKNOWN) {
                return RecipeTypes.get(LuaValue.valueOf(kind.getLuaName()), true).reference;
            }
            return super.get(key);
        }

        @Override
        public LuaValue override(LuaValue definition) {
            if (!definition.istable()) {
                throw new LuaError("recipe override expects a table.");
            }
            RecipeTypes.owner();
            if (!exists()) {
                throw new LuaError("Recipe registration no longer exists: " + key);
            }
            String inactiveReason = checkConditions(definition.get("when"));
            if (inactiveReason != null) {
                LuaTable inactive = new LuaTable();
                inactive.set("target", this);
                inactive.set("active", LuaValue.FALSE);
                inactive.set("reason", LuaValue.valueOf(inactiveReason));
                return inactive;
            }
            LuaValue changes = definition.get("changes");
            if (changes.isnil()) {
                changes = RecipeValues.copy(definition);
                changes.set("when", LuaValue.NIL);
                changes.set("priority", LuaValue.NIL);
                changes.set("target", LuaValue.NIL);
            } else {
                RecipeValues.fields(definition, "native recipe override", "when", "changes", "priority", "target");
            }
            RecipeValues.fields(changes, "native recipe override.changes", "output", "enabled");
            int priority = definition.get("priority").isnil()
                    ? 0
                    : RecipeValues.integer(definition.get("priority"), "override.priority");
            RecipeValues.bool(changes.get("enabled"), true, "recipe override.enabled");
            List<OverrideManager.Layer<?, ?>> layers = new ArrayList<>();
            LuaValue output = changes.get("output");
            if (!output.isnil()) {
                final ItemStack stack = RecipeValues.stack(output, false, "recipe output");
                RecipeValues.fits(stack, "recipe output");
                OverrideManager.Property<IRecipe, ItemStack> property = new OverrideManager.Property<>("output",
                        outputAdapter(this));
                layers.add(OverrideManager.apply(identity, recipe, property, stack, priority));
                set("output", stackTable(stack));
            }
            LuaValue enabled = changes.get("enabled");
            if (!enabled.isnil()) {
                OverrideManager.Property<IRecipe, Boolean> property = new OverrideManager.Property<>("enabled",
                        enabledAdapter(this));
                layers.add(OverrideManager.apply(identity, recipe, property, Boolean.valueOf(enabled.toboolean()),
                        priority));
            }
            LuaTable handle = new LuaTable();
            handle.set("target", this);
            handle.set("active", LuaValue.TRUE);
            handle.set("remove", new Remove(layers, handle));
            handles.add(handle);
            return handle;
        }

        /** Evaluates the intentionally small declarative condition language. */
        private String checkConditions(LuaValue when) {
            if (when.isnil()) {
                return null;
            }
            if (!when.istable()) {
                throw new LuaError("recipe override when must be a table.");
            }
            LuaValue owner = when.get("owner");
            if (!owner.isnil() && !owner.tojstring().equals(get("owner").tojstring())) {
                return "target owner is '" + get("owner").tojstring() + "', expected '" + owner.tojstring() + "'";
            }
            LuaValue type = when.get("type");
            if (!type.isnil() && !matchesType(recipe, type)) {
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
                    if (!sameStack(NativeRecipeInspector.output(recipe), expected)) {
                        return "property 'output' did not match the expected value";
                    }
                }
            }
            return null;
        }
    }

    private static final class Apply extends VarArgFunction {
        private final RecipeReference reference;
        private Apply(RecipeReference reference) {
            this.reference = reference;
        }

        public Varargs invoke(Varargs args) {
            return reference.override(args.arg(args.arg1() == reference ? 2 : 1));
        }
    }

    private static final class Disable extends VarArgFunction {
        private final RecipeReference reference;
        private Disable(RecipeReference reference) {
            this.reference = reference;
        }

        public Varargs invoke(Varargs args) {
            LuaTable definition = new LuaTable();
            definition.set("enabled", LuaValue.FALSE);
            return reference.override(definition);
        }
    }

    private static final class Remove extends VarArgFunction {
        private final List<OverrideManager.Layer<?, ?>> layers;
        private final LuaTable handle;
        private Remove(List<OverrideManager.Layer<?, ?>> layers, LuaTable handle) {
            this.layers = layers;
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            if (!handle.get("active").toboolean()) {
                return LuaValue.NIL;
            }
            for (int i = layers.size() - 1; i >= 0; i--) {
                (layers.get(i)).remove();
            }
            handle.set("active", LuaValue.FALSE);
            return LuaValue.NIL;
        }
    }

    private static OverrideManager.PropertyAdapter<IRecipe, ItemStack> outputAdapter(final RecipeReference reference) {
        return new OverrideManager.PropertyAdapter<IRecipe, ItemStack>() {
            public ItemStack read(IRecipe target) {
                ItemStack output = NativeRecipeInspector.output(target);
                return output == null ? null : output.copy();
            }

            public void write(IRecipe target, ItemStack value) {
                if (reference.retired) {
                    return;
                }
                if (target instanceof SmeltingRecipe && (reference.disabled
                        || NATIVE.get(Integer.valueOf(((SmeltingRecipe) target).getInputId())) != reference)) {
                    ((SmeltingRecipe) target).setStoredOutput(value.copy());
                    return;
                }
                if (!RecipeModificationHandler.setRecipeOutput(target, value)) {
                    throw new IllegalStateException("Recipe output could not be updated: " + reference.key);
                }
            }
        };
    }

    private static OverrideManager.PropertyAdapter<IRecipe, Boolean> enabledAdapter(final RecipeReference reference) {
        final IRecipe recipe = reference.recipe;
        final List<IRecipe> crafting = NativeRecipeRegistries.crafting();
        final int originalIndex = crafting.indexOf(recipe);
        return new OverrideManager.PropertyAdapter<IRecipe, Boolean>() {
            public Boolean read(IRecipe target) {
                if (target instanceof SmeltingRecipe) {
                    return Boolean.valueOf(((SmeltingRecipe) target).isRegistered());
                }
                return Boolean.valueOf(crafting.contains(target));
            }

            public void write(IRecipe target, Boolean value) {
                if (reference.retired) {
                    return;
                }
                boolean enabled = value.booleanValue();
                reference.disabled = !enabled;
                if (target instanceof SmeltingRecipe) {
                    SmeltingRecipe smelting = (SmeltingRecipe) target;
                    if (NATIVE.get(Integer.valueOf(smelting.getInputId())) != reference) {
                        return;
                    }
                    if (enabled) {
                        smelting.setOutput(smelting.getOutput());
                    } else {
                        smelting.removeFromFurnace();
                    }
                    return;
                }
                if (!enabled) {
                    crafting.remove(target);
                } else if (!crafting.contains(target)) {
                    crafting.add(Math.min(originalIndex, crafting.size()), target);
                }
            }
        };
    }

    private static LuaValue stackTable(ItemStack stack) {
        if (stack == null) {
            return LuaValue.NIL;
        }
        LuaTable out = new LuaTable();
        out.set("id", stack.itemID);
        out.set("count", stack.stackSize);
        out.set("damage", stack.getItemDamage());
        return out;
    }

    private static boolean isEnabled(Object recipe) {
        if (recipe instanceof SmeltingRecipe) {
            return ((SmeltingRecipe) recipe).isRegistered();
        }
        return NativeRecipeRegistries.crafting().contains(recipe);
    }

    private static boolean sameStack(ItemStack left, ItemStack right) {
        return left == right || left != null && right != null && left.itemID == right.itemID
                && left.stackSize == right.stackSize && left.getItemDamage() == right.getItemDamage();
    }
}
