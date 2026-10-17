package betamoon.luaapi.utils;

import betamoon.worldgen.spatial.GenerationValues;
import betamoon.worldgen.terrain.GenerationSignature;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.DumpState;

/**
 * Bounded declaration identity, including Lua callback code without executing
 * callbacks.
 */
public final class LuaGenerationSignature {
    private final Map<LuaValue, Boolean> visiting = new IdentityHashMap<LuaValue, Boolean>();
    private int values;

    private LuaGenerationSignature() {
    }

    public static String of(LuaValue declaration) {
        return GenerationSignature
                .of(Collections.singletonMap("declaration", new LuaGenerationSignature().encode(declaration, 0)));
    }

    private String encode(LuaValue value, int depth) {
        if (++values > 150000 || depth > 32) {
            throw new LuaError("Generation declaration signature exceeds its size/depth budget");
        }
        if (value instanceof LuaDataReference) {
            return "reference:" + GenerationValues.canonical(((LuaDataReference) value).declarationValue());
        }
        if (value.isnil() || value.isboolean() || value.isnumber() || value.isstring()) {
            return value.typename() + ":" + value.tojstring().length() + ":" + value.tojstring();
        }
        if (value instanceof LuaClosure) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DumpState.dump(((LuaClosure) value).p, bytes, true);
                return "lua:" + GenerationSignature
                        .of(Collections.singletonMap("code", Base64.getEncoder().encodeToString(bytes.toByteArray())));
            } catch (IOException exception) {
                throw new LuaError("Cannot fingerprint generation callback: " + exception.getMessage());
            }
        }
        if (!value.istable()) {
            return "native:" + value.getClass().getName();
        }
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw new LuaError("Generation declarations cannot contain cyclic data tables");
        }
        Map<String, String> entries = new TreeMap<String, String>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = value.next(key);
            key = entry.arg1();
            if (key.isnil()) {
                break;
            }
            if (depth != 0 || !key.isstring() || !key.tojstring().equals("displayName")) {
                entries.put(encode(key, depth + 1), encode(entry.arg(2), depth + 1));
            }
        }
        visiting.remove(value);
        return "table:" + GenerationSignature.of(entries);
    }
}
