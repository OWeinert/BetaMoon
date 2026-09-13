package betamoon.tileentity;

import net.minecraft.src.NBTTagCompound;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Supported persistent value types for Lua-backed tile entities. */
public enum TileDataType {
    INTEGER("integer", true) {
        public Object defaultValue(LuaValue value) {
            return Integer.valueOf(value.optint(0));
        }

        public Object fromLua(LuaValue value) {
            return Integer.valueOf(value.checkint());
        }

        Object read(NBTTagCompound tag, String key) {
            return Integer.valueOf(tag.getInteger(key));
        }

        void write(NBTTagCompound tag, String key, Object value) {
            tag.setInteger(key, ((Number) value).intValue());
        }

        Object fromSyncValue(int value) {
            return Integer.valueOf(value);
        }

        boolean accepts(Object value) {
            return value instanceof Integer;
        }
    },
    NUMBER("number", false) {
        public Object defaultValue(LuaValue value) {
            return Double.valueOf(value.optdouble(0.0D));
        }

        public Object fromLua(LuaValue value) {
            return Double.valueOf(value.checkdouble());
        }

        Object read(NBTTagCompound tag, String key) {
            return Double.valueOf(tag.getDouble(key));
        }

        void write(NBTTagCompound tag, String key, Object value) {
            tag.setDouble(key, ((Number) value).doubleValue());
        }

        Object fromSyncValue(int value) {
            throw new IllegalStateException("Number fields cannot be synchronized as integers");
        }

        boolean accepts(Object value) {
            return value instanceof Number;
        }
    },
    BOOLEAN("boolean", true) {
        public Object defaultValue(LuaValue value) {
            return Boolean.valueOf(value.optboolean(false));
        }

        public Object fromLua(LuaValue value) {
            return Boolean.valueOf(value.checkboolean());
        }

        Object read(NBTTagCompound tag, String key) {
            return Boolean.valueOf(tag.getBoolean(key));
        }

        void write(NBTTagCompound tag, String key, Object value) {
            tag.setBoolean(key, ((Boolean) value).booleanValue());
        }

        Object fromSyncValue(int value) {
            return Boolean.valueOf(value != 0);
        }

        boolean accepts(Object value) {
            return value instanceof Boolean;
        }
    },
    STRING("string", false) {
        public Object defaultValue(LuaValue value) {
            return value.optjstring("");
        }

        public Object fromLua(LuaValue value) {
            return value.checkjstring();
        }

        Object read(NBTTagCompound tag, String key) {
            return tag.getString(key);
        }

        void write(NBTTagCompound tag, String key, Object value) {
            tag.setString(key, String.valueOf(value));
        }

        Object fromSyncValue(int value) {
            throw new IllegalStateException("String fields cannot be synchronized as integers");
        }

        boolean accepts(Object value) {
            return value instanceof String;
        }
    };

    private final String luaName;
    private final boolean synchronizedAsInteger;

    TileDataType(String luaName, boolean synchronizedAsInteger) {
        this.luaName = luaName;
        this.synchronizedAsInteger = synchronizedAsInteger;
    }

    public String getLuaName() {
        return luaName;
    }

    public boolean canSynchronize() {
        return synchronizedAsInteger;
    }

    public static TileDataType parse(String name) {
        for (TileDataType type : values()) {
            if (type.luaName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw new LuaError("Unsupported tile entity data type: " + name);
    }

    public abstract Object defaultValue(LuaValue value);

    public abstract Object fromLua(LuaValue value);

    abstract Object read(NBTTagCompound tag, String key);

    abstract void write(NBTTagCompound tag, String key, Object value);

    abstract Object fromSyncValue(int value);

    abstract boolean accepts(Object value);
}
