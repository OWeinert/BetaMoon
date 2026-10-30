package betamoon.luaapi.client.ui;

import betamoon.gui.presentation.GuiPublishedData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Strict bounded detachment for UI props, events and state; numeric strings and
 * native views remain strings/refused.
 */
public final class GuiLuaData {
    private final Set<LuaTable> ancestors = Collections.newSetFromMap(new IdentityHashMap<LuaTable, Boolean>());
    private int count;
    private int textUnits;

    public static Object read(LuaValue value, String path) {
        Object candidate = new GuiLuaData().copy(value, path, 0);
        GuiPublishedData validation = new GuiPublishedData();
        try {
            validation.publish(Collections.singletonMap("value", candidate));
        } catch (IllegalArgumentException invalid) {
            throw new LuaError(path + ": " + invalid.getMessage());
        }
        return validation.snapshot().get("value");
    }

    public static Map<String, Object> record(LuaValue value, String path) {
        Object result = read(value, path);
        if (!(result instanceof Map)) {
            throw new LuaError(path + ": expected a string-key record");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> record = (Map<String, Object>) result;
        return record;
    }

    private Object copy(LuaValue value, String path, int depth) {
        if (++count > 16384 || depth > 16) {
            throw new LuaError(path + ": UI data exceeds depth/value limits");
        }
        switch (value.type()) {
            case LuaValue.TNIL:
                return null;
            case LuaValue.TBOOLEAN:
                return value.toboolean();
            case LuaValue.TNUMBER:
                if (!Double.isFinite(value.todouble())) {
                    throw new LuaError(path + ": number must be finite");
                }
                return value.todouble();
            case LuaValue.TSTRING:
                text(value.tojstring(), path);
                return value.tojstring();
            case LuaValue.TTABLE:
                break;
            default:
                throw new LuaError(path + ": expected detached scalar/list/record; native values are unavailable");
        }
        LuaTable table = value.checktable();
        if (!ancestors.add(table)) {
            throw new LuaError(path + ": cyclic UI data is unavailable");
        }
        try {
            LuaValue[] keys = table.keys();
            boolean list = keys.length > 0 && keys[0].type() == LuaValue.TNUMBER;
            if (list) {
                int length = table.length();
                if (keys.length != length || length > 16384 - count) {
                    throw new LuaError(path + ": expected a bounded dense list");
                }
                for (LuaValue key : keys) {
                    if (!key.isinttype() || key.toint() < 1 || key.toint() > length) {
                        throw new LuaError(path + ": expected a dense list without record keys");
                    }
                }
                List<Object> result = new ArrayList<>();
                for (int index = 1; index <= length; index++) {
                    result.add(copy(table.get(index), path + "[" + index + "]", depth + 1));
                }
                return result;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (LuaValue key : keys) {
                if (key.type() != LuaValue.TSTRING) {
                    throw new LuaError(path + ": record keys must be strings");
                }
                text(key.tojstring(), path);
                result.put(key.tojstring(), copy(table.get(key), path + "." + key.tojstring(), depth + 1));
            }
            return result;
        } finally {
            ancestors.remove(table);
        }
    }

    private void text(String value, String path) {
        if (value.length() > 1048576 - textUnits) {
            throw new LuaError(path + ": UI data exceeds its text budget");
        }
        textUnits += value.length();
    }

    private GuiLuaData() {
    }
}
