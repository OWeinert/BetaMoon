package betamoon.luaapi.block;

import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.keys;
import static betamoon.luaapi.utils.LuaDeclarationValues.length;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** Stable named state packed into Minecraft's four metadata bits. */
public final class BlockStateSchema {
    private final Map<String, Field> fields = new LinkedHashMap<String, Field>();
    public final int defaults;
    public final String signature;
    public BlockStateSchema(LuaValue definition) {
        int bits = 0;
        int initial = 0;
        StringBuilder schema = new StringBuilder();
        if (!definition.isnil()) {
            for (String name : keys(definition, "state")) {
                LuaValue def = definition.get(name);
                fields(def, "state." + name, "type", "values", "default");
                String type = string(def.get("type"), "state." + name + ".type");
                LuaValue[] values;
                if (type.equals("boolean")) {
                    if (!def.get("values").isnil()) {
                        throw error("state." + name, "boolean state cannot declare values");
                    }
                    values = new LuaValue[]{LuaValue.FALSE, LuaValue.TRUE};
                } else if (type.equals("enum")) {
                    LuaValue list = def.get("values");
                    int size = length(list, "state." + name + ".values");
                    if (size < 2 || size > 16) {
                        throw error("state." + name, "expected 2..16 enum values");
                    }
                    values = new LuaValue[size];
                    for (int i = 0; i < size; i++) {
                        values[i] = LuaValue.valueOf(string(list.get(i + 1), "state." + name + ".values"));
                        for (int j = 0; j < i; j++) {
                            if (values[i].raweq(values[j])) {
                                throw error("state." + name, "duplicate enum value");
                            }
                        }
                    }
                } else {
                    throw error("state." + name, "expected boolean or enum");
                }
                int width = 1;
                while ((1 << width) < values.length) {
                    width++;
                }
                if (bits + width > 4) {
                    throw error("state", "exceeds four metadata bits; use tile entity data");
                }
                Field field = new Field(bits, width, values);
                fields.put(name, field);
                LuaValue fallback = def.get("default").isnil() ? values[0] : def.get("default");
                initial |= field.index(fallback, name) << bits;
                bits += width;
                schema.append(name).append(':').append(type).append(':');
                for (LuaValue value : values) {
                    schema.append(value.tojstring().length()).append('=').append(value.tojstring()).append(';');
                }
            }
        }
        defaults = initial;
        signature = schema.toString();
    }

    public boolean has(String name) {
        return fields.containsKey(name);
    }

    public void requireEnumValues(String name, String... expected) {
        Field field = field(name);
        if (field.values.length != expected.length) {
            throw error("state." + name, "must contain exactly the required direction values");
        }
        for (String value : expected) {
            field.index(LuaValue.valueOf(value), name);
        }
    }

    public LuaValue get(int metadata, String name) {
        Field field = field(name);
        int index = (metadata >> field.shift) & field.mask;
        return field.values[index < field.values.length ? index : 0];
    }

    public int set(int metadata, String name, LuaValue value) {
        Field field = field(name);
        return (metadata & ~(field.mask << field.shift)) | (field.index(value, name) << field.shift);
    }

    private Field field(String name) {
        Field field = fields.get(name);
        if (field == null) {
            throw error("state." + name, "unknown state field");
        }
        return field;
    }
    private static final class Field {
        final int shift;
        final int mask;
        final LuaValue[] values;
        Field(int shift, int width, LuaValue[] values) {
            this.shift = shift;
            this.mask = (1 << width) - 1;
            this.values = values;
        }

        int index(LuaValue value, String name) {
            for (int i = 0; i < values.length; i++) {
                if (values[i].raweq(value)) {
                    return i;
                }
            }
            throw error("state." + name, "invalid value " + value.tojstring());
        }
    }
}
