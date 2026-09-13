package betamoon.luaapi.module;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class ModuleApi {
    private ModuleApi() {
    }

    public static void attach(LuaTable module, LuaValue env) {
        LuaTable api = new LuaTable();
        api.set("export", new ExportModule(api, env));
        api.set("import", new ImportModule(api));
        module.set("modules", api);
    }

    private static final class ExportModule extends VarArgFunction {
        private final LuaTable receiver;
        private final LuaValue packageLoaded;

        private ExportModule(LuaTable receiver, LuaValue env) {
            this.receiver = receiver;
            LuaValue packageTable = env.get("package");
            if (packageTable.istable()) {
                packageLoaded = packageTable.get("loaded");
            } else {
                packageLoaded = LuaValue.NIL;
            }
        }

        @Override
        public Varargs invoke(Varargs args) {
            int offset = args.arg1() == receiver ? 1 : 0;
            String name = args.arg(1 + offset).checkjstring();
            LuaValue moduleValue = args.arg(2 + offset);
            if (!moduleValue.istable()) {
                throw new LuaError("Module: export(name, table) requires a table to export.");
            }

            ModuleRegistry.stage(name, (LuaTable) moduleValue, packageLoaded);
            return NONE;
        }
    }

    private static final class ImportModule extends VarArgFunction {
        private final LuaTable receiver;

        private ImportModule(LuaTable receiver) {
            this.receiver = receiver;
        }

        @Override
        public Varargs invoke(Varargs args) {
            int offset = args.arg1() == receiver ? 1 : 0;
            String name = args.arg(1 + offset).checkjstring();
            return ModuleRegistry.importRequired(name);
        }
    }
}
