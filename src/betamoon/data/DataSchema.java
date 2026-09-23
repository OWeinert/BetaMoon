package betamoon.data;

import betamoon.luaapi.utils.LuaDeclarationValues;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaValue;

/** Immutable named-field schema compiled once at content registration time. */
public final class DataSchema {
    private static final int MAX_FIELDS = 128;

    private final Map<String, DataField> fields;

    private DataSchema(Map<String, DataField> fields) {
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public static DataSchema empty() {
        return new DataSchema(Collections.<String, DataField>emptyMap());
    }

    public static DataSchema parse(LuaValue declarations, String path) {
        if (declarations.isnil()) {
            return empty();
        }
        List<String> names = LuaDeclarationValues.keys(declarations, path);
        if (names.size() > MAX_FIELDS) {
            throw LuaDeclarationValues.error(path, "expected at most " + MAX_FIELDS + " fields");
        }
        Map<String, DataField> fields = new LinkedHashMap<>();
        for (String name : names) {
            fields.put(name, DataField.parse(name, declarations.get(name), path + "." + name));
        }
        return new DataSchema(fields);
    }

    public Map<String, DataField> fields() {
        return fields;
    }

    public DataField get(String name) {
        return fields.get(name);
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    public boolean containsEntityReference() {
        for (DataField field : fields.values()) {
            if (field.containsEntityReference()) {
                return true;
            }
        }
        return false;
    }
}
