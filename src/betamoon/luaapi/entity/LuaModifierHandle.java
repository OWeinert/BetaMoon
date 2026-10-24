package betamoon.luaapi.entity;

import betamoon.entity.modifier.EntityModifierRuntime.Source;
import betamoon.entity.motion.EntitySimulationThread;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luaapi.utils.LuaDetachedData;
import betamoon.luamodloader.LuaScriptRegistry;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Owned generation-aware source handle; no callback entity facade is retained.
 */
final class LuaModifierHandle extends LuaTable {
    private final Source source;

    LuaModifierHandle(Source source) {
        this.source = source;
        set("update", mutation(args -> source
                .update(EntityModifierDeclarations.patch(source.definition(), argument(args, 1), source.owner()))));
        set("clear", mutation(args -> source.update(EntityModifierDeclarations.clear(source.definition(),
                LuaDeclarationValues.string(argument(args, 1), "modifier.clear")))));
        set("setEnabled", mutation(args -> {
            LuaValue enabled = argument(args, 1);
            if (!enabled.isboolean()) {
                throw LuaDeclarationValues.error("modifier.setEnabled", "expected a boolean");
            }
            source.setEnabled(enabled.toboolean());
        }));
        set("retry", mutation(args -> source.retry()));
        set("close", mutation(args -> source.close()));
        set("isValid", query(() -> LuaValue.valueOf(source.isValid())));
        set("isEnabled", query(() -> LuaValue.valueOf(source.definition().enabled)));
        set("getStatus", query(() -> LuaDetachedData.copy(source.status())));
    }

    private VarArgFunction mutation(Consumer<Varargs> action) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                EntitySimulationThread.requireCurrent();
                LuaCallbackScope.requireGameplayWrite();
                if (!source.owner().equals(LuaScriptRegistry.getCurrentScriptFile())) {
                    throw LuaDeclarationValues.error("modifier", "only the declaring script may change this source");
                }
                try {
                    action.accept(args);
                    return NIL;
                } catch (IllegalArgumentException | IllegalStateException error) {
                    throw LuaDeclarationValues.error("modifier", error.getMessage());
                }
            }
        };
    }

    private VarArgFunction query(Supplier<LuaValue> value) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                EntitySimulationThread.requireCurrent();
                return value.get();
            }
        };
    }

    private LuaValue argument(Varargs args, int index) {
        return args.arg(index + (args.arg1() == this ? 1 : 0));
    }
}
