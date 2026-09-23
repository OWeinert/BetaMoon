package betamoon.entity;

import betamoon.data.DataField;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import betamoon.network.protocol.WireValue;
import net.minecraft.src.NBTBase;
import net.minecraft.src.NBTTagCompound;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Preserves the complete NBT payload, including fields whose definition is currently unavailable. */
public final class EntityDataStore {
    private static final DataField.LuaContext NO_REFERENCES = new DataField.LuaContext() {
        @Override
        public DataField.ReferenceValue readReference(LuaValue value, String path) {
            throw new LuaError(path + ": a live entity callback is required for entity references");
        }

        @Override
        public LuaValue writeReference(DataField.ReferenceValue value) {
            throw new LuaError("A live entity callback is required for entity references");
        }
    };

    private NBTTagCompound raw = new NBTTagCompound();
    private final Map<String, Object> values = new HashMap<>();
    private EntityTypeDefinition bound;
    private String error;
    private long revision;
    private long dirtyBaseRevision;
    private final Set<String> dirtyFields = new LinkedHashSet<>();

    public void load(NBTTagCompound raw) {
        this.raw = raw;
        values.clear();
        bound = null;
        error = null;
        revision = 0;
        dirtyBaseRevision = 0;
        dirtyFields.clear();
    }

    public boolean bind(EntityTypeDefinition definition) {
        if (definition == null) {
            bound = null;
            return false;
        }
        if (bound == definition) {
            return error == null;
        }
        Map<String, NBTBase> tags = new HashMap<>();
        for (Object entry : raw.func_28110_c()) {
            NBTBase tag = (NBTBase) entry;
            tags.put(tag.getKey(), tag);
        }
        Map<String, Object> selected = new HashMap<>();
        for (DataField field : definition.data.values()) {
            NBTBase tag = tags.get(field.name);
            if (tag != null && !field.matches(tag)) {
                error = "Saved field '" + field.name + "' has an incompatible NBT type";
                bound = definition;
                return false;
            }
            Object value;
            try {
                value = tag == null ? field.defaultValue : field.read(raw);
            } catch (IllegalArgumentException savedError) {
                error = savedError.getMessage();
                bound = definition;
                return false;
            }
            selected.put(field.name, value);
        }
        values.clear();
        values.putAll(selected);
        for (DataField field : definition.data.values()) {
            if (!tags.containsKey(field.name)) {
                field.write(raw, values.get(field.name));
            }
        }
        error = null;
        bound = definition;
        return true;
    }

    public String error() {
        return error;
    }

    public LuaValue get(EntityTypeDefinition definition, String name) {
        return get(definition, name, NO_REFERENCES);
    }

    public LuaValue get(EntityTypeDefinition definition, String name, DataField.LuaContext context) {
        requireField(definition, name);
        return definition.data.get(name).toLua(values.get(name), context);
    }

    public void set(EntityTypeDefinition definition, String name, LuaValue value) {
        set(definition, name, value, NO_REFERENCES);
    }

    public void set(EntityTypeDefinition definition, String name, LuaValue value,
            DataField.LuaContext context) {
        requireField(definition, name);
        DataField field = definition.data.get(name);
        Object parsed = field.fromLua(value, context, "data." + name);
        values.put(name, parsed);
        field.write(raw, parsed);
        markChanged(name);
    }

    public long revision() {
        return revision;
    }

    public Map<String, WireValue> networkSnapshot(EntityTypeDefinition definition) {
        requireBound(definition);
        Map<String, WireValue> snapshot = new LinkedHashMap<>();
        for (DataField field : definition.data.values()) {
            snapshot.put(field.name, field.toWire(values.get(field.name)));
        }
        return snapshot;
    }

    public EntityDataDelta takeNetworkDelta(EntityTypeDefinition definition) {
        requireBound(definition);
        if (dirtyFields.isEmpty()) {
            return null;
        }
        Map<String, WireValue> changed = new LinkedHashMap<>();
        for (String name : dirtyFields) {
            DataField field = definition.data.get(name);
            changed.put(name, field.toWire(values.get(name)));
        }
        EntityDataDelta delta = new EntityDataDelta(dirtyBaseRevision, revision, changed);
        dirtyFields.clear();
        dirtyBaseRevision = revision;
        return delta;
    }

    public void applyNetworkSnapshot(EntityTypeDefinition definition, long nextRevision,
            Map<String, WireValue> snapshot) {
        requireBound(definition);
        if (nextRevision < 0 || snapshot == null || !snapshot.keySet().equals(definition.data.keySet())) {
            throw new IllegalArgumentException("Entity snapshot does not match its declared fields");
        }
        Map<String, Object> parsed = parseNetworkValues(definition, snapshot);
        replaceNetworkValues(definition, parsed);
        revision = nextRevision;
        dirtyBaseRevision = nextRevision;
        dirtyFields.clear();
    }

    public boolean applyNetworkDelta(EntityTypeDefinition definition, long baseRevision, long nextRevision,
            Map<String, WireValue> changed) {
        requireBound(definition);
        if (baseRevision != revision) {
            return false;
        }
        if (nextRevision <= baseRevision || changed == null || changed.isEmpty()) {
            throw new IllegalArgumentException("Entity delta has an invalid revision interval or no fields");
        }
        for (String name : changed.keySet()) {
            if (!definition.data.containsKey(name)) {
                throw new IllegalArgumentException("Entity delta contains undeclared field '" + name + "'");
            }
        }
        Map<String, Object> parsed = parseNetworkValues(definition, changed);
        for (Map.Entry<String, Object> entry : parsed.entrySet()) {
            values.put(entry.getKey(), entry.getValue());
            definition.data.get(entry.getKey()).write(raw, entry.getValue());
        }
        revision = nextRevision;
        dirtyBaseRevision = nextRevision;
        dirtyFields.clear();
        return true;
    }

    public NBTTagCompound raw() {
        return raw;
    }

    private void markChanged(String name) {
        if (dirtyFields.isEmpty()) {
            dirtyBaseRevision = revision;
        }
        revision++;
        dirtyFields.add(name);
    }

    private void requireBound(EntityTypeDefinition definition) {
        if (!bind(definition)) {
            throw new IllegalStateException("Entity data is unavailable: "
                    + (error == null ? "type missing" : error));
        }
    }

    private Map<String, Object> parseNetworkValues(EntityTypeDefinition definition,
            Map<String, WireValue> networkValues) {
        Map<String, Object> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, WireValue> entry : networkValues.entrySet()) {
            parsed.put(entry.getKey(), definition.data.get(entry.getKey())
                    .fromWire(entry.getValue(), "network data." + entry.getKey()));
        }
        return parsed;
    }

    private void replaceNetworkValues(EntityTypeDefinition definition, Map<String, Object> replacement) {
        values.clear();
        values.putAll(replacement);
        for (DataField field : definition.data.values()) {
            field.write(raw, values.get(field.name));
        }
    }

    private void requireField(EntityTypeDefinition definition, String name) {
        if (!bind(definition)) {
            throw new LuaError("Entity data is unavailable: " + (error == null ? "type missing" : error));
        }
        if (!definition.data.containsKey(name)) {
            throw new LuaError("Unknown entity data field: " + name);
        }
    }
}
