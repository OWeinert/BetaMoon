package betamoon.recipes.custom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import static betamoon.recipes.custom.RecipeValues.*;

/**
 * Compiled immutable recipe values; contains no user callbacks or live
 * inventory stacks.
 */
public final class RecipeDefinition {
    public final RecipeTypes.Type type;
    public final Map<String, Ingredient> ingredients;
    private final Map<String, PoolIngredients> ingredientPools = new LinkedHashMap<String, PoolIngredients>();
    private final Map<String, GridIngredients> ingredientGrids = new LinkedHashMap<String, GridIngredients>();
    private final Map<String, ItemStack> outputs = new LinkedHashMap<String, ItemStack>();
    private final Map<String, List<ItemStack>> outputPools = new LinkedHashMap<String, List<ItemStack>>();
    private final LuaTable value = new LuaTable();
    private final LuaValue data;
    private final LuaValue conditions;
    public final int priority;
    public final boolean enabled;
    public final String fingerprint;

    public RecipeDefinition(RecipeTypes.Type type, LuaValue definition, String path) {
        this.type = type;
        Map<String, Ingredient> ingredients = new LinkedHashMap<>();
        fields(definition, path, "key", "type", "ingredients", "input", "outputs", "output", "data", "conditions",
                "priority", "enabled");
        LuaValue inputs = definition.get("ingredients");
        if (!definition.get("input").isnil()) {
            RecipeTypes.Role onlyRole = type.ingredients.size() == 1
                    ? type.ingredients.values().iterator().next()
                    : null;
            if (!inputs.isnil() || onlyRole == null || onlyRole.type != RecipeTypes.RoleValueType.ITEM) {
                throw error(path + ".input", "alias requires exactly one item role and no ingredients map");
            }
            inputs = new LuaTable();
            inputs.set(onlyRole.name, definition.get("input"));
        }
        if (inputs.isnil()) {
            inputs = new LuaTable();
        }
        checkRoles(inputs, type.ingredients, path + ".ingredients");
        LuaTable normalizedInputs = new LuaTable();
        for (RecipeTypes.Role role : type.ingredients.values()) {
            LuaValue input = inputs.get(role.name);
            if (input.isnil()) {
                if (!role.optional) {
                    throw error(path + ".ingredients." + role.name, "required role is missing");
                }
                continue;
            }
            String rolePath = path + ".ingredients." + role.name;
            if (role.type == RecipeTypes.RoleValueType.ITEM) {
                Ingredient ingredient = new Ingredient(input, role, type, rolePath);
                ingredients.put(role.name, ingredient);
                normalizedInputs.set(role.name, ingredient.value);
            } else if (role.type == RecipeTypes.RoleValueType.ITEM_POOL) {
                PoolIngredients pool = new PoolIngredients(input, role, type, rolePath);
                ingredientPools.put(role.name, pool);
                normalizedInputs.set(role.name, pool.value);
            } else if (role.type == RecipeTypes.RoleValueType.ITEM_GRID) {
                GridIngredients grid = new GridIngredients(input, role, type, rolePath);
                ingredientGrids.put(role.name, grid);
                normalizedInputs.set(role.name, grid.value);
            } else {
                throw error(rolePath, "unsupported ingredient role type");
            }
        }
        LuaValue products = definition.get("outputs").isnil()
                ? new LuaTable()
                : copy(table(definition.get("outputs"), path + ".outputs"));
        if (!definition.get("output").isnil()) {
            if (!products.get(type.primary).isnil()) {
                throw error(path + ".output", "duplicates primary output");
            }
            products.set(type.primary, definition.get("output"));
        }
        checkRoles(products, type.outputs, path + ".outputs");
        LuaTable normalizedOutputs = new LuaTable();
        for (RecipeTypes.Role role : type.outputs.values()) {
            LuaValue product = products.get(role.name);
            if (product.isnil()) {
                if (!role.optional) {
                    throw error(path + ".outputs." + role.name, "required role is missing");
                }
                continue;
            }
            String rolePath = path + ".outputs." + role.name;
            if (role.type == RecipeTypes.RoleValueType.ITEM) {
                ItemStack result = stack(product, false, rolePath);
                fits(result, rolePath);
                outputs.put(role.name, result);
                normalizedOutputs.set(role.name, stack(result));
            } else if (role.type == RecipeTypes.RoleValueType.ITEM_OUTPUT_POOL) {
                List<ItemStack> results = readOutputPool(product, rolePath);
                outputPools.put(role.name, results);
                normalizedOutputs.set(role.name, stackList(results));
            } else {
                throw error(rolePath, "unsupported output role type");
            }
        }
        // Explicit output remainders are deterministic, so impossible combinations fail
        // at declaration time.
        Map<String, ItemStack> combined = new LinkedHashMap<String, ItemStack>();
        for (Map.Entry<String, ItemStack> entry : outputs.entrySet()) {
            combined.put(entry.getKey(), entry.getValue().copy());
        }
        for (Ingredient ingredient : ingredients.values()) {
            addRemainder(combined, ingredient, 1, path);
        }
        for (PoolIngredients pool : ingredientPools.values()) {
            for (Ingredient ingredient : pool.requirements) {
                addRemainder(combined, ingredient, 1, path);
            }
        }
        for (GridIngredients grid : ingredientGrids.values()) {
            for (Map.Entry<Character, Ingredient> entry : grid.key.entrySet()) {
                int occurrences = 0;
                for (String row : grid.pattern) {
                    for (int column = 0; column < row.length(); column++) {
                        if (row.charAt(column) == entry.getKey().charValue()) {
                            occurrences++;
                        }
                    }
                }
                addRemainder(combined, entry.getValue(), occurrences, path);
            }
        }
        LuaValue suppliedData = definition.get("data").isnil() ? new LuaTable() : definition.get("data");
        data = new LuaTable();
        for (String key : keys(suppliedData, path + ".data")) {
            if (!type.data.containsKey(key)) {
                throw error(path + ".data." + key, "unknown field");
            }
        }
        for (Map.Entry<String, RecipeTypes.Field> field : type.data.entrySet()) {
            LuaValue v = suppliedData.get(field.getKey());
            if (v.isnil()) {
                v = field.getValue().getSchema().get("default");
            }
            data.set(field.getKey(), field.getValue().validate(v, path + ".data." + field.getKey()));
        }
        conditions = definition.get("conditions").isnil() ? new LuaTable() : copy(definition.get("conditions"));
        for (String key : keys(conditions, path + ".conditions")) {
            RecipeTypes.Field field = type.context.get(key);
            if (field == null) {
                throw error(path + ".conditions." + key, "unknown context field");
            }
            validatePredicate(field, conditions.get(key), path + ".conditions." + key);
        }
        priority = definition.get("priority").isnil() ? 0 : integer(definition.get("priority"), path + ".priority");
        enabled = bool(definition.get("enabled"), true, path + ".enabled");
        value.set("ingredients", normalizedInputs);
        value.set("outputs", normalizedOutputs);
        value.set("data", data);
        value.set("conditions", conditions);
        value.set("priority", priority);
        value.set("enabled", LuaValue.valueOf(enabled));
        fingerprint = fingerprint(value);
        this.ingredients = Collections.unmodifiableMap(ingredients);
    }

    private static void addRemainder(Map<String, ItemStack> combined, Ingredient ingredient, int occurrences,
            String path) {
        if (ingredient.remainderOutput == null) {
            return;
        }
        String role = ingredient.remainderOutput;
        ItemStack remainder = ingredient.remainder.copy();
        long count = (long) remainder.stackSize * ingredient.count * occurrences;
        if (count > 64) {
            throw error(path + ".outputs." + role, "remainder exceeds stack capacity");
        }
        remainder.stackSize = (int) count;
        ItemStack old = combined.get(role);
        if (old != null) {
            if (!old.isItemEqual(remainder)) {
                throw error(path + ".outputs." + role, "incompatible remainder and output");
            }
            remainder.stackSize += old.stackSize;
        }
        fits(remainder, path + ".outputs." + role);
        combined.put(role, remainder);
    }

    /** Returns independent output stacks in declaration order. */
    public Map<String, ItemStack> getOutputs() {
        Map<String, ItemStack> result = new LinkedHashMap<>();
        for (Map.Entry<String, ItemStack> output : outputs.entrySet()) {
            result.put(output.getKey(), output.getValue().copy());
        }
        return Collections.unmodifiableMap(result);
    }

    public ItemStack getOutput(String role) {
        ItemStack output = outputs.get(role);
        return output == null ? null : output.copy();
    }

    public Map<String, PoolIngredients> getIngredientPools() {
        return Collections.unmodifiableMap(ingredientPools);
    }

    public Map<String, GridIngredients> getIngredientGrids() {
        return Collections.unmodifiableMap(ingredientGrids);
    }

    public Map<String, List<ItemStack>> getOutputPools() {
        Map<String, List<ItemStack>> result = new LinkedHashMap<String, List<ItemStack>>();
        for (Map.Entry<String, List<ItemStack>> entry : outputPools.entrySet()) {
            List<ItemStack> stacks = new ArrayList<ItemStack>();
            for (ItemStack stack : entry.getValue()) {
                stacks.add(stack.copy());
            }
            result.put(entry.getKey(), Collections.unmodifiableList(stacks));
        }
        return Collections.unmodifiableMap(result);
    }

    public LuaValue getValue() {
        return copy(value);
    }

    public LuaValue getData() {
        return copy(data);
    }

    public LuaValue getConditions() {
        return copy(conditions);
    }

    public static void checkRoles(LuaValue values, Map<String, RecipeTypes.Role> roles, String path) {
        for (String key : keys(values, path)) {
            if (!roles.containsKey(key)) {
                throw error(path + "." + key, "unknown role; expected " + roles.keySet());
            }
        }
    }

    public static void validatePredicate(RecipeTypes.Field field, LuaValue predicate, String path) {
        if (!predicate.istable()) {
            field.validate(predicate, path);
            return;
        }
        if (!field.type.equals("integer") && !field.type.equals("number")) {
            throw error(path, "bounds require a numeric field");
        }
        fields(predicate, path, "min", "max");
        if (predicate.get("min").isnil() && predicate.get("max").isnil()) {
            throw error(path, "expected min or max");
        }
        for (String key : keys(predicate, path)) {
            number(predicate.get(key), path + "." + key);
        }
        if (!predicate.get("min").isnil() && !predicate.get("max").isnil()
                && predicate.get("min").todouble() > predicate.get("max").todouble()) {
            throw error(path, "min exceeds max");
        }
    }

    public static boolean predicate(LuaValue value, LuaValue test) {
        if (value.isnil()) {
            return false;
        }
        if (!test.istable()) {
            return value.raweq(test);
        }
        return (test.get("min").isnil() || value.todouble() >= test.get("min").todouble())
                && (test.get("max").isnil() || value.todouble() <= test.get("max").todouble());
    }

    public boolean matchesContext(LuaValue context) {
        for (String key : keys(conditions, "conditions")) {
            if (!predicate(context.get(key), conditions.get(key))) {
                return false;
            }
        }
        return true;
    }

    public static LuaValue context(RecipeTypes.Type type, LuaValue value) {
        if (value.isnil()) {
            value = new LuaTable();
        }
        for (String key : keys(value, "context")) {
            RecipeTypes.Field field = type.context.get(key);
            if (field == null) {
                throw error("context." + key, "unknown field");
            }
            field.validate(value.get(key), "context." + key);
        }
        return value;
    }

    public boolean matches(Map<String, ItemStack> inputs) {
        for (String role : type.ingredients.keySet()) {
            Ingredient ingredient = ingredients.get(role);
            ItemStack input = inputs.get(role);
            if (ingredient == null ? input != null : !ingredient.matches(input, true)) {
                return false;
            }
        }
        return enabled;
    }

    private static List<ItemStack> readOutputPool(LuaValue value, String path) {
        table(value, path);
        if (value.length() == 0) {
            throw error(path, "expected a nonempty output list");
        }
        validateDenseList(value, path);
        List<ItemStack> result = new ArrayList<ItemStack>();
        for (int i = 1; i <= value.length(); i++) {
            ItemStack stack = stack(value.get(i), false, path + "[" + i + "]");
            fits(stack, path + "[" + i + "]");
            result.add(stack);
        }
        return Collections.unmodifiableList(result);
    }

    private static LuaValue stackList(List<ItemStack> stacks) {
        LuaTable result = new LuaTable();
        for (int i = 0; i < stacks.size(); i++) {
            result.set(i + 1, stack(stacks.get(i)));
        }
        return result;
    }

    private static void validateDenseList(LuaValue value, String path) {
        LuaValue key = LuaValue.NIL;
        while (!(key = value.next(key).arg1()).isnil()) {
            int index = integer(key, path + " index");
            if (index < 1 || index > value.length()) {
                throw error(path, "expected a dense list");
            }
        }
    }

    public static final class PoolIngredients {
        public final List<Ingredient> requirements;
        private final LuaTable value = new LuaTable();

        PoolIngredients(LuaValue input, RecipeTypes.Role role, RecipeTypes.Type type, String path) {
            table(input, path);
            if (input.length() == 0) {
                throw error(path, "expected a nonempty ingredient list");
            }
            validateDenseList(input, path);
            List<Ingredient> requirements = new ArrayList<Ingredient>();
            for (int i = 1; i <= input.length(); i++) {
                Ingredient ingredient = new Ingredient(input.get(i), role, type, path + "[" + i + "]");
                requirements.add(ingredient);
                value.set(i, ingredient.value);
            }
            this.requirements = Collections.unmodifiableList(requirements);
        }
    }

    public static final class GridIngredients {
        public final List<String> pattern;
        public final Map<Character, Ingredient> key;
        private final LuaTable value = new LuaTable();

        GridIngredients(LuaValue input, RecipeTypes.Role role, RecipeTypes.Type type, String path) {
            fields(input, path, "pattern", "key", "ingredients");
            LuaValue patternValue = table(input.get("pattern"), path + ".pattern");
            validateDenseList(patternValue, path + ".pattern");
            List<String> pattern = readPattern(patternValue, role, path);
            LuaValue keyValue = input.get("key").isnil() ? input.get("ingredients") : input.get("key");
            if (!input.get("key").isnil() && !input.get("ingredients").isnil()) {
                throw error(path, "choose key or ingredients");
            }
            Map<Character, Ingredient> key = readKey(keyValue, pattern, role, type, path);
            this.pattern = Collections.unmodifiableList(pattern);
            this.key = Collections.unmodifiableMap(key);
            LuaTable normalizedPattern = new LuaTable();
            for (int i = 0; i < pattern.size(); i++) {
                normalizedPattern.set(i + 1, pattern.get(i));
            }
            LuaTable normalizedKey = new LuaTable();
            for (Map.Entry<Character, Ingredient> entry : key.entrySet()) {
                normalizedKey.set(String.valueOf(entry.getKey().charValue()), entry.getValue().value);
            }
            value.set("pattern", normalizedPattern);
            value.set("key", normalizedKey);
        }

        private static List<String> readPattern(LuaValue value, RecipeTypes.Role role, String path) {
            int height = value.length();
            if (height == 0 || height > role.grid.height) {
                throw error(path + ".pattern", "height exceeds the declared grid");
            }
            List<String> pattern = new ArrayList<String>();
            int width = -1;
            for (int i = 1; i <= height; i++) {
                String row = string(value.get(i), path + ".pattern[" + i + "]");
                if (width < 0) {
                    width = row.length();
                }
                if (row.length() != width || width == 0 || width > role.grid.width) {
                    throw error(path + ".pattern[" + i + "]", "expected equal nonempty rows within the grid width");
                }
                pattern.add(row);
            }
            if (!role.grid.allowSmaller && (width != role.grid.width || height != role.grid.height)) {
                throw error(path + ".pattern", "must fill the declared grid dimensions");
            }
            return pattern;
        }

        private static Map<Character, Ingredient> readKey(LuaValue value, List<String> pattern, RecipeTypes.Role role,
                RecipeTypes.Type type, String path) {
            table(value, path + ".key");
            Set<Character> used = new LinkedHashSet<Character>();
            for (String row : pattern) {
                for (int i = 0; i < row.length(); i++) {
                    if (row.charAt(i) != ' ') {
                        used.add(Character.valueOf(row.charAt(i)));
                    }
                }
            }
            if (used.isEmpty()) {
                throw error(path + ".pattern", "must contain at least one ingredient character");
            }
            Map<Character, Ingredient> result = new LinkedHashMap<Character, Ingredient>();
            for (String name : keys(value, path + ".key")) {
                if (name.length() != 1 || name.charAt(0) == ' ') {
                    throw error(path + ".key." + name, "expected one non-space character");
                }
                Character character = Character.valueOf(name.charAt(0));
                if (!used.contains(character)) {
                    throw error(path + ".key." + name, "character is not used by the pattern");
                }
                result.put(character, new Ingredient(value.get(name), role, type, path + ".key." + name));
            }
            for (Character character : used) {
                if (!result.containsKey(character)) {
                    throw error(path + ".key", "missing ingredient for character '" + character + "'");
                }
            }
            return result;
        }
    }

    public static final class Ingredient {
        private final List<ItemStack> alternatives = new ArrayList<ItemStack>();
        public final int count;
        public final boolean anyDamage;
        public final boolean consume;
        private final ItemStack remainder;
        public final String remainderOutput;
        private final LuaTable value = new LuaTable();
        Ingredient(LuaValue input, RecipeTypes.Role role, RecipeTypes.Type type, String path) {
            consume = role.consume;
            // Public bm.stack values contain both id and item; id identifies the stack
            // form.
            boolean descriptor = input.istable() && input.get("id").isnil()
                    && (!input.get("item").isnil() || !input.get("anyOf").isnil());
            if (input.istable() && !input.get("id").isnil()
                    && (!input.get("anyOf").isnil() || !input.get("remainder").isnil())) {
                throw error(path,
                        "use an item descriptor for alternatives or remainders; do not combine it with a stack id");
            }
            if (!descriptor) {
                ItemStack stack = stack(input, false, path);
                fits(stack, path);
                count = stack.stackSize;
                stack.stackSize = 1;
                alternatives.add(stack);
                anyDamage = false;
                remainder = null;
                remainderOutput = null;
            } else {
                fields(input, path, "item", "anyOf", "count", "damage", "remainder");
                if (!input.get("item").isnil() && !input.get("anyOf").isnil()) {
                    throw error(path, "choose item or anyOf");
                }
                count = input.get("count").isnil() ? 1 : integer(input.get("count"), path + ".count");
                if (count < 1) {
                    throw error(path + ".count", "expected positive integer");
                }
                LuaValue damage = input.get("damage");
                anyDamage = damage.type() == LuaValue.TSTRING && damage.tojstring().equals("any");
                if (!damage.isnil() && !anyDamage && integer(damage, path + ".damage") < 0) {
                    throw error(path + ".damage", "must be nonnegative");
                }
                if (!input.get("item").isnil()) {
                    addAlternative(input.get("item"), damage, path + ".item");
                } else {
                    LuaValue list = table(input.get("anyOf"), path + ".anyOf");
                    if (list.length() == 0) {
                        throw error(path + ".anyOf", "expected nonempty list");
                    }
                    LuaValue key = LuaValue.NIL;
                    while (!(key = list.next(key).arg1()).isnil()) {
                        int i = integer(key, path + ".anyOf index");
                        if (i < 1 || i > list.length()) {
                            throw error(path + ".anyOf", "expected dense list");
                        }
                    }
                    for (int i = 1; i <= list.length(); i++) {
                        addAlternative(list.get(i), damage, path + ".anyOf[" + i + "]");
                    }
                }
                LuaValue rem = input.get("remainder");
                if (!rem.isnil()) {
                    if (!consume) {
                        throw error(path + ".remainder", "retained ingredients cannot emit remainders");
                    }
                    fields(rem, path + ".remainder", "output", "stack");
                    remainderOutput = string(rem.get("output"), path + ".remainder.output");
                    RecipeTypes.Role outputRole = type.outputs.get(remainderOutput);
                    if (outputRole == null) {
                        throw error(path + ".remainder.output", "unknown output role");
                    }
                    if (outputRole.type != RecipeTypes.RoleValueType.ITEM) {
                        throw error(path + ".remainder.output", "remainders require a single item output role");
                    }
                    remainder = stack(rem.get("stack"), false, path + ".remainder.stack");
                    fits(remainder, path + ".remainder.stack");
                } else {
                    remainder = null;
                    remainderOutput = null;
                }
            }
            Collections.sort(alternatives, new Comparator<ItemStack>() {
                public int compare(ItemStack a, ItemStack b) {
                    return a.itemID != b.itemID ? a.itemID - b.itemID : a.getItemDamage() - b.getItemDamage();
                }
            });
            LuaTable list = new LuaTable();
            for (int i = 0; i < alternatives.size(); i++) {
                list.set(i + 1, stack(alternatives.get(i)));
            }
            value.set("anyOf", list);
            value.set("count", count);
            if (anyDamage) {
                value.set("damage", "any");
            }
            if (remainder != null) {
                LuaTable rem = new LuaTable();
                rem.set("output", remainderOutput);
                rem.set("stack", stack(remainder));
                value.set("remainder", rem);
            }
        }

        /**
         * Returns independent alternatives without exposing the matching definition.
         */
        public List<ItemStack> getAlternatives() {
            List<ItemStack> result = new ArrayList<>();
            for (ItemStack alternative : alternatives) {
                result.add(alternative.copy());
            }
            return Collections.unmodifiableList(result);
        }

        public ItemStack getRemainder() {
            return remainder == null ? null : remainder.copy();
        }

        private void addAlternative(LuaValue value, LuaValue damage, String path) {
            ItemStack stack = stack(value, false, path);
            if (stack.stackSize != 1) {
                throw error(path, "alternatives must be item references or IDs, without quantities");
            }
            if (!damage.isnil()) {
                stack.setItemDamage(anyDamage ? 0 : integer(damage, path + ".damage"));
            }
            ItemStack quantity = stack.copy();
            quantity.stackSize = count;
            fits(quantity, path);
            for (ItemStack old : alternatives) {
                if (old.isItemEqual(stack)) {
                    return;
                }
            }
            alternatives.add(stack);
        }

        public boolean matches(ItemStack input, boolean quantity) {
            if (input == null || quantity && input.stackSize < count) {
                return false;
            }
            for (ItemStack item : alternatives) {
                if (item.itemID == input.itemID && (anyDamage || item.getItemDamage() == input.getItemDamage())) {
                    return true;
                }
            }
            return false;
        }
    }
}
