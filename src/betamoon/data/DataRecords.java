package betamoon.data;

import betamoon.luaapi.utils.LuaDeclarationValues;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Validates detached Lua records against compiled schemas. */
public final class DataRecords {
    private DataRecords() {
    }

    public static Map<String, Object> read(DataSchema schema, LuaValue value, String path, boolean requireAll) {
        if (value.isnil()) {
            if (schema.isEmpty()) {
                return Collections.emptyMap();
            }
            value = new LuaTable();
        }
        if (!value.istable()) {
            throw new LuaError(path + " must be a table.");
        }
        LuaDeclarationValues.fields(value, path, schema.fields().keySet().toArray(new String[0]));
        Map<String, Object> result = new LinkedHashMap<>();
        for (DataField field : schema.fields().values()) {
            LuaValue fieldValue = value.get(field.name);
            if (fieldValue.isnil()) {
                if (requireAll && !field.hasDeclaredDefault) {
                    throw new LuaError(path + "." + field.name + " is required.");
                }
                result.put(field.name, field.defaultValue);
            } else {
                result.put(field.name, field.fromLua(fieldValue, path + "." + field.name));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public static LuaTable write(DataSchema schema, Map<String, Object> values) {
        LuaTable result = new LuaTable();
        for (DataField field : schema.fields().values()) {
            result.set(field.name, field.toLua(values.get(field.name)));
        }
        return result;
    }
}
