package betamoon.luaapi.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/**
 * Exact, path-aware declaration parsing shared by block and item definition.
 */
public final class LuaDeclarationValues {
    private LuaDeclarationValues() {
    }

    public static LuaError error(String path, String message) {
        return new LuaError(path + ": " + message);
    }

    /**
     * Reads a required field while preserving the declarative API's missing-field
     * error.
     */
    public static LuaValue required(LuaValue table, String key) {
        LuaValue value = table.get(key);
        if (value.isnil()) {
            throw new LuaError("Definition requires '" + key + "'.");
        }
        return value;
    }

    public static String internalName(LuaValue definition) {
        LuaValue value = definition.get("internalName");
        if (value.isnil()) {
            value = definition.get("name");
        }
        if (value.isnil()) {
            value = definition.get("key");
        }
        if (value.isnil()) {
            throw new LuaError("Definition requires 'name', 'internalName', or 'key'.");
        }
        String name = value.checkjstring();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    public static List<String> keys(LuaValue table, String path) {
        if (!table.istable()) {
            throw error(path, "expected a table");
        }
        List<String> keys = new ArrayList<String>();
        LuaValue key = LuaValue.NIL;
        for (;;) {
            key = table.next(key).arg1();
            if (key.isnil()) {
                break;
            }
            keys.add(string(key, path + " key"));
        }
        Collections.sort(keys);
        return keys;
    }

    public static void fields(LuaValue table, String path, String... allowed) {
        for (String key : keys(table, path)) {
            boolean valid = false;
            for (String field : allowed) {
                if (field.equals(key)) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                throw error(path + "." + key, "unknown field");
            }
        }
    }

    public static String string(LuaValue value, String path) {
        if (value.type() != LuaValue.TSTRING) {
            throw error(path, "expected a string");
        }
        return value.tojstring();
    }

    public static double number(LuaValue value, String path) {
        if (value.type() != LuaValue.TNUMBER) {
            throw error(path, "expected a finite number");
        }
        double number = value.todouble();
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            throw error(path, "expected a finite number");
        }
        return number;
    }

    public static int integer(LuaValue value, String path, int min, int max) {
        double n = number(value, path);
        if (n != Math.floor(n) || n < min || n > max) {
            throw error(path, "expected integer " + min + ".." + max);
        }
        return (int) n;
    }

    public static boolean bool(LuaValue value, String path, boolean fallback) {
        if (value.isnil()) {
            return fallback;
        }
        if (!value.isboolean()) {
            throw error(path, "expected a boolean");
        }
        return value.toboolean();
    }

    public static LuaValue action(LuaValue value, String path, String... extra) {
        if (value.isnil()) {
            return LuaValue.NIL;
        }
        String[] fields = new String[extra.length + 1];
        fields[0] = "action";
        System.arraycopy(extra, 0, fields, 1, extra.length);
        fields(value, path, fields);
        LuaValue action = value.get("action");
        if (!action.isfunction()) {
            throw error(path + ".action", "expected a function");
        }
        return action;
    }

    public static int id(LuaValue value, String path) {
        return integer(value.istable() ? value.get("id") : value, path, 0, 32767);
    }

    public static int length(LuaValue value, String path) {
        if (!value.istable()) {
            throw error(path, "expected a list");
        }
        int n = value.length();
        LuaValue key = LuaValue.NIL;
        while (!(key = value.next(key).arg1()).isnil()) {
            integer(key, path + " index", 1, n);
        }
        for (int i = 1; i <= n; i++) {
            if (value.get(i).isnil()) {
                throw error(path, "expected a dense list");
            }
        }
        return n;
    }
}
