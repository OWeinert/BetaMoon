package betamoon.recipes.custom;

import betamoon.BetaMoonCommon;

import betamoon.luaapi.resource.RecipeTarget;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.recipes.custom.RecipeValues.*;

/** Owner-scoped custom registrations behind the public recipe registry. */
public final class CustomRecipes {
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<String, Entry>();
    private static final Map<RecipeTypes.Type, Index> INDEXES = new LinkedHashMap<RecipeTypes.Type, Index>();
    private static long sequence;
    private CustomRecipes() {
    }

    public static Entry add(LuaValue definition) {
        String owner = RecipeTypes.owner();
        RecipeTypes.Type type = RecipeTypes.get(definition.get("type"), true);
        if (type.builtin) {
            throw error(type.name, "use the native recipe adapter");
        }
        String key = definition.get("key").isnil()
                ? RecipeTypes.qualify("_generated_recipe_" + sequence++)
                : RecipeTypes.qualify(string(definition.get("key"), "recipes:add.key"));
        if (key.startsWith("minecraft:")) {
            throw error(key, "reserved namespace");
        }
        if (ENTRIES.containsKey(key)) {
            throw error(key, "duplicate recipe key; use an override");
        }
        final Entry entry = new Entry(key, owner, type, new RecipeDefinition(type, definition, "recipes:add " + key),
                sequence++);
        for (Entry old : ENTRIES.values()) {
            if (old.type == type && old.effective.priority == entry.effective.priority
                    && canonical(old.effective.getValue().get("ingredients"))
                            .equals(canonical(entry.effective.getValue().get("ingredients")))
                    && canonical(old.effective.getConditions()).equals(canonical(entry.effective.getConditions()))) {
                BetaMoonCommon.LOGGER
                        .warning("Overlapping recipes " + old.key + " and " + key + "; use explicit recipe priority.");
            }
        }
        ENTRIES.put(key, entry);
        type.revision++;
        ScriptResourceTracker.trackOwned(entry);
        ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
            public void run() {
                if (ENTRIES.get(entry.key) == entry) {
                    ENTRIES.remove(entry.key);
                }
                entry.exists = false;
                entry.type.revision++;
                for (Patch patch : entry.patches) {
                    patch.active = false;
                    patch.reason = "target registration was removed";
                }
                entry.patches.clear();
            }
        });
        return entry;
    }

    public static Entry get(String key) {
        return ENTRIES.get(key);
    }

    public static List<Entry> all() {
        return new ArrayList<Entry>(ENTRIES.values());
    }

    /**
     * Index one discriminating role by item ID; full matching verifies all roles
     * and metadata.
     */
    private static final class Index {
        long revision = -1;
        final Map<Integer, List<Entry>> buckets = new LinkedHashMap<Integer, List<Entry>>();
        final List<Entry> fallback = new ArrayList<Entry>();
    }

    public static List<Entry> candidates(RecipeTypes.Type type, Map<String, ItemStack> inputs) {
        Set<Integer> ids = new LinkedHashSet<Integer>();
        for (ItemStack input : inputs.values()) {
            if (input != null) {
                ids.add(Integer.valueOf(input.itemID));
            }
        }
        return candidates(type, ids);
    }

    public static List<Entry> candidates(RecipeTypes.Type type, Set<Integer> inputIds) {
        Index index = INDEXES.get(type);
        if (index == null) {
            index = new Index();
            INDEXES.put(type, index);
        }
        if (index.revision != type.revision) {
            index.buckets.clear();
            index.fallback.clear();
            for (Entry entry : ENTRIES.values()) {
                if (entry.type == type && entry.effective.enabled) {
                    Set<Integer> ids = ingredientIds(entry.effective);
                    if (ids.isEmpty()) {
                        index.fallback.add(entry);
                    }
                    for (Integer id : ids) {
                        List<Entry> bucket = index.buckets.get(id);
                        if (bucket == null) {
                            bucket = new ArrayList<Entry>();
                            index.buckets.put(id, bucket);
                        }
                        bucket.add(entry);
                    }
                }
            }
            for (List<Entry> bucket : index.buckets.values()) {
                sortCandidates(bucket);
            }
            sortCandidates(index.fallback);
            index.revision = type.revision;
        }
        Set<Entry> unique = new LinkedHashSet<Entry>(index.fallback);
        for (Integer id : inputIds) {
            List<Entry> bucket = index.buckets.get(id);
            if (bucket != null) {
                unique.addAll(bucket);
            }
        }
        List<Entry> result = new ArrayList<Entry>(unique);
        sortCandidates(result);
        return result;
    }

    private static Set<Integer> ingredientIds(RecipeDefinition definition) {
        Set<Integer> result = new LinkedHashSet<Integer>();
        for (RecipeDefinition.Ingredient ingredient : definition.ingredients.values()) {
            addIngredientIds(result, ingredient);
        }
        for (RecipeDefinition.PoolIngredients pool : definition.getIngredientPools().values()) {
            for (RecipeDefinition.Ingredient ingredient : pool.requirements) {
                addIngredientIds(result, ingredient);
            }
        }
        for (RecipeDefinition.GridIngredients grid : definition.getIngredientGrids().values()) {
            for (RecipeDefinition.Ingredient ingredient : grid.key.values()) {
                addIngredientIds(result, ingredient);
            }
        }
        return result;
    }

    private static void addIngredientIds(Set<Integer> result, RecipeDefinition.Ingredient ingredient) {
        for (ItemStack stack : ingredient.getAlternatives()) {
            result.add(Integer.valueOf(stack.itemID));
        }
    }

    private static void sortCandidates(List<Entry> candidates) {
        Collections.sort(candidates, new Comparator<Entry>() {
            public int compare(Entry a, Entry b) {
                if (a.effective.priority != b.effective.priority) {
                    return a.effective.priority > b.effective.priority ? -1 : 1;
                }
                return a.order < b.order ? -1 : a.order == b.order ? 0 : 1;
            }
        });
    }

    public static boolean matchesType(RecipeTypes.Type type, LuaValue criterion) {
        if (criterion.isnil()) {
            return true;
        }
        if (criterion.istable() && !(criterion instanceof RecipeTypes.Reference)) {
            for (int i = 1; i <= criterion.length(); i++) {
                if (matchesType(type, criterion.get(i))) {
                    return true;
                }
            }
            return false;
        }
        return type.name.equals(RecipeTypes.name(criterion));
    }

    /** Validate query syntax even when the registry has no candidates. */
    public static void validateQuery(LuaValue query) {
        fields(query, "recipe query", "type", "input", "output", "ingredients", "outputs", "data", "owner", "enabled");
        bool(query.get("enabled"), true, "recipe query.enabled");
        if (!query.get("owner").isnil()) {
            string(query.get("owner"), "recipe query.owner");
        }
        LuaValue types = query.get("type");
        if (!types.isnil()) {
            if (types.istable() && !(types instanceof RecipeTypes.Reference)) {
                LuaValue key = LuaValue.NIL;
                while (!(key = types.next(key).arg1()).isnil()) {
                    int i = integer(key, "recipe query.type index");
                    if (i < 1 || i > types.length()) {
                        throw error("recipe query.type", "expected a dense list");
                    }
                    RecipeTypes.name(types.get(i));
                }
            } else {
                RecipeTypes.name(types);
            }
        }
        for (String field : new String[]{"input", "output"}) {
            if (!query.get(field).isnil()) {
                outputMatches(null, query.get(field));
            }
        }
        for (String field : new String[]{"ingredients", "outputs"}) {
            if (!query.get(field).isnil()) {
                for (String role : keys(query.get(field), "recipe query." + field)) {
                    outputMatches(null, query.get(field).get(role));
                }
            }
        }
        if (!query.get("data").isnil()) {
            for (String field : keys(query.get("data"), "recipe query.data")) {
                LuaValue predicate = query.get("data").get(field);
                if (predicate.istable()) {
                    fields(predicate, "recipe query.data." + field, "min", "max");
                    if (predicate.get("min").isnil() && predicate.get("max").isnil()) {
                        throw error("recipe query.data." + field, "expected min or max");
                    }
                    for (String bound : keys(predicate, "recipe query.data." + field)) {
                        number(predicate.get(bound), "recipe query.data." + field + "." + bound);
                    }
                    if (!predicate.get("min").isnil() && !predicate.get("max").isnil()
                            && predicate.get("min").todouble() > predicate.get("max").todouble()) {
                        throw error("recipe query.data." + field, "min exceeds max");
                    }
                } else if (predicate.type() == LuaValue.TNUMBER) {
                    number(predicate, "recipe query.data." + field);
                } else if (predicate.type() != LuaValue.TSTRING && !predicate.isboolean()) {
                    throw error("recipe query.data." + field, "expected primitive or bounds");
                }
            }
        }
    }

    public static boolean matchesQuery(Entry entry, LuaValue query) {
        RecipeDefinition def = entry.effective;
        if (!matchesType(entry.type, query.get("type"))) {
            return false;
        }
        if (def.enabled != bool(query.get("enabled"), true, "recipe query.enabled")) {
            return false;
        }
        if (!query.get("owner").isnil() && !entry.owner.equals(string(query.get("owner"), "recipe query.owner"))) {
            return false;
        }
        if (!query.get("output").isnil() && !outputMatches(def.getOutput(entry.type.primary), query.get("output"))) {
            return false;
        }
        if (!query.get("input").isnil()) {
            if (!anyIngredientMatches(def, query.get("input"))) {
                return false;
            }
        }
        LuaValue inputs = query.get("ingredients");
        if (!inputs.isnil()) {
            for (String role : keys(inputs, "recipe query.ingredients")) {
                if (!roleIngredientMatches(def, role, inputs.get(role))) {
                    return false;
                }
            }
        }
        LuaValue outputs = query.get("outputs");
        if (!outputs.isnil()) {
            for (String role : keys(outputs, "recipe query.outputs")) {
                if (!roleOutputMatches(def, role, outputs.get(role))) {
                    return false;
                }
            }
        }
        LuaValue data = query.get("data");
        if (!data.isnil()) {
            for (String field : keys(data, "recipe query.data")) {
                if (!entry.type.data.containsKey(field)) {
                    return false;
                }
                RecipeDefinition.validatePredicate(entry.type.data.get(field), data.get(field),
                        "recipe query.data." + field);
                if (!RecipeDefinition.predicate(def.getData().get(field), data.get(field))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean anyIngredientMatches(RecipeDefinition definition, LuaValue criterion) {
        for (String role : definition.type.ingredients.keySet()) {
            if (roleIngredientMatches(definition, role, criterion)) {
                return true;
            }
        }
        return false;
    }

    private static boolean roleIngredientMatches(RecipeDefinition definition, String role, LuaValue criterion) {
        RecipeDefinition.Ingredient item = definition.ingredients.get(role);
        if (item != null) {
            return ingredientMatches(item, criterion);
        }
        RecipeDefinition.PoolIngredients pool = definition.getIngredientPools().get(role);
        if (pool != null) {
            for (RecipeDefinition.Ingredient ingredient : pool.requirements) {
                if (ingredientMatches(ingredient, criterion)) {
                    return true;
                }
            }
        }
        RecipeDefinition.GridIngredients grid = definition.getIngredientGrids().get(role);
        if (grid != null) {
            for (RecipeDefinition.Ingredient ingredient : grid.key.values()) {
                if (ingredientMatches(ingredient, criterion)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean roleOutputMatches(RecipeDefinition definition, String role, LuaValue criterion) {
        ItemStack item = definition.getOutput(role);
        if (item != null) {
            return outputMatches(item, criterion);
        }
        List<ItemStack> pool = definition.getOutputPools().get(role);
        if (pool != null) {
            for (ItemStack output : pool) {
                if (outputMatches(output, criterion)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean anyDamage(LuaValue criterion) {
        return criterion.istable() && criterion.get("damage").type() == LuaValue.TSTRING
                && criterion.get("damage").tojstring().equals("any");
    }

    public static ItemStack queryStack(LuaValue criterion) {
        LuaValue v = copy(criterion);
        if (anyDamage(criterion)) {
            v.set("damage", LuaValue.ZERO);
        }
        if (v.istable() && !v.get("item").isnil()) {
            LuaValue item = v.get("item");
            ItemStack base = stack(item, true, "query.item");
            v.set("id", base.itemID);
            if (v.get("damage").isnil()) {
                v.set("damage", base.getItemDamage());
            }
        }
        return stack(v, true, "recipe query stack");
    }

    public static boolean outputMatches(ItemStack actual, LuaValue criterion) {
        ItemStack expected = queryStack(criterion);
        return actual != null && actual.itemID == expected.itemID
                && (anyDamage(criterion) || actual.getItemDamage() == expected.getItemDamage())
                && (expected.stackSize == 0 || actual.stackSize == expected.stackSize);
    }

    private static boolean ingredientMatches(RecipeDefinition.Ingredient ingredient, LuaValue value) {
        LuaValue query = value.istable() ? copy(value) : stack(stack(value, true, "recipe query.input"));
        query.set("count", 0);
        // Validate even if the candidate has no such optional ingredient.
        outputMatches(null, query);
        if (ingredient == null) {
            return false;
        }
        if (ingredient.anyDamage) {
            query.set("damage", "any");
        }
        for (ItemStack alternative : ingredient.getAlternatives()) {
            if (outputMatches(alternative, query)) {
                return true;
            }
        }
        return false;
    }
    public static final class Entry {
        public final String key;
        public final String owner;
        public final RecipeTypes.Type type;
        public final long order;
        public final RecipeDefinition base;
        public RecipeDefinition effective;
        public boolean exists = true;
        public long revision;
        private final List<Patch> patches = new ArrayList<Patch>();
        public final Reference reference;
        Entry(String key, String owner, RecipeTypes.Type type, RecipeDefinition base, long order) {
            this.key = key;
            this.owner = owner;
            this.type = type;
            this.base = base;
            effective = base;
            this.order = order;
            reference = new Reference(this);
        }

        void recompute(boolean adding) {
            Collections.sort(patches, new Comparator<Patch>() {
                public int compare(Patch a, Patch b) {
                    return a.priority != b.priority
                            ? (a.priority < b.priority ? -1 : 1)
                            : a.order < b.order ? -1 : a.order == b.order ? 0 : 1;
                }
            });
            LuaValue current = base.getValue();
            for (Patch patch : patches) {
                if (patch.removed) {
                    continue;
                }
                LuaValue candidate = copy(current);
                for (String property : keys(patch.changes, "changes")) {
                    if (property.equals("output")) {
                        candidate.get("outputs").set(type.primary, patch.changes.get(property));
                    } else {
                        candidate.set(property, patch.changes.get(property));
                    }
                }
                try {
                    RecipeDefinition checked = new RecipeDefinition(type, candidate, "recipe override " + key);
                    current = checked.getValue();
                    patch.active = true;
                    patch.reason = null;
                } catch (LuaError error) {
                    if (adding) {
                        throw error;
                    }
                    patch.active = false;
                    patch.reason = error.getMessage();
                }
            }
            RecipeDefinition next = new RecipeDefinition(type, current, "recipe override " + key);
            if (!next.fingerprint.equals(effective.fingerprint)) {
                effective = next;
                revision++;
                type.revision++;
            }
        }
    }
    public static final class Reference extends LuaTable implements RecipeTarget {
        public final Entry entry;
        Reference(final Entry entry) {
            this.entry = entry;
            set("override", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    return override(args.arg(args.arg1() == Reference.this ? 2 : 1));
                }
            });
            set("disable", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    LuaTable changes = new LuaTable();
                    changes.set("enabled", LuaValue.FALSE);
                    return override(changes);
                }
            });
        }

        public LuaValue get(String key) {
            return get(LuaValue.valueOf(key));
        }

        public LuaValue get(LuaValue key) {
            String name = key.tojstring();
            if (name.equals("key")) {
                return LuaValue.valueOf(entry.key);
            }
            if (name.equals("owner")) {
                return LuaValue.valueOf(entry.owner);
            }
            if (name.equals("type")) {
                return LuaValue.valueOf(entry.type.name);
            }
            if (name.equals("recipeType")) {
                return entry.type.reference;
            }
            if (name.equals("exists")) {
                return LuaValue.valueOf(entry.exists);
            }
            if (name.equals("output")) {
                return stack(entry.effective.getOutput(entry.type.primary));
            }
            LuaValue value = entry.effective.getValue().get(key);
            return value.isnil() ? super.get(key) : copy(value);
        }

        @Override
        public LuaValue override(LuaValue definition) {
            RecipeTypes.owner();
            table(definition, "recipe override");
            if (!entry.exists) {
                throw RecipeValues.error(entry.key, "recipe registration no longer exists");
            }
            LuaValue when = definition.get("when");
            String reason = null;
            if (!when.isnil()) {
                fields(when, "recipe override.when", "owner", "type", "properties");
                if (!when.get("type").isnil() && !matchesType(entry.type, when.get("type"))) {
                    reason = "recipe type did not match";
                }
                if (!when.get("owner").isnil() && !entry.owner.equals(string(when.get("owner"), "when.owner"))) {
                    reason = "owner did not match";
                }
                LuaValue props = when.get("properties");
                if (!props.isnil()) {
                    for (String key : RecipeValues.keys(props, "when.properties")) {
                        LuaValue actual = get(key);
                        if (actual.isnil()) {
                            throw RecipeValues.error("when.properties." + key, "unknown recipe property");
                        }
                        boolean same = key.equals("output")
                                ? outputMatches(entry.effective.getOutput(entry.type.primary), props.get(key))
                                : canonical(actual).equals(canonical(props.get(key)));
                        if (!same) {
                            reason = "property '" + key + "' did not match";
                        }
                    }
                }
            }
            LuaValue changes = definition.get("changes");
            if (changes.isnil()) {
                changes = copy(definition);
                changes.set("when", LuaValue.NIL);
                changes.set("target", LuaValue.NIL);
                changes.set("priority", LuaValue.NIL);
            } else {
                fields(definition, "recipe override", "when", "changes", "priority", "target");
            }
            fields(changes, "recipe override.changes", "ingredients", "outputs", "output", "data", "conditions",
                    "enabled", "priority");
            if (!changes.get("outputs").isnil() && !changes.get("output").isnil()) {
                throw RecipeValues.error("recipe override", "choose output or outputs");
            }
            final Patch patch = new Patch(entry, copy(changes),
                    definition.get("priority").isnil() ? 0 : integer(definition.get("priority"), "override.priority"));
            if (reason != null) {
                patch.active = false;
                patch.removed = true;
                patch.reason = reason;
                return patch;
            }
            entry.patches.add(patch);
            try {
                entry.recompute(true);
            } catch (LuaError error) {
                entry.patches.remove(patch);
                entry.recompute(false);
                throw error;
            }
            ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
                public void run() {
                    patch.remove();
                }
            });
            return patch;
        }
    }
    private static final class Patch extends LuaTable {
        final Entry entry;
        final LuaValue changes;
        final int priority;
        final long order = sequence++;
        boolean active = true;
        boolean removed;
        String reason;
        Patch(Entry entry, LuaValue changes, int priority) {
            this.entry = entry;
            this.changes = changes;
            this.priority = priority;
            set("target", entry.reference);
            set("remove", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    remove();
                    return NIL;
                }
            });
        }

        void remove() {
            if (removed) {
                return;
            }
            removed = true;
            active = false;
            entry.patches.remove(this);
            if (entry.exists) {
                entry.recompute(false);
            }
        }

        public LuaValue get(String key) {
            return get(LuaValue.valueOf(key));
        }

        public LuaValue get(LuaValue key) {
            if (key.tojstring().equals("active")) {
                return LuaValue.valueOf(active && entry.exists);
            }
            if (key.tojstring().equals("reason")) {
                return reason == null ? LuaValue.NIL : LuaValue.valueOf(reason);
            }
            return super.get(key);
        }
    }
}
