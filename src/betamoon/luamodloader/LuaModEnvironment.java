package betamoon.luamodloader;

import betamoon.luaapi.BetaMoonModule;
import java.io.IOException;
import java.io.InputStream;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.ResourceFinder;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Creates one isolated Lua runtime and module cache for a mod generation. */
final class LuaModEnvironment {
    private LuaModEnvironment() {
    }

    static Globals create(LuaModSource source) throws IOException {
        Globals globals = JsePlatform.standardGlobals();
        globals.load(new BetaMoonModule());

        String prefix = source.modulePathPrefix();
        globals.package_.setLuaPath(prefix + "?.lua;" + prefix + "?/init.lua");
        LuaValue searchers = globals.get("package").get("searchers");
        if (searchers.istable()) {
            searchers.set(3, LuaValue.NIL);
        }

        if (source.layout() != LuaModSource.Layout.SINGLE_FILE) {
            globals.finder = new LuaModResourceFinder(source);
        } else {
            globals.finder = EMPTY_FINDER;
        }

        LuaValue originalRequire = globals.get("require");
        globals.set("require", new ScopedRequire(originalRequire, source.entrypointRelative()));
        return globals;
    }

    private static final ResourceFinder EMPTY_FINDER = new ResourceFinder() {
        @Override
        public InputStream findResource(String filename) {
            return null;
        }
    };

    private static final class ScopedRequire extends VarArgFunction {
        private final LuaValue delegate;
        private final String owner;

        private ScopedRequire(LuaValue delegate, String owner) {
            this.delegate = delegate;
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            try (ScriptExecutionScope ignored = ScriptExecutionScope.open(owner)) {
                return delegate.invoke(args);
            }
        }
    }
}
