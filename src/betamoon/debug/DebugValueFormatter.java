package betamoon.debug;

import betamoon.recipes.custom.RecipeValues;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * Formats detached Lua data values without exposing their serialization format.
 */
final class DebugValueFormatter {
    private DebugValueFormatter() {
    }

    static String format(LuaValue value) {
        if (value == null || value.isnil()) {
            return "nil";
        }
        if (value.type() == LuaValue.TSTRING) {
            return quote(value.tojstring());
        }
        if (!value.istable()) {
            return value.tojstring();
        }
        if (isDenseList(value)) {
            StringBuilder out = new StringBuilder("{ ");
            for (int i = 1; i <= value.length(); i++) {
                if (i > 1) {
                    out.append(", ");
                }
                out.append(format(value.get(i)));
            }
            return out.append(" }").toString();
        }
        StringBuilder out = new StringBuilder("{ ");
        List<String> keys = RecipeValues.keys(value, "debug value");
        orderBoundsFirst(keys);
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            String key = keys.get(i);
            out.append(key).append(" = ").append(format(value.get(key)));
        }
        return keys.isEmpty() ? "{}" : out.append(" }").toString();
    }

    private static void orderBoundsFirst(List<String> keys) {
        if (!keys.remove("min")) {
            return;
        }
        keys.add(0, "min");
        if (keys.remove("max")) {
            keys.add(1, "max");
        }
    }

    static String quote(String value) {
        String escaped = value == null
                ? ""
                : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n")
                        .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    private static boolean isDenseList(LuaValue value) {
        int length = value.length();
        if (length == 0) {
            return false;
        }
        List<Integer> indexes = new ArrayList<Integer>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs pair = value.next(key);
            key = pair.arg1();
            if (key.isnil()) {
                break;
            }
            if (!key.isint()) {
                return false;
            }
            indexes.add(Integer.valueOf(key.toint()));
        }
        Collections.sort(indexes);
        if (indexes.size() != length) {
            return false;
        }
        for (int i = 0; i < indexes.size(); i++) {
            if (indexes.get(i).intValue() != i + 1) {
                return false;
            }
        }
        return true;
    }
}
