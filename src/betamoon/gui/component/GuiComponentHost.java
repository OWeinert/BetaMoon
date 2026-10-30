package betamoon.gui.component;

import betamoon.gui.presentation.GuiDocumentDefinition;
import betamoon.gui.presentation.GuiNodeDefinition;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPresentationSources;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaTable;

/**
 * Adds local keyed state to the existing observed source contract; preserves
 * the actual host's native entries.
 */
final class GuiComponentHost implements GuiPresentationHost {
    private final GuiPresentationHost host;
    private final GuiComponentPlan plan;

    GuiComponentHost(GuiPresentationHost host, GuiComponentPlan plan) {
        this.host = host;
        this.plan = plan;
    }

    public GuiPresentationSources snapshot() {
        GuiPresentationSources base = host.snapshot();
        plan.sources = base;
        Map<String, Object> state = new LinkedHashMap<>(base.state);
        for (Map.Entry<String, Object> value : plan.state.snapshot().entrySet()) {
            if (state.containsKey(value.getKey())) {
                throw new IllegalArgumentException("Host/component state identities overlap");
            }
            state.put(value.getKey(), value.getValue());
        }
        return new GuiPresentationSources(state, base.data, base.nativeData);
    }

    public Map<String, Object> baseline(GuiNodeDefinition node, GuiPresentationSources sources) {
        return host.baseline(node, sources);
    }

    public List<AutoCloseable> observe(Observer observer) {
        List<AutoCloseable> subscriptions = new ArrayList<>(host.observe(observer));
        try {
            subscriptions.add(plan.state.observe(observer));
        } catch (RuntimeException | Error failure) {
            for (AutoCloseable subscription : subscriptions) {
                try {
                    subscription.close();
                } catch (Exception cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        }
        return subscriptions;
    }

    public LuaTable context(LuaCallbackScope scope) {
        return host.context(scope);
    }

    public LuaTable bindingContext(LuaCallbackScope scope) {
        return host.bindingContext(scope);
    }

    public GuiPresentationHost forDefinition(GuiDocumentDefinition replacement) {
        GuiPresentationHost next = host.forDefinition(replacement);
        return replacement instanceof GuiComponentDocument
                ? new GuiComponentHost(next, ((GuiComponentDocument) replacement).plan)
                : next;
    }
}
