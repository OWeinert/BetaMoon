package betamoon.luaapi.material;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/**
 * Module-local material handles; native material registration is supplied by
 * the domain API.
 */
final class MaterialRegistry extends LuaTable {
    private final Map<String, LuaValue> values = new HashMap<>();

    MaterialRegistry(BiFunction<String, LuaValue, LuaValue> registration) {
        set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue definition = argument(args);
                if (!definition.istable()) {
                    throw new LuaError("material add expects a definition table.");
                }
                String key = required(definition, "key").checkjstring();
                LuaValue value = registration.apply(key, definition);
                values.put(normalize(key), value);
                return value;
            }
        });
        set("get", new Get(false));
        set("getRequired", new Get(true));
    }

    private final class Get extends VarArgFunction {
        private final boolean required;

        private Get(boolean required) {
            this.required = required;
        }

        @Override
        public Varargs invoke(Varargs args) {
            String key = argument(args).checkjstring();
            LuaValue value = values.get(normalize(key));
            if (value != null) {
                return value;
            }
            if (required) {
                throw new LuaError("Material '" + key + "' has not been declared by BetaMoon.");
            }
            // Content definitions also accept vanilla material names directly.
            return LuaValue.valueOf(key);
        }
    }

    private LuaValue argument(Varargs args) {
        return args.arg(args.arg1() == this ? 2 : 1);
    }

    private static String normalize(String key) {
        return key.trim().toLowerCase();
    }
}
