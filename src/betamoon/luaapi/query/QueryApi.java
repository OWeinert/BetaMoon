package betamoon.luaapi.query;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class QueryApi {
    private QueryApi() {
    }

    public static void attach(LuaTable module) {
        module.set("query", new CreateQuery());
    }

    private static final class CreateQuery extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            return new ContentQueryHandle();
        }
    }

    private static final class ContentQueryHandle extends LuaTable {
        private ContentQueryHandle() {
            set("recipe", new StartRecipeQuery());
            set("block", new StartBlockQuery());
            set("item", new StartItemQuery());
        }
    }

    private static final class StartRecipeQuery extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            return QueryRecipeApi.createHandle();
        }
    }

    private static final class StartBlockQuery extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            return QueryBlockApi.createHandle();
        }
    }

    private static final class StartItemQuery extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            return QueryItemApi.createHandle();
        }
    }
}
