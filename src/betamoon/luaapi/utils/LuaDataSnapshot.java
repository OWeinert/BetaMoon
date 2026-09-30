package betamoon.luaapi.utils;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Strictly snapshots declarative Lua tables into JSON-compatible Java values. */
public final class LuaDataSnapshot {
    private static final int MAX_DEPTH = 48;
    private static final int MAX_VALUES = 150000;

    private final IdentityHashMap<LuaTable, Boolean> visiting = new IdentityHashMap<LuaTable, Boolean>();
    private int values;

    private LuaDataSnapshot() {
    }

    public static Map<String, Object> object(LuaValue value, String path) {
        LuaDataSnapshot converter = new LuaDataSnapshot();
        Object result = converter.value(value, path, 0, Shape.OBJECT);
        @SuppressWarnings("unchecked")
        Map<String, Object> object = (Map<String, Object>) result;
        return object;
    }

    private Object value(LuaValue value, String path, int depth, Shape expected) {
        if (depth > MAX_DEPTH || ++values > MAX_VALUES) {
            throw new LuaError(path + ": nesting or value count exceeds declaration limits");
        }
        if (value.isnil()) {
            return null;
        }
        if (value.isboolean()) {
            return Boolean.valueOf(value.toboolean());
        }
        if (value.isnumber()) {
            double number = value.todouble();
            if (!Double.isFinite(number) || Math.abs(number) > 1000000.0D) {
                throw new LuaError(path + ": expected a finite number within +/-1000000");
            }
            return Double.valueOf(number);
        }
        if (value.isstring()) {
            return value.tojstring();
        }
        if (value instanceof LuaDataReference) {
            return ((LuaDataReference) value).declarationValue();
        }
        if (!value.istable()) {
            throw new LuaError(path + ": expected JSON-compatible nil, boolean, number, string, or table");
        }
        return table((LuaTable) value, path, depth, expected);
    }

    private Object table(LuaTable table, String path, int depth, Shape expected) {
        if (visiting.put(table, Boolean.TRUE) != null) {
            throw new LuaError(path + ": cyclic tables are unsupported");
        }
        try {
            TableShape shape = shape(table, path);
            if (shape.count == 0) {
                return expected == Shape.ARRAY ? new ArrayList<Object>() : new LinkedHashMap<String, Object>();
            }
            if (shape.array) {
                List<Object> result = new ArrayList<Object>(shape.count);
                for (int index = 1; index <= shape.count; index++) {
                    result.add(value(table.rawget(index), path + "[" + (index - 1) + "]", depth + 1,
                            childShape(path, null)));
                }
                return result;
            }
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs next = table.next(key);
                key = next.arg1();
                if (key.isnil()) {
                    break;
                }
                String name = key.tojstring();
                LuaValue entry = next.arg(2);
                if (!entry.isnil()) {
                    result.put(name, value(entry, path + "." + name, depth + 1, childShape(path, name)));
                }
            }
            return result;
        } finally {
            visiting.remove(table);
        }
    }

    private static TableShape shape(LuaTable table, String path) {
        boolean hasNumbers = false;
        boolean hasStrings = false;
        int count = 0;
        int maximumIndex = 0;
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) {
                break;
            }
            count++;
            if (key.isint() && key.toint() >= 1) {
                hasNumbers = true;
                maximumIndex = Math.max(maximumIndex, key.toint());
            } else if (key.isstring()) {
                hasStrings = true;
            } else {
                throw new LuaError(path + ": table keys must be positive integers or strings");
            }
        }
        if (hasNumbers && hasStrings) {
            throw new LuaError(path + ": mixed array and object keys are unsupported");
        }
        if (hasNumbers && maximumIndex != count) {
            throw new LuaError(path + ": arrays must be dense and one-based");
        }
        return new TableShape(hasNumbers, count);
    }

    private static Shape childShape(String parentPath, String field) {
        if (field == null) {
            return parentPath.endsWith(".layers") || parentPath.matches(".*\\.layers\\[[0-9]+\\]")
                    ? Shape.ARRAY : Shape.ANY;
        }
        if (field.equals("elements") || field.equals("processors") || field.equals("variants")
                || field.equals("tags") || field.equals("faces") || field.equals("caps")
                || field.equals("layers") || field.equals("choices") || field.equals("replace")
                || field.equals("pools") || field.equals("entries") || field.equals("items")
                || field.equals("excludeStates") || field.equals("excludeTags")
                || field.equals("excludeMarkers") || field.equals("states") && parentPath.endsWith(".select")) {
            return Shape.ARRAY;
        }
        if (field.equals("palette") || field.equals("templates") || field.equals("legend")
                || field.equals("map") || field.equals("select") || field.equals("bounds")
                || field.equals("height") || field.equals("nearMarker") || field.equals("data")
                || field.equals("value") || field.equals("states") || field.equals("stack")
                || field.equals("rolls")) {
            return Shape.OBJECT;
        }
        return Shape.ANY;
    }

    private enum Shape {
        ANY,
        ARRAY,
        OBJECT
    }

    private static final class TableShape {
        private final boolean array;
        private final int count;

        private TableShape(boolean array, int count) {
            this.array = array;
            this.count = count;
        }
    }
}
