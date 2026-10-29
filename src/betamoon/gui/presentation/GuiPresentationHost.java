package betamoon.gui.presentation;

import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.luaj.vm2.LuaTable;

/**
 * Host data and authority adapter; the presentation engine never acquires
 * gameplay access itself.
 */
public interface GuiPresentationHost {
    GuiPresentationSources snapshot();

    Map<String, Object> baseline(GuiNodeDefinition node, GuiPresentationSources sources);

    /** Returns subscriptions owned exclusively by the new runtime revision. */
    List<AutoCloseable> observe(Observer observer);

    LuaTable context(LuaCallbackScope scope);

    /** Creates a read-only binding context using the host's existing native scope. */
    default LuaTable bindingContext(LuaCallbackScope scope) {
        return new LuaTable();
    }

    /**
     * Validates host compatibility and returns an adapter without acquiring
     * subscriptions.
     */
    GuiPresentationHost forDefinition(GuiDocumentDefinition replacement);

    interface Observer {
        void changed(String source, Object previous, Object value);

        void event(String type, Supplier<LuaTable> payload);
    }
}
