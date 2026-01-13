package betamoon.luaapi;

import java.util.HashMap;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

final class ModuleApi {
    private static final Map modules = new HashMap();

    private ModuleApi() {
    }

    static void attach(LuaTable module, LuaValue env) {
        module.set("exportModule", new ExportModule(env));
        module.set("requireModule", new RequireModule());
    }

    private static final class ExportModule extends VarArgFunction {
        private final LuaValue packageLoaded;

        private ExportModule(LuaValue env) {
            LuaValue packageTable = env.get("package");
            if (packageTable.istable()) {
                packageLoaded = packageTable.get("loaded");
            } else {
                packageLoaded = LuaValue.NIL;
            }
        }

        public Varargs invoke(Varargs args) {
            int offset = (args.narg() >= 1 && args.arg(1).istable()) ? 1 : 0;
            String name = args.arg(1 + offset).checkjstring();
            LuaValue moduleValue = args.arg(2 + offset);
            LuaTable moduleTable;
            if (moduleValue.isnil()) {
                moduleTable = new LuaTable();
            } else if (moduleValue.istable()) {
                moduleTable = (LuaTable) moduleValue;
            } else {
                throw new LuaError("exportModule expects a module table or nil.");
            }
            if (packageLoaded.isnil()) {
                throw new LuaError("Lua package.loaded table not available.");
            }
            packageLoaded.set(name, moduleTable);
            modules.put(name, moduleTable);
            return moduleTable;
        }
    }

    private static final class RequireModule extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            int offset = (args.narg() >= 1 && args.arg(1).istable()) ? 1 : 0;
            String name = args.arg(1 + offset).checkjstring();
            LuaValue moduleTable = (LuaValue) modules.get(name);
            if (moduleTable == null) {
                throw new LuaError("Module not exported: " + name);
            }
            return moduleTable;
        }
    }
}
