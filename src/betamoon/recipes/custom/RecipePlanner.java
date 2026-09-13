package betamoon.recipes.custom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import static betamoon.recipes.custom.RecipeValues.stack;

/**
 * Produces deterministic, mutation-free ingredient allocations from inventory
 * snapshots.
 */
final class RecipePlanner {
    private RecipePlanner() {
    }

    static Selection select(RecipeDefinition recipe, ItemStack[] inventory,
            Map<String, RecipeBindings.Resolved> bindings) {
        if (!recipe.enabled) {
            return null;
        }
        Selection selection = new Selection();
        for (RecipeTypes.Role role : recipe.type.ingredients.values()) {
            RecipeBindings.Resolved binding = bindings.get(role.name);
            if (binding == null) {
                return null;
            }
            boolean matched;
            if (role.type == RecipeTypes.RoleValueType.ITEM) {
                matched = selectItem(recipe, inventory, binding, selection);
            } else if (role.type == RecipeTypes.RoleValueType.ITEM_POOL) {
                matched = selectPool(recipe, inventory, binding, selection);
            } else if (role.type == RecipeTypes.RoleValueType.ITEM_GRID) {
                matched = selectGrid(recipe, inventory, binding, selection);
            } else {
                matched = false;
            }
            if (!matched) {
                return null;
            }
        }
        return selection;
    }

    private static boolean selectItem(RecipeDefinition recipe, ItemStack[] inventory, RecipeBindings.Resolved binding,
            Selection selection) {
        RecipeDefinition.Ingredient ingredient = recipe.ingredients.get(binding.role.name);
        ItemStack available = inventory[binding.slots[0]];
        if (ingredient == null) {
            return available == null;
        }
        if (!ingredient.matches(available, true)) {
            return false;
        }
        selection.add(new Use(binding.role.name, binding.names.get(0), binding.slots[0], ingredient.count, ingredient,
                available, 0, -1, -1));
        return true;
    }

    private static boolean selectPool(RecipeDefinition recipe, ItemStack[] inventory, RecipeBindings.Resolved binding,
            Selection selection) {
        RecipeDefinition.PoolIngredients pool = recipe.getIngredientPools().get(binding.role.name);
        if (pool == null) {
            return allEmpty(inventory, binding.slots);
        }
        int requirements = pool.requirements.size();
        int slots = binding.slots.length;
        int source = requirements + slots;
        int sink = source + 1;
        FlowNetwork network = new FlowNetwork(sink + 1);
        int total = 0;
        boolean[] recognized = new boolean[slots];
        FlowNetwork.Edge[][] allocation = new FlowNetwork.Edge[requirements][slots];
        for (int requirement = 0; requirement < requirements; requirement++) {
            RecipeDefinition.Ingredient ingredient = pool.requirements.get(requirement);
            network.add(source, requirement, ingredient.count);
            total += ingredient.count;
            for (int slot = 0; slot < slots; slot++) {
                ItemStack available = inventory[binding.slots[slot]];
                if (ingredient.matches(available, false)) {
                    recognized[slot] = true;
                    allocation[requirement][slot] = network.add(requirement, requirements + slot, available.stackSize);
                }
            }
        }
        for (int slot = 0; slot < slots; slot++) {
            ItemStack available = inventory[binding.slots[slot]];
            if (available != null && !binding.role.pool.allowExtra && !recognized[slot]) {
                return false;
            }
            network.add(requirements + slot, sink, available == null ? 0 : available.stackSize);
        }
        if (network.maximum(source, sink) != total) {
            return false;
        }
        for (int requirement = 0; requirement < requirements; requirement++) {
            RecipeDefinition.Ingredient ingredient = pool.requirements.get(requirement);
            for (int slot = 0; slot < slots; slot++) {
                FlowNetwork.Edge edge = allocation[requirement][slot];
                int count = edge == null ? 0 : edge.flow();
                if (count > 0) {
                    selection.add(new Use(binding.role.name, binding.names.get(slot), binding.slots[slot], count,
                            ingredient, inventory[binding.slots[slot]], requirement + 1, -1, -1));
                }
            }
        }
        return true;
    }

    private static boolean selectGrid(RecipeDefinition recipe, ItemStack[] inventory, RecipeBindings.Resolved binding,
            Selection selection) {
        RecipeDefinition.GridIngredients grid = recipe.getIngredientGrids().get(binding.role.name);
        if (grid == null) {
            return allEmpty(inventory, binding.slots);
        }
        for (TransformedPattern pattern : transformations(grid, binding.role.grid.transformations)) {
            int maxRow = binding.role.grid.placement.equals("fixed") ? 0 : binding.height - pattern.height;
            int maxColumn = binding.role.grid.placement.equals("fixed") ? 0 : binding.width - pattern.width;
            if (maxRow < 0 || maxColumn < 0) {
                continue;
            }
            for (int row = 0; row <= maxRow; row++) {
                for (int column = 0; column <= maxColumn; column++) {
                    List<Use> uses = gridUses(grid, pattern, row, column, binding, inventory);
                    if (uses != null) {
                        selection.addGrid(binding.role.name, pattern.name, row, column, uses);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<Use> gridUses(RecipeDefinition.GridIngredients grid, TransformedPattern pattern, int offsetRow,
            int offsetColumn, RecipeBindings.Resolved binding, ItemStack[] inventory) {
        List<Use> uses = new ArrayList<Use>();
        for (int row = 0; row < binding.height; row++) {
            for (int column = 0; column < binding.width; column++) {
                int patternRow = row - offsetRow;
                int patternColumn = column - offsetColumn;
                char symbol = patternRow >= 0 && patternRow < pattern.height && patternColumn >= 0
                        && patternColumn < pattern.width ? pattern.rows.get(patternRow).charAt(patternColumn) : ' ';
                int position = row * binding.width + column;
                ItemStack available = inventory[binding.slots[position]];
                if (symbol == ' ') {
                    if (binding.role.grid.emptyCells.equals("required") && available != null) {
                        return null;
                    }
                    continue;
                }
                RecipeDefinition.Ingredient ingredient = grid.key.get(Character.valueOf(symbol));
                if (!ingredient.matches(available, true)) {
                    return null;
                }
                uses.add(new Use(binding.role.name, binding.names.get(position), binding.slots[position],
                        ingredient.count, ingredient, available, 0, row + 1, column + 1));
            }
        }
        return uses;
    }

    private static List<TransformedPattern> transformations(RecipeDefinition.GridIngredients grid, LuaValue requested) {
        List<TransformedPattern> result = new ArrayList<TransformedPattern>();
        Set<String> seen = new LinkedHashSet<String>();
        addTransformation(result, seen, new TransformedPattern("identity", grid.pattern));
        for (int i = 1; i <= requested.length(); i++) {
            String name = requested.get(i).tojstring();
            List<String> rows;
            if (name.equals("mirror_horizontal")) {
                rows = mirrorHorizontal(grid.pattern);
            } else if (name.equals("mirror_vertical")) {
                rows = mirrorVertical(grid.pattern);
            } else {
                int turns = name.equals("rotate_90") ? 1 : name.equals("rotate_180") ? 2 : 3;
                rows = grid.pattern;
                for (int turn = 0; turn < turns; turn++) {
                    rows = rotate(rows);
                }
            }
            addTransformation(result, seen, new TransformedPattern(name, rows));
        }
        return result;
    }

    private static void addTransformation(List<TransformedPattern> result, Set<String> seen,
            TransformedPattern pattern) {
        String key = pattern.rows.toString();
        if (seen.add(key)) {
            result.add(pattern);
        }
    }

    private static List<String> mirrorHorizontal(List<String> source) {
        List<String> result = new ArrayList<String>();
        for (String row : source) {
            result.add(new StringBuilder(row).reverse().toString());
        }
        return result;
    }

    private static List<String> mirrorVertical(List<String> source) {
        List<String> result = new ArrayList<String>(source);
        Collections.reverse(result);
        return result;
    }

    private static List<String> rotate(List<String> source) {
        int oldHeight = source.size();
        int oldWidth = source.get(0).length();
        List<String> result = new ArrayList<String>();
        for (int column = 0; column < oldWidth; column++) {
            StringBuilder row = new StringBuilder();
            for (int oldRow = oldHeight - 1; oldRow >= 0; oldRow--) {
                row.append(source.get(oldRow).charAt(column));
            }
            result.add(row.toString());
        }
        return result;
    }

    private static boolean allEmpty(ItemStack[] inventory, int[] slots) {
        for (int slot : slots) {
            if (inventory[slot] != null) {
                return false;
            }
        }
        return true;
    }

    static final class Selection {
        final List<Use> uses = new ArrayList<Use>();
        final Map<String, GridPlacement> grids = new LinkedHashMap<String, GridPlacement>();

        void add(Use use) {
            uses.add(use);
        }

        void addGrid(String role, String transformation, int row, int column, List<Use> selected) {
            uses.addAll(selected);
            grids.put(role, new GridPlacement(transformation, row, column));
        }

        LuaValue view() {
            LuaTable result = new LuaTable();
            for (Use use : uses) {
                LuaTable role = result.get(use.role).istable() ? result.get(use.role).checktable() : new LuaTable();
                LuaTable entry = new LuaTable();
                entry.set("slot", use.slotName);
                entry.set("count", use.count);
                ItemStack selected = use.selected.copy();
                selected.stackSize = use.count;
                entry.set("stack", stack(selected));
                if (use.requirement > 0) {
                    entry.set("requirement", use.requirement);
                }
                if (use.row > 0) {
                    entry.set("row", use.row);
                    entry.set("column", use.column);
                }
                role.set(role.length() + 1, entry);
                result.set(use.role, role);
            }
            for (Map.Entry<String, GridPlacement> item : grids.entrySet()) {
                LuaTable role = result.get(item.getKey()).checktable();
                role.set("transformation", item.getValue().transformation);
                role.set("row", item.getValue().row + 1);
                role.set("column", item.getValue().column + 1);
            }
            return result;
        }

        String fingerprint() {
            return RecipeValues.fingerprint(view());
        }
    }

    static final class Use {
        final String role;
        final String slotName;
        final int slot;
        final int count;
        final RecipeDefinition.Ingredient ingredient;
        final ItemStack selected;
        final int requirement;
        final int row;
        final int column;

        Use(String role, String slotName, int slot, int count, RecipeDefinition.Ingredient ingredient,
                ItemStack selected, int requirement, int row, int column) {
            this.role = role;
            this.slotName = slotName;
            this.slot = slot;
            this.count = count;
            this.ingredient = ingredient;
            this.selected = selected.copy();
            this.requirement = requirement;
            this.row = row;
            this.column = column;
        }
    }

    private static final class GridPlacement {
        final String transformation;
        final int row;
        final int column;

        GridPlacement(String transformation, int row, int column) {
            this.transformation = transformation;
            this.row = row;
            this.column = column;
        }
    }

    private static final class TransformedPattern {
        final String name;
        final List<String> rows;
        final int width;
        final int height;

        TransformedPattern(String name, List<String> rows) {
            this.name = name;
            this.rows = rows;
            height = rows.size();
            width = rows.get(0).length();
        }
    }

    private static final class FlowNetwork {
        private final List<List<Edge>> edges;
        private boolean[] visited;

        FlowNetwork(int nodes) {
            edges = new ArrayList<List<Edge>>();
            for (int node = 0; node < nodes; node++) {
                edges.add(new ArrayList<Edge>());
            }
        }

        Edge add(int from, int to, int capacity) {
            Edge forward = new Edge(to, capacity);
            Edge reverse = new Edge(from, 0);
            forward.reverse = reverse;
            reverse.reverse = forward;
            edges.get(from).add(forward);
            edges.get(to).add(reverse);
            return forward;
        }

        int maximum(int source, int sink) {
            int total = 0;
            while (true) {
                visited = new boolean[edges.size()];
                int added = augment(source, sink, Integer.MAX_VALUE);
                if (added == 0) {
                    return total;
                }
                total += added;
            }
        }

        private int augment(int node, int sink, int available) {
            if (node == sink) {
                return available;
            }
            if (visited[node]) {
                return 0;
            }
            visited[node] = true;
            for (Edge edge : edges.get(node)) {
                int remaining = edge.capacity - edge.used;
                if (remaining <= 0) {
                    continue;
                }
                int added = augment(edge.to, sink, Math.min(available, remaining));
                if (added > 0) {
                    edge.used += added;
                    edge.reverse.used -= added;
                    return added;
                }
            }
            return 0;
        }

        static final class Edge {
            final int to;
            final int capacity;
            int used;
            Edge reverse;

            Edge(int to, int capacity) {
                this.to = to;
                this.capacity = capacity;
            }

            int flow() {
                return used;
            }
        }
    }
}
