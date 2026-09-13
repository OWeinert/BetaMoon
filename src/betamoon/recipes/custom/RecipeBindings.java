package betamoon.recipes.custom;

import betamoon.tileentity.LuaTileEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.recipes.custom.RecipeValues.*;

/**
 * Creates and resolves reusable machine-slot bindings for generalized recipes.
 */
public final class RecipeBindings {
    private RecipeBindings() {
    }

    public static void attach(LuaTable root) {
        final LuaTable service = new LuaTable();
        root.set("recipeBindings", service);
        service.set("pool", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return pool(args.arg(args.arg1() == service ? 2 : 1));
            }
        });
        service.set("grid", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return grid(args.arg(args.arg1() == service ? 2 : 1));
            }
        });
    }

    public static PoolReference pool(LuaValue definition) {
        return new PoolReference(readPool(definition, "recipeBindings:pool"));
    }

    public static GridReference grid(LuaValue definition) {
        return new GridReference(readGrid(definition, "recipeBindings:grid"));
    }

    static Resolved resolve(LuaTileEntity entity, LuaValue value, RecipeTypes.Role role, Set<Integer> used,
            String path) {
        if (role.type == RecipeTypes.RoleValueType.ITEM) {
            List<String> names = Collections.singletonList(string(value, path));
            return resolveNames(entity, role, names, 1, 1, used, path);
        }
        if (role.type == RecipeTypes.RoleValueType.ITEM_POOL
                || role.type == RecipeTypes.RoleValueType.ITEM_OUTPUT_POOL) {
            List<String> names = value instanceof PoolReference ? ((PoolReference) value).names : readPool(value, path);
            return resolveNames(entity, role, names, names.size(), 1, used, path);
        }
        if (role.type == RecipeTypes.RoleValueType.ITEM_GRID) {
            GridNames names = value instanceof GridReference ? ((GridReference) value).names : readGrid(value, path);
            if (names.width != role.grid.width || names.height != role.grid.height) {
                throw error(path, "expected a " + role.grid.width + "x" + role.grid.height + " slot grid");
            }
            return resolveNames(entity, role, names.flattened, names.width, names.height, used, path);
        }
        throw error(path, "recipe role type '" + role.type.schemaName + "' cannot bind machine slots");
    }

    private static Resolved resolveNames(LuaTileEntity entity, RecipeTypes.Role role, List<String> names, int width,
            int height, Set<Integer> used, String path) {
        int[] slots = new int[names.size()];
        Map<String, Integer> available = entity.getDefinition().slots;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            Integer slot = available.get(name);
            if (slot == null) {
                throw error(path, "tile inventory has no slot named '" + name + "'");
            }
            if (!used.add(slot)) {
                throw error(path, "physical slot '" + name + "' is already bound");
            }
            slots[i] = slot.intValue();
        }
        return new Resolved(role, names, slots, width, height);
    }

    private static List<String> readPool(LuaValue value, String path) {
        table(value, path);
        if (!value.get("prefix").isnil() || !value.get("count").isnil()) {
            fields(value, path, "prefix", "count");
            String prefix = prefix(value.get("prefix"), path + ".prefix");
            int count = positive(value.get("count"), 4096, path + ".count");
            List<String> names = new ArrayList<String>();
            for (int i = 1; i <= count; i++) {
                names.add(prefix + i);
            }
            return immutableUnique(names, path);
        }
        return immutableUnique(denseStrings(value, path), path);
    }

    private static GridNames readGrid(LuaValue value, String path) {
        table(value, path);
        if (!value.get("prefix").isnil() || !value.get("width").isnil() || !value.get("height").isnil()) {
            fields(value, path, "prefix", "width", "height");
            String prefix = prefix(value.get("prefix"), path + ".prefix");
            int width = positive(value.get("width"), 16, path + ".width");
            int height = positive(value.get("height"), 16, path + ".height");
            List<List<String>> rows = new ArrayList<List<String>>();
            for (int row = 1; row <= height; row++) {
                List<String> names = new ArrayList<String>();
                for (int column = 1; column <= width; column++) {
                    names.add(prefix + row + "_" + column);
                }
                rows.add(names);
            }
            return new GridNames(rows, path);
        }
        if (value.length() == 0) {
            throw error(path, "expected a nonempty matrix of slot names");
        }
        ensureDense(value, path);
        List<List<String>> rows = new ArrayList<List<String>>();
        for (int row = 1; row <= value.length(); row++) {
            rows.add(denseStrings(value.get(row), path + "[" + row + "]"));
        }
        return new GridNames(rows, path);
    }

    private static List<String> denseStrings(LuaValue value, String path) {
        table(value, path);
        if (value.length() == 0) {
            throw error(path, "expected a nonempty list of slot names");
        }
        ensureDense(value, path);
        List<String> result = new ArrayList<String>();
        for (int i = 1; i <= value.length(); i++) {
            String name = string(value.get(i), path + "[" + i + "]");
            if (name.trim().isEmpty()) {
                throw error(path + "[" + i + "]", "slot name cannot be empty");
            }
            result.add(name);
        }
        return result;
    }

    private static void ensureDense(LuaValue value, String path) {
        LuaValue key = LuaValue.NIL;
        while (!(key = value.next(key).arg1()).isnil()) {
            int index = integer(key, path + " index");
            if (index < 1 || index > value.length()) {
                throw error(path, "expected a dense list");
            }
        }
    }

    private static List<String> immutableUnique(List<String> names, String path) {
        Set<String> unique = new HashSet<String>();
        for (String name : names) {
            if (!unique.add(name)) {
                throw error(path, "slot name '" + name + "' is listed more than once");
            }
        }
        return Collections.unmodifiableList(new ArrayList<String>(names));
    }

    private static int positive(LuaValue value, int maximum, String path) {
        int result = integer(value, path);
        if (result < 1 || result > maximum) {
            throw error(path, "expected an integer from 1 to " + maximum);
        }
        return result;
    }

    private static String prefix(LuaValue value, String path) {
        String result = string(value, path);
        if (result.isEmpty()) {
            throw error(path, "prefix cannot be empty");
        }
        return result;
    }

    private static LuaTable listView(List<String> names) {
        LuaTable result = new LuaTable();
        for (int i = 0; i < names.size(); i++) {
            result.set(i + 1, names.get(i));
        }
        return result;
    }

    private static LuaTable gridView(GridNames names) {
        LuaTable result = new LuaTable();
        for (int row = 0; row < names.rows.size(); row++) {
            result.set(row + 1, listView(names.rows.get(row)));
        }
        return result;
    }

    static final class Resolved {
        final RecipeTypes.Role role;
        final List<String> names;
        final int[] slots;
        final int width;
        final int height;

        Resolved(RecipeTypes.Role role, List<String> names, int[] slots, int width, int height) {
            this.role = role;
            this.names = Collections.unmodifiableList(new ArrayList<String>(names));
            this.slots = slots.clone();
            this.width = width;
            this.height = height;
        }

        LuaValue view() {
            if (role.type == RecipeTypes.RoleValueType.ITEM) {
                return LuaValue.valueOf(names.get(0));
            }
            if (role.type == RecipeTypes.RoleValueType.ITEM_GRID) {
                List<List<String>> rows = new ArrayList<List<String>>();
                for (int row = 0; row < height; row++) {
                    rows.add(names.subList(row * width, (row + 1) * width));
                }
                return gridView(new GridNames(rows, "binding"));
            }
            return listView(names);
        }
    }

    public static final class PoolReference extends LuaTable {
        private final List<String> names;
        private final LuaValue toTable;

        private PoolReference(List<String> names) {
            this.names = names;
            toTable = new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    return listView(PoolReference.this.names);
                }
            };
        }

        @Override
        public LuaValue get(LuaValue key) {
            if (key.tojstring().equals("kind")) {
                return LuaValue.valueOf("item_pool");
            }
            if (key.tojstring().equals("count")) {
                return LuaValue.valueOf(names.size());
            }
            if (key.tojstring().equals("toTable")) {
                return toTable;
            }
            if (key.isint() && key.toint() >= 1 && key.toint() <= names.size()) {
                return LuaValue.valueOf(names.get(key.toint() - 1));
            }
            return super.get(key);
        }
    }

    public static final class GridReference extends LuaTable {
        private final GridNames names;
        private final LuaValue toTable;

        private GridReference(GridNames names) {
            this.names = names;
            toTable = new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    return gridView(GridReference.this.names);
                }
            };
        }

        @Override
        public LuaValue get(LuaValue key) {
            if (key.tojstring().equals("kind")) {
                return LuaValue.valueOf("item_grid");
            }
            if (key.tojstring().equals("width")) {
                return LuaValue.valueOf(names.width);
            }
            if (key.tojstring().equals("height")) {
                return LuaValue.valueOf(names.height);
            }
            if (key.tojstring().equals("toTable")) {
                return toTable;
            }
            if (key.isint() && key.toint() >= 1 && key.toint() <= names.height) {
                return listView(names.rows.get(key.toint() - 1));
            }
            return super.get(key);
        }
    }

    private static final class GridNames {
        final List<List<String>> rows;
        final List<String> flattened;
        final int width;
        final int height;

        GridNames(List<List<String>> rows, String path) {
            height = rows.size();
            width = rows.get(0).size();
            if (width == 0) {
                throw error(path, "grid rows cannot be empty");
            }
            List<List<String>> copiedRows = new ArrayList<List<String>>();
            List<String> flattened = new ArrayList<String>();
            for (int row = 0; row < rows.size(); row++) {
                if (rows.get(row).size() != width) {
                    throw error(path + "[" + (row + 1) + "]", "grid rows must have equal width");
                }
                List<String> copied = new ArrayList<String>(rows.get(row));
                copiedRows.add(Collections.unmodifiableList(copied));
                flattened.addAll(copied);
            }
            this.rows = Collections.unmodifiableList(copiedRows);
            this.flattened = immutableUnique(flattened, path);
        }
    }
}
