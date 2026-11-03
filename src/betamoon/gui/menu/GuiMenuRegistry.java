package betamoon.gui.menu;

import betamoon.gui.component.GuiComponentDefinition;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Bounded owned declarations and activation claims, separate from native
 * adapters.
 */
public final class GuiMenuRegistry {
    private final Map<String, GuiMenuDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, ScriptResourceTracker.Cleanup> cleanups = new LinkedHashMap<>();
    private final Map<String, GuiMenuActivation> activations = new LinkedHashMap<>();
    private long revision;

    public void add(GuiMenuDefinition definition) {
        LuaCallbackScope.requireUiStateWrite();
        if (!definition.available()) {
            throw new IllegalStateException("Menu definition owner expired");
        }
        GuiMenuDefinition previous = definitions.get(definition.key);
        if (previous != null && !previous.owner.equals(definition.owner)) {
            throw new IllegalArgumentException("Menu key belongs to another owner");
        }
        if (previous == null && definitions.size() >= 256) {
            throw new IllegalArgumentException("Menu registry exceeds 256 definitions");
        }
        Map<String, GuiMenuDefinition> next = new LinkedHashMap<>(definitions);
        next.put(definition.key, definition);
        Set<String> checked = new HashSet<>();
        for (GuiMenuDefinition value : next.values()) {
            if (checked.add(value.target + "/" + value.region)) {
                GuiMenuOrder.order(next, value.target, value.region, false);
            }
        }
        ScriptResourceTracker.Cleanup cleanup = () -> {
            if (definitions.get(definition.key) == definition) {
                definitions.remove(definition.key);
                cleanups.remove(definition.key);
                GuiMenuActivation activation = activations.get(definition.key);
                if (activation != null) {
                    activation.close();
                }
                revision++;
            }
        };
        ScriptExecution.withOwner(definition.owner, () -> {
            ScriptResourceTracker.track(cleanup);
            return null;
        });
        ScriptResourceTracker.Cleanup old = cleanups.put(definition.key, cleanup);
        definitions.put(definition.key, definition);
        if (old != null) {
            ScriptResourceTracker.untrack(definition.owner, old);
        }
        revision++;
    }

    public GuiMenuDefinition get(String key) {
        GuiMenuDefinition value = definitions.get(GuiComponentDefinition.key(key));
        return value != null && value.available() ? value : null;
    }

    public GuiMenuDefinition getRequired(String key) {
        GuiMenuDefinition value = get(key);
        if (value == null) {
            throw new IllegalArgumentException("Unknown menu definition: " + key);
        }
        return value;
    }

    public GuiMenuActivation activate(String owner, String key) {
        LuaCallbackScope.requireUiStateWrite();
        GuiMenuDefinition definition = getRequired(key);
        if (!definition.owner.equals(owner)) {
            throw new IllegalArgumentException("Menu belongs to another owner");
        }
        if (activations.containsKey(key)) {
            throw new IllegalArgumentException("Menu is already activated: " + key);
        }
        long own = activations.values().stream().filter(value -> value.owner.equals(owner)).count();
        if (activations.size() >= 32 || own >= 8) {
            throw new IllegalArgumentException("Menu activation capacity exceeded");
        }
        GuiMenuActivation[] result = new GuiMenuActivation[1];
        ScriptResourceTracker.Cleanup[] tracked = new ScriptResourceTracker.Cleanup[1];
        ScriptResourceTracker.Cleanup cleanup = () -> {
            if (activations.get(key) == result[0]) {
                activations.remove(key);
                revision++;
            }
            ScriptResourceTracker.untrack(owner, tracked[0]);
        };
        GuiMenuActivation activation = new GuiMenuActivation(owner, key, cleanup::run);
        result[0] = activation;
        tracked[0] = activation::close;
        ScriptExecution.withOwner(owner, () -> {
            ScriptResourceTracker.track(tracked[0]);
            return null;
        });
        activations.put(key, activation);
        revision++;
        return activation;
    }

    public long revision() {
        return revision;
    }

    public Map<String, GuiMenuDefinition> snapshot() {
        Map<String, GuiMenuDefinition> result = new LinkedHashMap<>();
        for (GuiMenuDefinition value : definitions.values()) {
            if (value.available()) {
                result.put(value.key, value);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public List<GuiMenuActivation> activations() {
        return Collections.unmodifiableList(new ArrayList<>(activations.values()));
    }
}
