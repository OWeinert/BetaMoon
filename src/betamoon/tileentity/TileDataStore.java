package betamoon.tileentity;

import betamoon.data.DataField;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.src.NBTBase;
import net.minecraft.src.NBTTagCompound;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Owns validated values and preserves every saved tile-data field, including unknown fields. */
final class TileDataStore {
    private static final String NBT_PREFIX = "Data_";

    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, NBTBase> rawTags = new LinkedHashMap<>();
    private TileEntityDefinition bound;
    private String error;

    void initialize(TileEntityDefinition definition) {
        bound = definition;
        for (TileEntityDefinition.Field field : definition.fields.values()) {
            if (!values.containsKey(field.name)) {
                values.put(field.name, field.defaultValue);
            }
        }
    }

    void read(NBTTagCompound tag, TileEntityDefinition definition) {
        bound = definition;
        values.clear();
        rawTags.clear();
        error = null;
        for (Object entry : tag.func_28110_c()) {
            NBTBase saved = (NBTBase) entry;
            if (saved.getKey().startsWith(NBT_PREFIX)) {
                rawTags.put(saved.getKey(), saved);
            }
        }
        if (definition == null) {
            return;
        }
        for (TileEntityDefinition.Field field : definition.fields.values()) {
            String key = nbtKey(field);
            NBTBase saved = rawTags.get(key);
            if (saved == null) {
                values.put(field.name, field.defaultValue);
                continue;
            }
            if (!field.schema.matches(saved)) {
                error = "Saved tile data field '" + field.name + "' has an incompatible NBT type";
                return;
            }
            try {
                values.put(field.name, field.schema.read(tag, key, "saved tile data." + field.name));
            } catch (IllegalArgumentException invalid) {
                error = invalid.getMessage();
                return;
            }
        }
    }

    void write(NBTTagCompound tag, TileEntityDefinition definition) {
        for (Map.Entry<String, NBTBase> saved : rawTags.entrySet()) {
            tag.setTag(saved.getKey(), saved.getValue());
        }
        if (error != null || definition == null) {
            return;
        }
        for (TileEntityDefinition.Field field : definition.fields.values()) {
            field.schema.write(tag, nbtKey(field), values.get(field.name));
        }
    }

    Object get(TileEntityDefinition definition, String name) {
        requireAvailable(definition, name);
        return values.get(name);
    }

    Object get(String name) {
        return get(bound, name);
    }

    LuaValue getLua(TileEntityDefinition definition, String name) {
        requireAvailable(definition, name);
        TileEntityDefinition.Field field = definition.fields.get(name);
        return field.schema.toLua(values.get(name));
    }

    void setLua(TileEntityDefinition definition, String name, LuaValue value) {
        requireAvailable(definition, name);
        TileEntityDefinition.Field field = definition.fields.get(name);
        values.put(name, field.schema.fromLua(value, "tile data." + name));
    }

    void setInteger(TileEntityDefinition definition, String name, int value) {
        requireAvailable(definition, name);
        TileEntityDefinition.Field field = definition.fields.get(name);
        if (field.schema.type != DataField.Type.INTEGER) {
            throw new LuaError("Tile entity data field '" + name + "' is not an integer.");
        }
        values.put(name, Integer.valueOf(value));
    }

    void set(TileEntityDefinition definition, String name, Object value) {
        requireAvailable(definition, name);
        DataField.Type type = definition.fields.get(name).schema.type;
        boolean accepted = type == DataField.Type.BOOLEAN && value instanceof Boolean
                || type == DataField.Type.INTEGER && value instanceof Integer
                || type == DataField.Type.NUMBER && value instanceof Number
                        && Double.isFinite(((Number) value).doubleValue())
                || type == DataField.Type.STRING && value instanceof String;
        if (!accepted) {
            throw new LuaError("Invalid value for '" + name + "'; expected "
                    + type.name().toLowerCase() + ".");
        }
        values.put(name, type == DataField.Type.NUMBER
                ? Double.valueOf(((Number) value).doubleValue()) : value);
    }

    int getSyncValue(TileEntityDefinition definition, String name) {
        requireAvailable(definition, name);
        Object value = values.get(name);
        return value instanceof Number ? ((Number) value).intValue() : Boolean.TRUE.equals(value) ? 1 : 0;
    }

    int getSyncValue(String name) {
        return getSyncValue(bound, name);
    }

    void setSyncValue(TileEntityDefinition definition, String name, int value) {
        requireAvailable(definition, name);
        TileEntityDefinition.Field field = definition.fields.get(name);
        if (!field.sync) {
            throw new LuaError("Tile entity data field '" + name + "' is not synchronized.");
        }
        if (field.schema.type == DataField.Type.INTEGER) {
            values.put(name, Integer.valueOf(value));
        } else if (field.schema.type == DataField.Type.BOOLEAN) {
            values.put(name, Boolean.valueOf(value != 0));
        } else {
            throw new LuaError("Tile entity data field '" + name + "' cannot be synchronized as an integer.");
        }
    }

    String error() {
        return error;
    }

    private void requireAvailable(TileEntityDefinition definition, String name) {
        if (error != null) {
            throw new LuaError("Tile entity data is unavailable: " + error);
        }
        if (definition == null || !definition.fields.containsKey(name)) {
            throw new LuaError("Unknown tile entity data field: " + name);
        }
    }

    private static String nbtKey(TileEntityDefinition.Field field) {
        return NBT_PREFIX + field.name;
    }
}
