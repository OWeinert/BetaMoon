package betamoon.data;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.src.NBTBase;
import net.minecraft.src.NBTTagCompound;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Bound, persistent values for one compiled schema. Unknown saved fields remain untouched. */
public final class DataStore {
    public interface ChangeListener {
        void changed(String field);
    }

    private final DataSchema schema;
    private final ChangeListener listener;
    private final Map<String, Object> values = new HashMap<>();
    private NBTTagCompound raw = new NBTTagCompound();
    private String error;
    private boolean mutating;

    public DataStore(DataSchema schema, ChangeListener listener) {
        if (schema == null) {
            throw new IllegalArgumentException("Data schema is required");
        }
        this.schema = schema;
        this.listener = listener;
        bind();
    }

    public void load(NBTTagCompound saved) {
        raw = saved == null ? new NBTTagCompound() : saved;
        bind();
    }

    public NBTTagCompound raw() {
        return raw;
    }

    public String error() {
        return error;
    }

    public LuaValue get(String name) {
        DataField field = requireField(name);
        return field.toLua(values.get(name));
    }

    public Object getValue(String name) {
        requireField(name);
        return values.get(name);
    }

    public void set(String name, LuaValue value, boolean mutable) {
        if (!mutable) {
            throw new LuaError("Data is read-only in this callback.");
        }
        if (mutating) {
            throw new LuaError("Reentrant writes to the same data store are not allowed.");
        }
        DataField field = requireField(name);
        Object parsed = field.fromLua(value, "data." + name);
        mutating = true;
        try {
            values.put(name, parsed);
            field.write(raw, parsed);
            if (listener != null) {
                listener.changed(name);
            }
        } finally {
            mutating = false;
        }
    }

    public LuaTable snapshot() {
        requireAvailable();
        LuaTable result = new LuaTable();
        for (DataField field : schema.fields().values()) {
            result.set(field.name, field.toLua(values.get(field.name)));
        }
        return result;
    }

    public Map<String, Object> values() {
        requireAvailable();
        return new LinkedHashMap<>(values);
    }

    public Map<String, Object> checkpoint() {
        requireAvailable();
        return new LinkedHashMap<>(values);
    }

    public void restore(Map<String, Object> checkpoint) {
        if (checkpoint == null || !checkpoint.keySet().equals(schema.fields().keySet())) {
            throw new IllegalArgumentException("Checkpoint does not match the data schema");
        }
        values.clear();
        values.putAll(checkpoint);
        for (DataField field : schema.fields().values()) {
            field.write(raw, values.get(field.name));
        }
    }

    private void bind() {
        Map<String, NBTBase> tags = new HashMap<>();
        for (Object entry : raw.func_28110_c()) {
            NBTBase tag = (NBTBase) entry;
            tags.put(tag.getKey(), tag);
        }
        Map<String, Object> parsed = new HashMap<>();
        for (DataField field : schema.fields().values()) {
            NBTBase tag = tags.get(field.name);
            if (tag != null && !field.matches(tag)) {
                error = "Saved field '" + field.name + "' has an incompatible NBT type";
                return;
            }
            try {
                parsed.put(field.name, tag == null ? field.defaultValue : field.read(raw));
            } catch (IllegalArgumentException invalid) {
                error = invalid.getMessage();
                return;
            }
        }
        values.clear();
        values.putAll(parsed);
        for (DataField field : schema.fields().values()) {
            if (!tags.containsKey(field.name)) {
                field.write(raw, field.defaultValue);
            }
        }
        error = null;
    }

    private DataField requireField(String name) {
        requireAvailable();
        DataField field = schema.get(name);
        if (field == null) {
            throw new LuaError("Unknown data field: " + name);
        }
        return field;
    }

    private void requireAvailable() {
        if (error != null) {
            throw new LuaError("Data is unavailable: " + error);
        }
    }
}
