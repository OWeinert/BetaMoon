package betamoon.luaapi.utils;

import betamoon.luaapi.resource.OverrideManager;
import java.util.function.BiConsumer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Builds layered callback properties for immutable-identity runtime definitions. */
public final class LuaDefinitionCallbackLayers {
    private LuaDefinitionCallbackLayers() {
    }

    public static <T> OverrideManager.Property<T, LuaOverrideLayers<LuaOverrideDefinition>> property(
            String name, final LuaValue original, final BiConsumer<T, LuaValue> writer) {
        return new OverrideManager.Property<T, LuaOverrideLayers<LuaOverrideDefinition>>(name,
                new OverrideManager.PropertyAdapter<T, LuaOverrideLayers<LuaOverrideDefinition>>() {
                    public LuaOverrideLayers<LuaOverrideDefinition> read(T target) {
                        return null;
                    }

                    public void write(T target, LuaOverrideLayers<LuaOverrideDefinition> layers) {
                        writer.accept(target, layers == null ? original : layered(original, layers));
                    }
                }, LuaOverrideLayers.resolver());
    }

    private static LuaValue layered(final LuaValue original,
            LuaOverrideLayers<LuaOverrideDefinition> definitions) {
        final LuaOverrideCallback callback = new LuaOverrideCallback(definitions);
        return new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                final LuaTable context = arguments.arg1().checktable();
                return callback.invoke(context, arguments,
                        () -> original.isnil() ? LuaValue.NIL : original.invoke(arguments).arg1(),
                        value -> value);
            }
        };
    }
}
