package betamoon.recipes.custom;

import betamoon.luaapi.resource.RecipeRegistryApi;
import betamoon.tileentity.LuaTileEntity;
import betamoon.tileentity.RecipeCommitResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.FurnaceRecipes;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.recipes.custom.RecipeValues.*;

/** Matches recipe roles and commits prepared inventory changes atomically. */
public final class RecipeMatching {
    private RecipeMatching() {
    }

    public static void attach(final LuaTable service, final LuaTileEntity entity) {
        service.set("match", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return match(args.arg(args.arg1() == service ? 2 : 1), entity);
            }
        });
    }

    public static LuaValue match(LuaValue query, LuaTileEntity entity) {
        fields(query, "recipes:match",
                entity == null
                        ? new String[]{"type", "ingredients", "context"}
                        : new String[]{"type", "slots", "context"});
        RecipeTypes.Type type = RecipeTypes.get(query.get("type"), true);
        if (type.builtin && !type.name.equals("minecraft:smelting")) {
            throw error(type.name, "generic grid/pool matching is unavailable for native shaped and shapeless recipes");
        }
        LuaValue context = RecipeDefinition.context(type, query.get("context"));
        MatchSource source = entity == null
                ? directSource(type, query.get("ingredients"))
                : entitySource(type, query.get("slots"), entity);
        if (source == null) {
            return LuaValue.NIL;
        }

        CustomRecipes.Entry entry = null;
        RecipeDefinition definition = null;
        RecipePlanner.Selection selection = null;
        ItemStack nativeOutput = null;
        int nativeInput = -1;
        if (type.builtin) {
            RecipeBindings.Resolved inputBinding = source.inputs.get("input");
            ItemStack input = source.inventory[inputBinding.slots[0]];
            if (input == null || input.stackSize < 1) {
                return LuaValue.NIL;
            }
            nativeInput = input.itemID;
            nativeOutput = FurnaceRecipes.smelting().getSmeltingResult(nativeInput);
            if (nativeOutput == null) {
                return LuaValue.NIL;
            }
            LuaTable nativeDefinition = new LuaTable();
            LuaTable ingredient = new LuaTable();
            ingredient.set("item", nativeInput);
            ingredient.set("damage", "any");
            nativeDefinition.set("input", ingredient);
            nativeDefinition.set("output", stack(nativeOutput));
            definition = new RecipeDefinition(type, nativeDefinition, "smelting");
            selection = select(null, definition, source.inventory, source.inputs, context);
        } else {
            for (CustomRecipes.Entry candidate : CustomRecipes.candidates(type, source.itemIds)) {
                RecipePlanner.Selection candidateSelection = select(candidate.key, candidate.effective,
                        source.inventory, source.inputs, context);
                if (candidateSelection != null && candidate.effective.matchesContext(context)) {
                    entry = candidate;
                    definition = candidate.effective;
                    selection = candidateSelection;
                    break;
                }
            }
        }
        if (definition == null || selection == null) {
            return LuaValue.NIL;
        }
        return new Match(new Plan(type, entry, definition, selection, entity, source.inputs, source.outputs,
                nativeInput, nativeOutput));
    }

    private static RecipePlanner.Selection select(String recipeKey, RecipeDefinition definition, ItemStack[] inventory,
            Map<String, RecipeBindings.Resolved> bindings, LuaValue context) {
        return definition.type.matcherName == null
                ? RecipePlanner.select(definition, inventory, bindings)
                : RecipeMatcherRegistry.select(definition.type.matcherName, recipeKey, definition, inventory, bindings,
                        context);
    }

    private static MatchSource entitySource(RecipeTypes.Type type, LuaValue value, LuaTileEntity entity) {
        LuaValue slots = table(value, "recipes:match.slots");
        fields(slots, "recipes:match.slots", "ingredients", "outputs");
        if (!entity.isRecipeInventoryValid()) {
            return null;
        }
        Set<Integer> used = new HashSet<Integer>();
        Map<String, RecipeBindings.Resolved> inputs = resolveRoles(entity, slots.get("ingredients"), type.ingredients,
                used, "recipes:match.slots.ingredients");
        Map<String, RecipeBindings.Resolved> outputs = resolveRoles(entity, slots.get("outputs"), type.outputs, used,
                "recipes:match.slots.outputs");
        return new MatchSource(entity.copyRecipeInventory(), inputs, outputs);
    }

    private static Map<String, RecipeBindings.Resolved> resolveRoles(LuaTileEntity entity, LuaValue values,
            Map<String, RecipeTypes.Role> roles, Set<Integer> used, String path) {
        table(values, path);
        RecipeDefinition.checkRoles(values, roles, path);
        Map<String, RecipeBindings.Resolved> result = new LinkedHashMap<String, RecipeBindings.Resolved>();
        for (RecipeTypes.Role role : roles.values()) {
            LuaValue value = values.get(role.name);
            if (value.isnil()) {
                throw error(path + "." + role.name, "missing slot binding");
            }
            result.put(role.name, RecipeBindings.resolve(entity, value, role, used, path + "." + role.name));
        }
        return result;
    }

    private static MatchSource directSource(RecipeTypes.Type type, LuaValue value) {
        LuaValue ingredients = value.isnil() ? new LuaTable() : table(value, "recipes:match.ingredients");
        RecipeDefinition.checkRoles(ingredients, type.ingredients, "recipes:match.ingredients");
        List<ItemStack> inventory = new ArrayList<ItemStack>();
        Map<String, RecipeBindings.Resolved> bindings = new LinkedHashMap<String, RecipeBindings.Resolved>();
        for (RecipeTypes.Role role : type.ingredients.values()) {
            LuaValue roleValue = ingredients.get(role.name);
            if (role.type == RecipeTypes.RoleValueType.ITEM) {
                addDirectItem(role, roleValue, inventory, bindings);
            } else if (role.type == RecipeTypes.RoleValueType.ITEM_POOL) {
                addDirectPool(role, roleValue, inventory, bindings);
            } else if (role.type == RecipeTypes.RoleValueType.ITEM_GRID) {
                addDirectGrid(role, roleValue, inventory, bindings);
            }
        }
        return new MatchSource(inventory.toArray(new ItemStack[inventory.size()]), bindings,
                new LinkedHashMap<String, RecipeBindings.Resolved>());
    }

    private static void addDirectItem(RecipeTypes.Role role, LuaValue value, List<ItemStack> inventory,
            Map<String, RecipeBindings.Resolved> bindings) {
        int slot = inventory.size();
        inventory.add(value.isnil() ? null : stack(value, false, "recipes:match.ingredients." + role.name));
        bindings.put(role.name, directBinding(role, slot, 1, 1));
    }

    private static void addDirectPool(RecipeTypes.Role role, LuaValue value, List<ItemStack> inventory,
            Map<String, RecipeBindings.Resolved> bindings) {
        LuaValue list = value.isnil() ? new LuaTable() : table(value, "recipes:match.ingredients." + role.name);
        validateDense(list, "recipes:match.ingredients." + role.name);
        int start = inventory.size();
        int count = Math.max(1, list.length());
        for (int i = 1; i <= count; i++) {
            inventory.add(i > list.length() || list.get(i).isnil()
                    ? null
                    : stack(list.get(i), false, "recipes:match.ingredients." + role.name + "[" + i + "]"));
        }
        bindings.put(role.name, directBinding(role, start, count, 1));
    }

    private static void addDirectGrid(RecipeTypes.Role role, LuaValue value, List<ItemStack> inventory,
            Map<String, RecipeBindings.Resolved> bindings) {
        int start = inventory.size();
        if (value.isnil()) {
            for (int i = 0; i < role.grid.width * role.grid.height; i++) {
                inventory.add(null);
            }
            bindings.put(role.name, directBinding(role, start, role.grid.width, role.grid.height));
            return;
        }
        LuaValue rows = table(value, "recipes:match.ingredients." + role.name);
        validateDense(rows, "recipes:match.ingredients." + role.name);
        if (rows.length() != role.grid.height) {
            throw error("recipes:match.ingredients." + role.name, "expected " + role.grid.height + " grid rows");
        }
        for (int row = 1; row <= role.grid.height; row++) {
            LuaValue columns = table(rows.get(row), "recipes:match.ingredients." + role.name + "[" + row + "]");
            validateDense(columns, "recipes:match.ingredients." + role.name + "[" + row + "]");
            if (columns.length() != role.grid.width) {
                throw error("recipes:match.ingredients." + role.name + "[" + row + "]",
                        "expected " + role.grid.width + " grid columns");
            }
            for (int column = 1; column <= role.grid.width; column++) {
                LuaValue item = columns.get(column);
                inventory.add(item.isnil()
                        ? null
                        : stack(item, false,
                                "recipes:match.ingredients." + role.name + "[" + row + "][" + column + "]"));
            }
        }
        bindings.put(role.name, directBinding(role, start, role.grid.width, role.grid.height));
    }

    private static RecipeBindings.Resolved directBinding(RecipeTypes.Role role, int start, int width, int height) {
        int count = width * height;
        int[] slots = new int[count];
        List<String> names = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            slots[i] = start + i;
            names.add(role.name + "[" + (i + 1) + "]");
        }
        return new RecipeBindings.Resolved(role, names, slots, width, height);
    }

    private static void validateDense(LuaValue value, String path) {
        LuaValue key = LuaValue.NIL;
        while (!(key = value.next(key).arg1()).isnil()) {
            int index = integer(key, path + " index");
            if (index < 1 || index > value.length()) {
                throw error(path, "expected a dense list");
            }
        }
    }

    private static final class MatchSource {
        final ItemStack[] inventory;
        final Map<String, RecipeBindings.Resolved> inputs;
        final Map<String, RecipeBindings.Resolved> outputs;
        final Set<Integer> itemIds = new LinkedHashSet<Integer>();

        MatchSource(ItemStack[] inventory, Map<String, RecipeBindings.Resolved> inputs,
                Map<String, RecipeBindings.Resolved> outputs) {
            this.inventory = inventory;
            this.inputs = inputs;
            this.outputs = outputs;
            for (RecipeBindings.Resolved binding : inputs.values()) {
                for (int slot : binding.slots) {
                    if (inventory[slot] != null) {
                        itemIds.add(Integer.valueOf(inventory[slot].itemID));
                    }
                }
            }
        }
    }

    private static final class Plan {
        final RecipeTypes.Type type;
        final CustomRecipes.Entry entry;
        final RecipeDefinition definition;
        final RecipePlanner.Selection selection;
        final LuaTileEntity entity;
        final Map<String, RecipeBindings.Resolved> inputBindings;
        final Map<String, RecipeBindings.Resolved> outputBindings;
        final long typeRevision;
        final long entryRevision;
        final int nativeInput;
        final ItemStack nativeOutput;
        final ItemStack nativeIdentity;
        final String signature;
        boolean applied;

        Plan(RecipeTypes.Type type, CustomRecipes.Entry entry, RecipeDefinition definition,
                RecipePlanner.Selection selection, LuaTileEntity entity,
                Map<String, RecipeBindings.Resolved> inputBindings, Map<String, RecipeBindings.Resolved> outputBindings,
                int nativeInput, ItemStack nativeOutput) {
            this.type = type;
            this.entry = entry;
            this.definition = definition;
            this.selection = selection;
            this.entity = entity;
            this.inputBindings = inputBindings;
            this.outputBindings = outputBindings;
            this.nativeInput = nativeInput;
            nativeIdentity = nativeOutput;
            this.nativeOutput = nativeOutput == null ? null : nativeOutput.copy();
            typeRevision = type.revision;
            entryRevision = entry == null ? 0 : entry.revision;
            LuaTable value = new LuaTable();
            value.set("recipe", entry == null ? "minecraft:smelting/" + nativeInput : entry.key);
            value.set("definition", definition.fingerprint);
            value.set("allocation", selection.view());
            value.set("binding", bindingView());
            signature = fingerprint(value);
        }

        LuaValue ingredientView() {
            LuaTable result = new LuaTable();
            LuaValue allocations = selection.view();
            for (RecipeTypes.Role role : type.ingredients.values()) {
                if (role.type != RecipeTypes.RoleValueType.ITEM) {
                    result.set(role.name, allocations.get(role.name));
                    continue;
                }
                for (RecipePlanner.Use use : selection.uses) {
                    if (use.role.equals(role.name)) {
                        ItemStack selected = use.selected.copy();
                        selected.stackSize = use.count;
                        LuaValue view = stack(selected);
                        view.set("consume", LuaValue.valueOf(use.ingredient.consume));
                        result.set(role.name, view);
                        break;
                    }
                }
            }
            return result;
        }

        LuaValue remainderView() {
            LuaTable result = new LuaTable();
            int index = 1;
            for (RecipePlanner.Use use : selection.uses) {
                ItemStack remainder = remainder(use.ingredient, use.selected, use.count);
                if (remainder == null) {
                    continue;
                }
                LuaTable view = new LuaTable();
                view.set("ingredient", use.role);
                view.set("slot", use.slotName);
                view.set("stack", stack(remainder));
                if (use.ingredient.remainderOutput != null) {
                    view.set("output", use.ingredient.remainderOutput);
                }
                result.set(index++, view);
            }
            return result;
        }

        private LuaValue bindingView() {
            LuaTable result = new LuaTable();
            LuaTable ingredients = new LuaTable();
            for (Map.Entry<String, RecipeBindings.Resolved> binding : inputBindings.entrySet()) {
                ingredients.set(binding.getKey(), binding.getValue().view());
            }
            LuaTable outputs = new LuaTable();
            for (Map.Entry<String, RecipeBindings.Resolved> binding : outputBindings.entrySet()) {
                outputs.set(binding.getKey(), binding.getValue().view());
            }
            result.set("ingredients", ingredients);
            result.set("outputs", outputs);
            return result;
        }

        Preparation prepare(LuaValue options) {
            if (applied) {
                return Preparation.failure("already_applied");
            }
            if (!entity.isRecipeInventoryValid()) {
                return Preparation.failure("invalid_inventory");
            }
            if (entity.isRecipeCommitBlocked()) {
                return Preparation.failure("reentrant_apply");
            }
            if (type.revision != typeRevision || entry != null && (!entry.exists || entry.revision != entryRevision)) {
                return Preparation.failure("stale_recipe");
            }
            if (entry == null) {
                ItemStack current = FurnaceRecipes.smelting().getSmeltingResult(nativeInput);
                if (current != nativeIdentity || !same(current, nativeOutput)) {
                    return Preparation.failure("stale_recipe");
                }
            }
            LuaValue context = freshContext(options);
            if (!keys(definition.getConditions(), "conditions").isEmpty() && !definition.matchesContext(context)) {
                return Preparation.failure("conditions_changed");
            }
            ItemStack[] expected = entity.copyRecipeInventory();
            long revisionBeforeMatching = type.revision;
            RecipePlanner.Selection current = select(entry == null ? null : entry.key, definition, expected,
                    inputBindings, context);
            if (type.revision != revisionBeforeMatching) {
                return Preparation.failure("stale_recipe");
            }
            if (current == null || !current.fingerprint().equals(selection.fingerprint())) {
                return Preparation.failure("inputs_changed");
            }
            ItemStack[] planned = copyInventory(expected);
            consume(planned);
            String outputFailure = insertOutputs(planned);
            if (outputFailure != null) {
                return Preparation.failure(outputFailure);
            }
            if (!insertRemainders(planned)) {
                return Preparation.failure("remainder_blocked");
            }
            return Preparation.success(expected, planned);
        }

        private LuaValue freshContext(LuaValue options) {
            if (options.isnil()) {
                if (!keys(definition.getConditions(), "conditions").isEmpty()) {
                    throw error("recipe application.context", "conditions require a fresh context snapshot");
                }
                return new LuaTable();
            }
            fields(options, "recipe application", "context");
            return RecipeDefinition.context(type, options.get("context"));
        }

        private void consume(ItemStack[] planned) {
            Map<Integer, Integer> counts = new LinkedHashMap<Integer, Integer>();
            for (RecipePlanner.Use use : selection.uses) {
                if (use.ingredient.consume) {
                    Integer old = counts.get(Integer.valueOf(use.slot));
                    counts.put(Integer.valueOf(use.slot),
                            Integer.valueOf((old == null ? 0 : old.intValue()) + use.count));
                }
            }
            for (Map.Entry<Integer, Integer> consumed : counts.entrySet()) {
                int slot = consumed.getKey().intValue();
                planned[slot].stackSize -= consumed.getValue().intValue();
                if (planned[slot].stackSize == 0) {
                    planned[slot] = null;
                }
            }
        }

        private String insertOutputs(ItemStack[] planned) {
            int limit = entity.getInventoryStackLimit();
            for (Map.Entry<String, ItemStack> output : definition.getOutputs().entrySet()) {
                RecipeBindings.Resolved binding = outputBindings.get(output.getKey());
                if (binding == null || !insert(planned, binding.slots[0], output.getValue(), limit)) {
                    return "output_full";
                }
            }
            for (Map.Entry<String, List<ItemStack>> output : definition.getOutputPools().entrySet()) {
                RecipeBindings.Resolved binding = outputBindings.get(output.getKey());
                for (ItemStack product : output.getValue()) {
                    if (binding == null || !insertAcross(planned, binding.slots, product, limit)) {
                        return "output_full";
                    }
                }
            }
            return null;
        }

        private boolean insertRemainders(ItemStack[] planned) {
            int limit = entity.getInventoryStackLimit();
            for (RecipePlanner.Use use : selection.uses) {
                ItemStack remainder = remainder(use.ingredient, use.selected, use.count);
                if (remainder == null) {
                    continue;
                }
                int slot = use.slot;
                if (use.ingredient.remainderOutput != null) {
                    RecipeBindings.Resolved output = outputBindings.get(use.ingredient.remainderOutput);
                    if (output == null) {
                        return false;
                    }
                    slot = output.slots[0];
                }
                if (!insert(planned, slot, remainder, limit)) {
                    return false;
                }
            }
            return true;
        }

        String apply(LuaValue options) {
            Preparation preparation = prepare(options);
            if (!preparation.isReady()) {
                return preparation.failureReason;
            }
            applied = true;
            RecipeCommitResult result = entity.commitRecipeInventory(preparation.expected, preparation.planned);
            if (!result.wasApplied()) {
                applied = false;
            }
            return result.getFailureReason();
        }
    }

    private static final class Preparation {
        private final String failureReason;
        private final ItemStack[] expected;
        private final ItemStack[] planned;

        private Preparation(String failureReason, ItemStack[] expected, ItemStack[] planned) {
            this.failureReason = failureReason;
            this.expected = expected;
            this.planned = planned;
        }

        static Preparation failure(String reason) {
            return new Preparation(reason, null, null);
        }

        static Preparation success(ItemStack[] expected, ItemStack[] planned) {
            return new Preparation(null, expected, planned);
        }

        boolean isReady() {
            return failureReason == null;
        }
    }

    private static ItemStack[] copyInventory(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i] == null ? null : source[i].copy();
        }
        return result;
    }

    private static ItemStack remainder(RecipeDefinition.Ingredient ingredient, ItemStack input, int count) {
        if (!ingredient.consume) {
            return null;
        }
        ItemStack result = ingredient.getRemainder();
        if (result == null && input.getItem().hasContainerItem()) {
            result = new ItemStack(input.getItem().getContainerItem());
        }
        if (result != null) {
            result.stackSize *= count;
        }
        return result;
    }

    private static boolean insert(ItemStack[] inventory, int slot, ItemStack incoming, int limit) {
        ItemStack old = inventory[slot];
        if (old != null && !old.isItemEqual(incoming)) {
            return false;
        }
        long count = (old == null ? 0 : old.stackSize) + (long) incoming.stackSize;
        if (count > Math.min(limit, incoming.getMaxStackSize())) {
            return false;
        }
        ItemStack result = incoming.copy();
        result.stackSize = (int) count;
        inventory[slot] = result;
        return true;
    }

    private static boolean insertAcross(ItemStack[] inventory, int[] slots, ItemStack incoming, int limit) {
        int remaining = incoming.stackSize;
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int slot : slots) {
                ItemStack old = inventory[slot];
                if (pass == 0 && (old == null || !old.isItemEqual(incoming)) || pass == 1 && old != null) {
                    continue;
                }
                int capacity = Math.min(limit, incoming.getMaxStackSize()) - (old == null ? 0 : old.stackSize);
                if (capacity <= 0) {
                    continue;
                }
                int added = Math.min(capacity, remaining);
                ItemStack result = old == null ? incoming.copy() : old.copy();
                result.stackSize = (old == null ? 0 : old.stackSize) + added;
                inventory[slot] = result;
                remaining -= added;
                if (remaining == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class Match extends LuaTable {
        Match(final Plan plan) {
            set("recipe",
                    plan.entry == null
                            ? RecipeRegistryApi.referenceForSmelting(plan.nativeInput)
                            : plan.entry.reference);
            set("type", plan.type.builtin ? "smelting" : plan.type.name);
            set("signature", plan.signature);
            set("data", plan.definition.getData());
            set("output", stack(plan.definition.getOutput(plan.type.primary)));
            set("outputs", copy(plan.definition.getValue().get("outputs")));
            set("ingredients", plan.ingredientView());
            set("allocations", plan.selection.view());
            set("remainders", plan.remainderView());
            if (plan.entity != null) {
                set("canApply", new VarArgFunction() {
                    public Varargs invoke(Varargs args) {
                        return check(plan, args, false);
                    }
                });
                set("apply", new VarArgFunction() {
                    public Varargs invoke(Varargs args) {
                        return check(plan, args, true);
                    }
                });
            }
        }

        private Varargs check(Plan plan, Varargs args, boolean commit) {
            LuaValue options = args.arg(args.arg1() == this ? 2 : 1);
            String reason = commit ? plan.apply(options) : plan.prepare(options).failureReason;
            return LuaValue.varargsOf(LuaValue.valueOf(reason == null),
                    reason == null ? LuaValue.NIL : LuaValue.valueOf(reason));
        }
    }
}
