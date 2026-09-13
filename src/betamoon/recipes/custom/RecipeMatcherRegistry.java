package betamoon.recipes.custom;

import betamoon.BetaMoonMain;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.recipes.custom.RecipeValues.*;

/**
 * Reload-scoped Lua matching strategies that can only describe validated
 * allocations.
 */
public final class RecipeMatcherRegistry {
    private static final Map<String, Matcher> MATCHERS = new LinkedHashMap<String, Matcher>();

    private RecipeMatcherRegistry() {
    }

    public static void attach(LuaTable root) {
        final LuaTable service = new LuaTable();
        root.set("recipeMatchers", service);
        service.set("add", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return RecipeMatcherRegistry.add(args.arg(args.arg1() == service ? 2 : 1)).reference;
            }
        });
        service.set("get", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                Matcher matcher = MATCHERS.get(RecipeMatcherRegistry.name(args.arg(args.arg1() == service ? 2 : 1)));
                return matcher == null ? LuaValue.NIL : matcher.reference;
            }
        });
    }

    public static String name(LuaValue value) {
        if (value instanceof Reference) {
            return ((Reference) value).matcher.name;
        }
        return RecipeTypes.qualify(string(value, "recipe matcher"));
    }

    static RecipePlanner.Selection select(String matcherName, String recipeKey, RecipeDefinition recipe,
            ItemStack[] inventory, Map<String, RecipeBindings.Resolved> bindings, LuaValue context) {
        Matcher matcher = MATCHERS.get(matcherName);
        if (matcher == null || !matcher.enabled) {
            return null;
        }
        if (matcher.invoking) {
            return null;
        }
        PlanBuilder plan = new PlanBuilder(recipe, inventory, bindings);
        try {
            matcher.invoking = true;
            LuaValue recipeView = recipe.getValue();
            recipeView.set("type", recipe.type.name);
            recipeView.set("key", recipeKey);
            LuaValue result = matcher.callback
                    .invoke(LuaValue
                            .varargsOf(new LuaValue[]{recipeView, snapshot(inventory, bindings), copy(context), plan}))
                    .arg1();
            if (!result.toboolean() || !matcher.enabled) {
                return null;
            }
            return plan.finish();
        } catch (Throwable failure) {
            matcher.enabled = false;
            RecipeTypes.matcherChanged(matcher.name);
            String detail = failure.getMessage() == null ? failure.toString() : failure.getMessage();
            String message = "recipe matcher '" + matcher.name + "' was disabled after an error: " + detail;
            LuaScriptErrors.add(matcher.owner, message);
            BetaMoonMain.LOGGER.warning(matcher.owner + ": " + message);
            return null;
        } finally {
            matcher.invoking = false;
        }
    }

    private static Matcher add(LuaValue definition) {
        fields(definition, "recipeMatchers:add", "name", "match");
        final String owner = RecipeTypes.owner();
        final String name = RecipeTypes.qualify(string(definition.get("name"), "recipeMatchers:add.name"));
        LuaValue callback = definition.get("match");
        if (!callback.isfunction()) {
            throw RecipeValues.error("recipeMatchers:add.match", "expected function");
        }
        if (MATCHERS.containsKey(name)) {
            throw RecipeValues.error(name, "recipe matcher is already registered");
        }
        final Matcher matcher = new Matcher(name, owner, callback);
        MATCHERS.put(name, matcher);
        RecipeTypes.matcherChanged(name);
        ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
            public void run() {
                if (MATCHERS.get(name) == matcher) {
                    MATCHERS.remove(name);
                    matcher.enabled = false;
                    RecipeTypes.matcherChanged(name);
                }
            }
        });
        return matcher;
    }

    private static LuaValue snapshot(ItemStack[] inventory, Map<String, RecipeBindings.Resolved> bindings) {
        LuaTable result = new LuaTable();
        for (Map.Entry<String, RecipeBindings.Resolved> entry : bindings.entrySet()) {
            RecipeBindings.Resolved binding = entry.getValue();
            if (binding.role.type == RecipeTypes.RoleValueType.ITEM) {
                result.set(entry.getKey(), cell(inventory, binding, 0));
            } else if (binding.role.type == RecipeTypes.RoleValueType.ITEM_GRID) {
                LuaTable rows = new LuaTable();
                for (int row = 0; row < binding.height; row++) {
                    LuaTable columns = new LuaTable();
                    for (int column = 0; column < binding.width; column++) {
                        columns.set(column + 1, cell(inventory, binding, row * binding.width + column));
                    }
                    rows.set(row + 1, columns);
                }
                result.set(entry.getKey(), rows);
            } else {
                LuaTable cells = new LuaTable();
                for (int i = 0; i < binding.slots.length; i++) {
                    cells.set(i + 1, cell(inventory, binding, i));
                }
                result.set(entry.getKey(), cells);
            }
        }
        return result;
    }

    private static LuaValue cell(ItemStack[] inventory, RecipeBindings.Resolved binding, int position) {
        LuaTable result = new LuaTable();
        result.set("index", position + 1);
        result.set("slot", binding.names.get(position));
        if (binding.role.type == RecipeTypes.RoleValueType.ITEM_GRID) {
            result.set("row", position / binding.width + 1);
            result.set("column", position % binding.width + 1);
        }
        ItemStack item = inventory[binding.slots[position]];
        if (item != null) {
            result.set("stack", stack(item));
        }
        return result;
    }

    private static final class PlanBuilder extends LuaTable {
        private final RecipeDefinition recipe;
        private final ItemStack[] inventory;
        private final Map<String, RecipeBindings.Resolved> bindings;
        private final RecipePlanner.Selection selection = new RecipePlanner.Selection();
        private final Map<String, int[]> requirementTotals = new LinkedHashMap<String, int[]>();
        private final Map<Integer, Integer> slotTotals = new LinkedHashMap<Integer, Integer>();
        private boolean finished;

        PlanBuilder(RecipeDefinition recipe, ItemStack[] inventory, Map<String, RecipeBindings.Resolved> bindings) {
            this.recipe = recipe;
            this.inventory = inventory;
            this.bindings = bindings;
            set("use", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    use(args.arg(args.arg1() == PlanBuilder.this ? 2 : 1));
                    return PlanBuilder.this;
                }
            });
        }

        private void use(LuaValue definition) {
            if (finished) {
                throw RecipeValues.error("recipe matcher plan", "plan is already finalized");
            }
            fields(definition, "recipe matcher plan.use", "role", "slot", "requirement", "count");
            String roleName = string(definition.get("role"), "recipe matcher plan.use.role");
            RecipeBindings.Resolved binding = bindings.get(roleName);
            if (binding == null) {
                throw RecipeValues.error("recipe matcher plan.use.role", "unknown ingredient role '" + roleName + "'");
            }
            int position = slotPosition(binding, definition.get("slot"));
            Requirement requirement = requirement(binding.role, definition.get("requirement"));
            int count = definition.get("count").isnil()
                    ? requirement.ingredient.count
                    : integer(definition.get("count"), "recipe matcher plan.use.count");
            if (count < 1) {
                throw RecipeValues.error("recipe matcher plan.use.count", "expected a positive integer");
            }
            ItemStack available = inventory[binding.slots[position]];
            if (!requirement.ingredient.matches(available, false)) {
                throw RecipeValues.error("recipe matcher plan.use",
                        "selected slot does not match the declared ingredient");
            }
            int slot = binding.slots[position];
            int allocated = slotTotals.containsKey(Integer.valueOf(slot))
                    ? slotTotals.get(Integer.valueOf(slot)).intValue()
                    : 0;
            if (allocated + count > available.stackSize) {
                throw RecipeValues.error("recipe matcher plan.use.count", "allocation exceeds the selected stack");
            }
            slotTotals.put(Integer.valueOf(slot), Integer.valueOf(allocated + count));
            int[] totals = totals(roleName, requirement.totalCount);
            totals[requirement.index] += count;
            int row = binding.role.type == RecipeTypes.RoleValueType.ITEM_GRID ? position / binding.width + 1 : -1;
            int column = binding.role.type == RecipeTypes.RoleValueType.ITEM_GRID ? position % binding.width + 1 : -1;
            int displayedRequirement = binding.role.type == RecipeTypes.RoleValueType.ITEM_POOL
                    ? requirement.index + 1
                    : 0;
            selection.add(new RecipePlanner.Use(roleName, binding.names.get(position), slot, count,
                    requirement.ingredient, available, displayedRequirement, row, column));
        }

        private RecipePlanner.Selection finish() {
            finished = true;
            for (RecipeTypes.Role role : recipe.type.ingredients.values()) {
                validateRole(role, bindings.get(role.name));
            }
            return selection;
        }

        private void validateRole(RecipeTypes.Role role, RecipeBindings.Resolved binding) {
            int[] expected = expectedCounts(role);
            int[] actual = requirementTotals.get(role.name);
            if (expected == null) {
                if (actual != null || !allEmpty(binding)) {
                    throw RecipeValues.error("recipe matcher plan." + role.name, "omitted optional role must be empty");
                }
                return;
            }
            if (actual == null || actual.length != expected.length) {
                throw RecipeValues.error("recipe matcher plan." + role.name, "role has no complete allocation");
            }
            for (int i = 0; i < expected.length; i++) {
                if (actual[i] != expected[i]) {
                    throw RecipeValues.error("recipe matcher plan." + role.name,
                            "requirement " + (i + 1) + " needs exactly " + expected[i] + " items");
                }
            }
            boolean requireEmpty = role.type == RecipeTypes.RoleValueType.ITEM_GRID
                    && role.grid.emptyCells.equals("required");
            boolean rejectUnknown = role.type == RecipeTypes.RoleValueType.ITEM_POOL && !role.pool.allowExtra;
            if (requireEmpty || rejectUnknown) {
                for (int slot : binding.slots) {
                    if (inventory[slot] != null && !slotTotals.containsKey(Integer.valueOf(slot))) {
                        throw RecipeValues.error("recipe matcher plan." + role.name,
                                "unallocated occupied slot is not allowed");
                    }
                }
            }
        }

        private int[] expectedCounts(RecipeTypes.Role role) {
            if (role.type == RecipeTypes.RoleValueType.ITEM) {
                RecipeDefinition.Ingredient ingredient = recipe.ingredients.get(role.name);
                return ingredient == null ? null : new int[]{ingredient.count};
            }
            if (role.type == RecipeTypes.RoleValueType.ITEM_POOL) {
                RecipeDefinition.PoolIngredients pool = recipe.getIngredientPools().get(role.name);
                if (pool == null) {
                    return null;
                }
                int[] result = new int[pool.requirements.size()];
                for (int i = 0; i < result.length; i++) {
                    result[i] = pool.requirements.get(i).count;
                }
                return result;
            }
            RecipeDefinition.GridIngredients grid = recipe.getIngredientGrids().get(role.name);
            if (grid == null) {
                return null;
            }
            int[] result = new int[grid.key.size()];
            int index = 0;
            for (Map.Entry<Character, RecipeDefinition.Ingredient> entry : grid.key.entrySet()) {
                int occurrences = 0;
                for (String row : grid.pattern) {
                    for (int column = 0; column < row.length(); column++) {
                        if (row.charAt(column) == entry.getKey().charValue()) {
                            occurrences++;
                        }
                    }
                }
                result[index++] = entry.getValue().count * occurrences;
            }
            return result;
        }

        private Requirement requirement(RecipeTypes.Role role, LuaValue value) {
            if (role.type == RecipeTypes.RoleValueType.ITEM) {
                if (!value.isnil()) {
                    throw RecipeValues.error("recipe matcher plan.use.requirement",
                            "single item roles do not use a requirement");
                }
                RecipeDefinition.Ingredient ingredient = recipe.ingredients.get(role.name);
                if (ingredient == null) {
                    throw RecipeValues.error("recipe matcher plan.use.role",
                            "recipe omits optional role '" + role.name + "'");
                }
                return new Requirement(ingredient, 0, 1);
            }
            if (role.type == RecipeTypes.RoleValueType.ITEM_POOL) {
                RecipeDefinition.PoolIngredients pool = recipe.getIngredientPools().get(role.name);
                int index = integer(value, "recipe matcher plan.use.requirement") - 1;
                if (pool == null || index < 0 || index >= pool.requirements.size()) {
                    throw RecipeValues.error("recipe matcher plan.use.requirement", "unknown pool requirement");
                }
                return new Requirement(pool.requirements.get(index), index, pool.requirements.size());
            }
            RecipeDefinition.GridIngredients grid = recipe.getIngredientGrids().get(role.name);
            String symbol = string(value, "recipe matcher plan.use.requirement");
            if (grid == null || symbol.length() != 1 || !grid.key.containsKey(Character.valueOf(symbol.charAt(0)))) {
                throw RecipeValues.error("recipe matcher plan.use.requirement", "unknown grid ingredient character");
            }
            int index = 0;
            for (Character character : grid.key.keySet()) {
                if (character.charValue() == symbol.charAt(0)) {
                    return new Requirement(grid.key.get(character), index, grid.key.size());
                }
                index++;
            }
            throw RecipeValues.error("recipe matcher plan.use.requirement", "unknown grid ingredient character");
        }

        private int slotPosition(RecipeBindings.Resolved binding, LuaValue value) {
            if (value.isnumber()) {
                int position = integer(value, "recipe matcher plan.use.slot") - 1;
                if (position >= 0 && position < binding.slots.length) {
                    return position;
                }
            } else if (value.isstring()) {
                int position = binding.names.indexOf(value.tojstring());
                if (position >= 0) {
                    return position;
                }
            }
            throw RecipeValues.error("recipe matcher plan.use.slot", "slot is outside the selected role binding");
        }

        private int[] totals(String role, int count) {
            int[] result = requirementTotals.get(role);
            if (result == null) {
                result = new int[count];
                requirementTotals.put(role, result);
            }
            return result;
        }

        private boolean allEmpty(RecipeBindings.Resolved binding) {
            for (int slot : binding.slots) {
                if (inventory[slot] != null) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class Requirement {
        final RecipeDefinition.Ingredient ingredient;
        final int index;
        final int totalCount;

        Requirement(RecipeDefinition.Ingredient ingredient, int index, int totalCount) {
            this.ingredient = ingredient;
            this.index = index;
            this.totalCount = totalCount;
        }
    }

    private static final class Matcher {
        final String name;
        final String owner;
        final LuaValue callback;
        final Reference reference;
        boolean enabled = true;
        boolean invoking;

        Matcher(String name, String owner, LuaValue callback) {
            this.name = name;
            this.owner = owner;
            this.callback = callback;
            reference = new Reference(this);
        }
    }

    public static final class Reference extends LuaTable {
        private final Matcher matcher;

        private Reference(Matcher matcher) {
            this.matcher = matcher;
        }

        @Override
        public LuaValue get(LuaValue key) {
            String name = key.tojstring();
            if (name.equals("name")) {
                return LuaValue.valueOf(matcher.name);
            }
            if (name.equals("owner")) {
                return LuaValue.valueOf(matcher.owner);
            }
            if (name.equals("exists")) {
                return LuaValue.valueOf(MATCHERS.get(matcher.name) == matcher && matcher.enabled);
            }
            return super.get(key);
        }
    }
}
