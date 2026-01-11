package betamoon.luaapi;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Lua entrypoint for world generation configuration.
 *
 * <p>Exposes a scoped handle so mods can group multiple world-gen registrations
 * and finish them explicitly.</p>
 */
final class WorldGenApi {
    /**
     * Utility class that installs world-gen-related Lua bindings.
     */
    private WorldGenApi() {
    }

    static void attach(LuaTable module) {
        module.set("startWorldGen", new StartWorldGen());
    }

    private static final class StartWorldGen extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            return new WorldGenHandle();
        }
    }

    static final class WorldGenHandle extends LuaTable {
        private boolean finished;

        private WorldGenHandle() {
            // Register world-gen sub-features on a single handle for Lua chaining.
            set("addOreGen", BlockOreGenApi.createWorldGenAddOreGen(this));
            set("addBiomeGen", BiomeGenApi.createBiomeGen(this));
            set("addBiomeGenFromDefault", BiomeGenApi.createBiomeGenFromDefault(this));
            set("endWorldGen", new EndWorldGen(this));
        }

        /**
         * Ensures the handle is still active before allowing mutations.
         */
        void ensureActive() {
            if (finished) {
                throw new LuaError("World generation handle already finished.");
            }
        }
    }

    private static final class EndWorldGen extends VarArgFunction {
        private final WorldGenHandle handle;

        private EndWorldGen(WorldGenHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.ensureActive();
            handle.finished = true;
            return LuaValue.NIL;
        }
    }
}
