package betamoon.gui.hud;

import betamoon.gui.component.GuiComponentDefinition;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HUD declarations and requests, independent of native screens and cursor
 * ownership.
 */
public final class GuiHudRegistry {
    private final Map<String, GuiHudDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, ScriptResourceTracker.Cleanup> cleanups = new LinkedHashMap<>();
    private final Map<String, GuiHudActivation> activations = new LinkedHashMap<>();
    private long revision;
    private long nextSequence;

    public void add(GuiHudDefinition definition) {
        LuaCallbackScope.requireUiStateWrite();
        if (!definition.available()) {
            throw new IllegalStateException("HUD definition owner expired");
        }
        GuiHudDefinition previous = definitions.get(definition.key);
        if (previous != null && !previous.owner.equals(definition.owner)) {
            throw new IllegalArgumentException("HUD definition belongs to another owner");
        }
        if (previous == null && definitions.size() >= 128) {
            throw new IllegalArgumentException("HUD registry exceeds 128 profiles");
        }
        ScriptResourceTracker.Cleanup cleanup = () -> {
            if (definitions.get(definition.key) == definition) {
                definitions.remove(definition.key);
                cleanups.remove(definition.key);
                GuiHudActivation activation = activations.get(definition.key);
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

    public GuiHudDefinition get(String key) {
        GuiHudDefinition definition = definitions.get(GuiComponentDefinition.key(key));
        return definition != null && definition.available() ? definition : null;
    }

    public GuiHudDefinition getRequired(String key) {
        GuiHudDefinition definition = get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown HUD definition: " + key);
        }
        return definition;
    }

    public GuiHudActivation activate(String owner, String key) {
        LuaCallbackScope.requireUiStateWrite();
        if (!getRequired(key).owner.equals(owner)) {
            throw new IllegalArgumentException("HUD definition belongs to another owner");
        }
        if (activations.containsKey(key)) {
            throw new IllegalArgumentException("HUD definition is already activated: " + key);
        }
        long own = activations.values().stream().filter(value -> value.owner.equals(owner)).count();
        if (activations.size() >= 16 || own >= 4) {
            throw new IllegalArgumentException("HUD activation capacity exceeded");
        }
        GuiHudActivation[] result = new GuiHudActivation[1];
        ScriptResourceTracker.Cleanup[] tracked = new ScriptResourceTracker.Cleanup[1];
        Runnable cleanup = () -> {
            if (activations.get(key) == result[0]) {
                activations.remove(key);
                revision++;
            }
            ScriptResourceTracker.untrack(owner, tracked[0]);
        };
        GuiHudActivation activation = new GuiHudActivation(owner, key, ++nextSequence, cleanup);
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

    public List<GuiHudActivation> activations() {
        return Collections.unmodifiableList(new ArrayList<>(activations.values()));
    }
}
