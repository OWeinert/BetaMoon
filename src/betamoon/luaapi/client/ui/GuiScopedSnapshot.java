package betamoon.luaapi.client.ui;

import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.Varargs;

/**
 * Scoped read-only host observations may be consumed by an authored component's
 * callback owner.
 */
public final class GuiScopedSnapshot {
    private final LuaValue detached;
    private final String source;
    private final Object revision;

    public GuiScopedSnapshot(Map<String, Object> values) {
        this(values, null, null);
    }

    public GuiScopedSnapshot(Map<String, Object> values, String source, Object revision) {
        detached = GuiReadOnlyValues.fromPublished(values);
        this.source = source;
        this.revision = revision;
    }

    public Object revision() {
        return revision;
    }

    public static LuaTable create(LuaCallbackScope scope, String method, Map<String, Object> values) {
        return new GuiScopedSnapshot(values).access(scope, method);
    }

    /**
     * Reuses one immutable publication across any number of scoped
     * callbacks/bindings.
     */
    public LuaTable access(LuaCallbackScope scope, String method) {
        LuaTable access = new LuaTable();
        access.set(method, new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                if (source != null) {
                    scope.readUiSource(source, revision);
                }
                return detached;
            }
        });
        return access;
    }

}
