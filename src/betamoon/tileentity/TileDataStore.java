package betamoon.tileentity;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.src.NBTTagCompound;
import org.luaj.vm2.LuaError;

/**
 * Owns validated values and their stable NBT representation for one tile
 * entity.
 */
final class TileDataStore {
    private static final String NBT_PREFIX = "Data_";

    private final Map<String, Object> values = new HashMap<>();

    void initialize(TileEntityDefinition definition) {
        for (TileEntityDefinition.Field field : definition.fields.values()) {
            if (!values.containsKey(field.name)) {
                values.put(field.name, field.defaultValue);
            }
        }
    }

    void read(NBTTagCompound tag, TileEntityDefinition definition) {
        for (TileEntityDefinition.Field field : definition.fields.values()) {
            String key = nbtKey(field);
            if (tag.hasKey(key)) {
                values.put(field.name, field.type.read(tag, key));
            }
        }
    }

    void write(NBTTagCompound tag, TileEntityDefinition definition) {
        for (TileEntityDefinition.Field field : definition.fields.values()) {
            field.type.write(tag, nbtKey(field), values.get(field.name));
        }
    }

    Object get(String name) {
        return values.get(name);
    }

    void set(TileEntityDefinition definition, String name, Object value) {
        TileEntityDefinition.Field field = definition == null ? null : definition.fields.get(name);
        if (field == null) {
            throw new LuaError("Unknown tile entity data field: " + name);
        }
        if (!field.type.accepts(value)) {
            throw new LuaError("Invalid value for '" + name + "'; expected " + field.type.getLuaName() + ".");
        }
        values.put(name, value);
    }

    int getSyncValue(String name) {
        Object value = values.get(name);
        return value instanceof Number ? ((Number) value).intValue() : Boolean.TRUE.equals(value) ? 1 : 0;
    }

    void setSyncValue(TileEntityDefinition definition, String name, int value) {
        TileEntityDefinition.Field field = definition == null ? null : definition.fields.get(name);
        if (field == null) {
            throw new LuaError("Unknown tile entity data field: " + name);
        }
        if (!field.type.canSynchronize()) {
            throw new LuaError("Tile entity data field '" + name + "' cannot be synchronized as an integer.");
        }
        set(definition, name, field.type.fromSyncValue(value));
    }

    private static String nbtKey(TileEntityDefinition.Field field) {
        return NBT_PREFIX + field.name;
    }
}
