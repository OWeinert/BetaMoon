package betamoon.recipes.custom;

import betamoon.luamodloader.LuaScriptRegistry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import static betamoon.recipes.custom.RecipeValues.*;

/**
 * Retained, callback-free recipe schemas. Recipe entries have a shorter
 * lifetime.
 */
public final class RecipeTypes {
    private static final Map<String, Type> TYPES = new LinkedHashMap<String, Type>();
    static {
        addBuiltIn("shaped", new String[][]{{"pattern", RoleValueType.STRING_LIST.schemaName},
                {"ingredients", RoleValueType.CHARACTER_ITEM_MAP.schemaName}});
        addBuiltIn("shapeless", new String[][]{{"ingredients", RoleValueType.ITEM_LIST.schemaName}});
        addBuiltIn("smelting", new String[][]{{"input", RoleValueType.ITEM.schemaName}});
    }

    private RecipeTypes() {
    }

    private static void addBuiltIn(String name, String[][] inputs) {
        LuaTable definition = new LuaTable();
        definition.set("name", "minecraft:" + name);
        LuaTable ingredients = new LuaTable();
        for (String[] input : inputs) {
            LuaTable role = new LuaTable();
            role.set("type", input[1]);
            ingredients.set(input[0], role);
        }
        definition.set("ingredients", ingredients);
        LuaTable outputs = new LuaTable();
        outputs.set("output", new LuaTable());
        definition.set("outputs", outputs);
        Type type = new Type(definition, "minecraft", true);
        TYPES.put(type.name, type);
    }

    public static String owner() {
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) {
            throw error("recipe registration", "must run while a script is loading");
        }
        return owner;
    }

    public static String qualify(String name) {
        String result = name;
        if (name.indexOf(':') < 0) {
            String owner = owner();
            int dot = owner.lastIndexOf('.');
            result = (dot > 0 ? owner.substring(0, dot) : owner).replaceAll("[^A-Za-z0-9_]", "_") + ":" + name;
        }
        result = result.toLowerCase(Locale.ROOT);
        if (!result.matches("[a-z0-9_]+:[a-z0-9_./-]+")) {
            throw error("name", "invalid qualified name '" + result + "'");
        }
        return result;
    }

    public static String name(LuaValue value) {
        if (value instanceof Reference) {
            return ((Reference) value).type.name;
        }
        String name = string(value, "recipe type").toLowerCase(Locale.ROOT);
        if (name.equals("shaped") || name.equals("shapeless") || name.equals("smelting")) {
            return "minecraft:" + name;
        }
        return qualify(name);
    }

    public static Type get(LuaValue value, boolean required) {
        String name = name(value);
        Type type = TYPES.get(name);
        if (type == null && required) {
            throw error("recipe type", "unknown type '" + name + "'");
        }
        return type;
    }

    public static Iterable<Type> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    public static Type add(LuaValue definition) {
        fields(definition, "recipeTypes:add", "name", "displayName", "ingredients", "outputs", "primaryOutput", "data",
                "context", "matcher");
        String owner = owner();
        Type next = new Type(definition, owner, false);
        Type old = TYPES.get(next.name);
        if (next.name.startsWith("minecraft:")) {
            throw error(next.name, "reserved namespace");
        }
        if (old != null) {
            if (!old.owner.equals(owner)) {
                throw error(next.name, "already owned by " + old.owner);
            }
            if (!canonical(old.definition).equals(canonical(next.definition))) {
                String path = difference(old.definition, next.definition, next.name);
                throw error(path, "schema changed after registration; restart Minecraft");
            }
            return old;
        }
        TYPES.put(next.name, next);
        return next;
    }

    static void matcherChanged(String matcherName) {
        for (Type type : TYPES.values()) {
            if (matcherName.equals(type.matcherName)) {
                type.revision++;
            }
        }
    }

    private static String difference(LuaValue a, LuaValue b, String path) {
        if (a.istable() && b.istable()) {
            if (a.length() > 0 || b.length() > 0) {
                return path;
            }
            for (String key : keys(a, path)) {
                if (!canonical(a.get(key)).equals(canonical(b.get(key)))) {
                    return difference(a.get(key), b.get(key), path + "." + key);
                }
            }
            for (String key : keys(b, path)) {
                if (a.get(key).isnil()) {
                    return path + "." + key;
                }
            }
        }
        return path;
    }
    public enum RoleValueType {
        ITEM("item"), ITEM_POOL("item_pool"), ITEM_GRID("item_grid"), ITEM_OUTPUT_POOL("item_output_pool"), ITEM_LIST(
                "list<item>"), STRING_LIST("list<string>"), CHARACTER_ITEM_MAP("map<char, item>");

        public final String schemaName;

        RoleValueType(String schemaName) {
            this.schemaName = schemaName;
        }

        static RoleValueType fromSchemaName(String schemaName) {
            for (RoleValueType type : values()) {
                if (type.schemaName.equals(schemaName)) {
                    return type;
                }
            }
            return null;
        }
    }

    public static final class Role {
        public final String name;
        public final RoleValueType type;
        public final boolean optional;
        public final boolean consume;
        public final PoolOptions pool;
        public final GridOptions grid;

        Role(String name, LuaValue def, boolean input, boolean builtIn, String path) {
            fields(def, path, "type", "optional", "consume", "allowExtra", "width", "height", "allowSmaller",
                    "placement", "transformations", "emptyCells");
            String schemaName = def.get("type").isnil()
                    ? RoleValueType.ITEM.schemaName
                    : string(def.get("type"), path + ".type");
            type = RoleValueType.fromSchemaName(schemaName);
            if (type == null) {
                throw error(path + ".type", "unsupported role value type '" + schemaName + "'");
            }
            if (!input && type != RoleValueType.ITEM && type != RoleValueType.ITEM_OUTPUT_POOL) {
                throw error(path + ".type", "outputs must use item or item_output_pool");
            }
            if (input && !builtIn && type != RoleValueType.ITEM && type != RoleValueType.ITEM_POOL
                    && type != RoleValueType.ITEM_GRID) {
                throw error(path + ".type", "ingredients must use item, item_pool, or item_grid");
            }
            this.name = name;
            optional = bool(def.get("optional"), false, path + ".optional");
            consume = bool(def.get("consume"), true, path + ".consume");
            pool = type == RoleValueType.ITEM_POOL ? new PoolOptions(def, path) : null;
            grid = type == RoleValueType.ITEM_GRID ? new GridOptions(def, path) : null;
            validateUnusedOptions(def, path, input);
        }

        LuaValue view(boolean input) {
            LuaTable out = new LuaTable();
            out.set("type", type.schemaName);
            out.set("optional", LuaValue.valueOf(optional));
            if (input) {
                out.set("consume", LuaValue.valueOf(consume));
            }
            if (pool != null) {
                out.set("allowExtra", LuaValue.valueOf(pool.allowExtra));
            }
            if (grid != null) {
                out.set("width", grid.width);
                out.set("height", grid.height);
                out.set("allowSmaller", LuaValue.valueOf(grid.allowSmaller));
                out.set("placement", grid.placement);
                out.set("transformations", copy(grid.transformations));
                out.set("emptyCells", grid.emptyCells);
            }
            return out;
        }

        private void validateUnusedOptions(LuaValue def, String path, boolean input) {
            if (!input && !def.get("consume").isnil()) {
                throw error(path + ".consume", "only ingredient roles can define consumption");
            }
            if (type != RoleValueType.ITEM_POOL && !def.get("allowExtra").isnil()) {
                throw error(path + ".allowExtra", "requires type = 'item_pool'");
            }
            if (type == RoleValueType.ITEM_GRID) {
                return;
            }
            for (String option : new String[]{"width", "height", "allowSmaller", "placement", "transformations",
                    "emptyCells"}) {
                if (!def.get(option).isnil()) {
                    throw error(path + "." + option, "requires type = 'item_grid'");
                }
            }
        }
    }

    public static final class PoolOptions {
        public final boolean allowExtra;

        PoolOptions(LuaValue definition, String path) {
            allowExtra = bool(definition.get("allowExtra"), false, path + ".allowExtra");
        }
    }

    public static final class GridOptions {
        public final int width;
        public final int height;
        public final boolean allowSmaller;
        public final String placement;
        public final LuaValue transformations;
        public final String emptyCells;

        GridOptions(LuaValue definition, String path) {
            width = dimension(definition.get("width"), path + ".width");
            height = dimension(definition.get("height"), path + ".height");
            allowSmaller = bool(definition.get("allowSmaller"), true, path + ".allowSmaller");
            placement = definition.get("placement").isnil()
                    ? "anywhere"
                    : string(definition.get("placement"), path + ".placement");
            if (!"anywhere".equals(placement) && !"fixed".equals(placement)) {
                throw error(path + ".placement", "expected 'anywhere' or 'fixed'");
            }
            emptyCells = definition.get("emptyCells").isnil()
                    ? "required"
                    : string(definition.get("emptyCells"), path + ".emptyCells");
            if (!"required".equals(emptyCells) && !"ignored".equals(emptyCells)) {
                throw error(path + ".emptyCells", "expected 'required' or 'ignored'");
            }
            transformations = readTransformations(definition.get("transformations"), path + ".transformations");
        }

        private static int dimension(LuaValue value, String path) {
            int result = integer(value, path);
            if (result < 1 || result > 16) {
                throw error(path, "expected an integer from 1 to 16");
            }
            return result;
        }

        private static LuaValue readTransformations(LuaValue value, String path) {
            LuaTable result = new LuaTable();
            if (value.isnil()) {
                return result;
            }
            table(value, path);
            LinkedHashSet<String> seen = new LinkedHashSet<String>();
            for (int i = 1; i <= value.length(); i++) {
                String transformation = string(value.get(i), path + "[" + i + "]");
                if (!transformation.equals("mirror_horizontal") && !transformation.equals("mirror_vertical")
                        && !transformation.equals("rotate_90") && !transformation.equals("rotate_180")
                        && !transformation.equals("rotate_270")) {
                    throw error(path + "[" + i + "]", "unknown grid transformation '" + transformation + "'");
                }
                seen.add(transformation);
            }
            LuaValue key = LuaValue.NIL;
            while (!(key = value.next(key).arg1()).isnil()) {
                int index = integer(key, path + " index");
                if (index < 1 || index > value.length()) {
                    throw error(path, "expected a dense list");
                }
            }
            int index = 1;
            for (String transformation : seen) {
                result.set(index++, transformation);
            }
            return result;
        }
    }
    public static final class Field {
        private final LuaValue schema;
        public final String type;
        Field(LuaValue value, String path, boolean context) {
            fields(value, path,
                    context
                            ? new String[]{"type", "min", "max", "values"}
                            : new String[]{"type", "min", "max", "values", "default"});
            type = string(value.get("type"), path + ".type");
            if (!type.equals("integer") && !type.equals("number") && !type.equals("boolean")
                    && !type.equals("string")) {
                throw error(path + ".type", "unsupported primitive type");
            }
            schema = copy(value);
            boolean numeric = type.equals("integer") || type.equals("number");
            for (String bound : new String[]{"min", "max"}) {
                if (!schema.get(bound).isnil()) {
                    if (!numeric) {
                        throw error(path + "." + bound, "only numeric fields accept bounds");
                    }
                    number(schema.get(bound), path + "." + bound);
                }
            }
            if (!schema.get("min").isnil() && !schema.get("max").isnil()
                    && schema.get("min").todouble() > schema.get("max").todouble()) {
                throw error(path, "min exceeds max");
            }
            LuaValue values = schema.get("values");
            if (!values.isnil()) {
                table(values, path + ".values");
                if (values.length() == 0) {
                    throw error(path + ".values", "expected nonempty list");
                }
                TreeMap<String, LuaValue> normalized = new TreeMap<String, LuaValue>();
                for (int i = 1; i <= values.length(); i++) {
                    validate(values.get(i), path + ".values[" + i + "]");
                    normalized.put(canonical(values.get(i)), values.get(i));
                }
                LuaValue key = LuaValue.NIL;
                while (!(key = values.next(key).arg1()).isnil()) {
                    int index = integer(key, path + ".values index");
                    if (index < 1 || index > values.length()) {
                        throw error(path + ".values", "expected a dense list");
                    }
                }
                LuaTable list = new LuaTable();
                int i = 1;
                for (LuaValue item : normalized.values()) {
                    list.set(i++, item);
                }
                schema.set("values", list);
            }
            if (!schema.get("default").isnil()) {
                validate(schema.get("default"), path + ".default");
            }
        }

        public LuaValue getSchema() {
            return copy(schema);
        }

        private void checkPrimitive(LuaValue value, String path) {
            if (type.equals("integer")) {
                integer(value, path);
            } else if (type.equals("number")) {
                number(value, path);
            } else if (type.equals("string")) {
                string(value, path);
            } else if (!value.isboolean()) {
                throw error(path, "expected boolean");
            }
        }

        public LuaValue validate(LuaValue value, String path) {
            checkPrimitive(value, path);
            if (!schema.get("min").isnil() && value.todouble() < schema.get("min").todouble()) {
                throw error(path, "below min");
            }
            if (!schema.get("max").isnil() && value.todouble() > schema.get("max").todouble()) {
                throw error(path, "above max");
            }
            LuaValue values = schema.get("values");
            if (!values.isnil()) {
                boolean found = false;
                for (int i = 1; i <= values.length(); i++) {
                    if (value.raweq(values.get(i))) {
                        found = true;
                    }
                }
                if (!found) {
                    throw error(path, "value is not in values");
                }
            }
            return value;
        }
    }
    public static final class Type {
        public final String name;
        public final String owner;
        public final String primary;
        public final boolean builtin;
        public final Map<String, Role> ingredients;
        public final Map<String, Role> outputs;
        public final Map<String, Field> data;
        public final Map<String, Field> context;
        public final String matcherName;
        private final LuaTable definition = new LuaTable();
        public long revision;
        public final Reference reference;
        Type(LuaValue def, String owner, boolean builtin) {
            Map<String, Role> ingredients = new LinkedHashMap<>();
            Map<String, Role> outputs = new LinkedHashMap<>();
            Map<String, Field> data = new LinkedHashMap<>();
            Map<String, Field> context = new LinkedHashMap<>();
            this.owner = owner;
            this.builtin = builtin;
            name = qualify(string(def.get("name"), "recipeTypes:add.name"));
            definition.set("name", name);
            definition.set("displayName",
                    def.get("displayName").isnil()
                            ? name.substring(name.indexOf(':') + 1)
                            : string(def.get("displayName"), name + ".displayName"));
            readRoles(def.get("ingredients"), ingredients, true);
            readRoles(def.get("outputs"), outputs, false);
            primary = def.get("primaryOutput").isnil() && outputs.size() == 1
                    ? outputs.keySet().iterator().next()
                    : string(def.get("primaryOutput"), name + ".primaryOutput");
            if (!outputs.containsKey(primary) || outputs.get(primary).optional
                    || outputs.get(primary).type != RoleValueType.ITEM) {
                throw error(name + ".primaryOutput", "must select a required item output role");
            }
            definition.set("primaryOutput", primary);
            readFields(def.get("data"), data, false);
            readFields(def.get("context"), context, true);
            matcherName = def.get("matcher").isnil() ? null : RecipeMatcherRegistry.name(def.get("matcher"));
            if (matcherName != null) {
                if (builtin) {
                    throw error(name + ".matcher", "native recipe types cannot use custom matchers");
                }
                definition.set("matcher", matcherName);
            }
            this.ingredients = Collections.unmodifiableMap(ingredients);
            this.outputs = Collections.unmodifiableMap(outputs);
            this.data = Collections.unmodifiableMap(data);
            this.context = Collections.unmodifiableMap(context);
            reference = new Reference(this);
        }

        public LuaValue getDefinition() {
            return copy(definition);
        }

        private void readRoles(LuaValue value, Map<String, Role> roles, boolean input) {
            String field = input ? "ingredients" : "outputs";
            String path = name + "." + field;
            LuaTable normalized = new LuaTable();
            for (String key : keys(value, path)) {
                if (!key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    throw error(path + "." + key, "invalid role name");
                }
                Role role = new Role(key, value.get(key), input, builtin, path + "." + key);
                roles.put(key, role);
                normalized.set(key, role.view(input));
            }
            if (roles.isEmpty()) {
                throw error(path, "requires at least one role");
            }
            definition.set(field, normalized);
        }

        private void readFields(LuaValue value, Map<String, Field> fields, boolean context) {
            String field = context ? "context" : "data";
            String path = name + "." + field;
            LuaTable normalized = new LuaTable();
            if (!value.isnil()) {
                for (String key : keys(value, path)) {
                    Field parsed = new Field(value.get(key), path + "." + key, context);
                    fields.put(key, parsed);
                    normalized.set(key, parsed.schema);
                }
            }
            definition.set(field, normalized);
        }
    }
    public static final class Reference extends LuaTable {
        public final Type type;
        Reference(Type type) {
            this.type = type;
        }

        public LuaValue get(String key) {
            return get(LuaValue.valueOf(key));
        }

        public LuaValue get(LuaValue key) {
            if (key.type() == LuaValue.TSTRING) {
                if (key.tojstring().equals("owner")) {
                    return LuaValue.valueOf(type.owner);
                }
                if (key.tojstring().equals("exists")) {
                    return LuaValue.TRUE;
                }
                return copy(type.definition.get(key));
            }
            return LuaValue.NIL;
        }
    }
}
