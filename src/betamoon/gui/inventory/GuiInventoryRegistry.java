package betamoon.gui.inventory;

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
 * Inventory presentation declarations and requests, independent of native
 * container instances.
 */
public final class GuiInventoryRegistry {
    private final Map<String, GuiInventoryProfile> definitions = new LinkedHashMap<>();
    private final Map<String, ScriptResourceTracker.Cleanup> cleanups = new LinkedHashMap<>();
    private final Map<String, GuiInventoryActivation> activations = new LinkedHashMap<>();
    private long revision;

    public void add(GuiInventoryProfile definition) {
        LuaCallbackScope.requireUiStateWrite();
        if (!definition.available()) {
            throw new IllegalStateException("Inventory profile owner expired");
        }
        GuiInventoryProfile previous = definitions.get(definition.key);
        if (previous != null && !previous.owner.equals(definition.owner)) {
            throw new IllegalArgumentException("Inventory profile belongs to another owner");
        }
        if (previous == null && definitions.size() >= 128) {
            throw new IllegalArgumentException("Inventory registry exceeds 128 profiles");
        }
        ScriptResourceTracker.Cleanup cleanup = () -> {
            if (definitions.get(definition.key) == definition) {
                definitions.remove(definition.key);
                cleanups.remove(definition.key);
                GuiInventoryActivation activation = activations.get(definition.key);
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

    public GuiInventoryProfile get(String key) {
        GuiInventoryProfile definition = definitions.get(GuiComponentDefinition.key(key));
        return definition != null && definition.available() ? definition : null;
    }

    public GuiInventoryProfile getRequired(String key) {
        GuiInventoryProfile definition = get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown inventory profile: " + key);
        }
        return definition;
    }

    public GuiInventoryActivation activate(String owner, String key) {
        LuaCallbackScope.requireUiStateWrite();
        if (!getRequired(key).owner.equals(owner)) {
            throw new IllegalArgumentException("Inventory profile belongs to another owner");
        }
        if (activations.containsKey(key)) {
            throw new IllegalArgumentException("Inventory profile is already activated: " + key);
        }
        long own = activations.values().stream().filter(value -> value.owner.equals(owner)).count();
        if (activations.size() >= 32 || own >= 8) {
            throw new IllegalArgumentException("Inventory activation capacity exceeded");
        }
        GuiInventoryActivation[] result = new GuiInventoryActivation[1];
        ScriptResourceTracker.Cleanup[] tracked = new ScriptResourceTracker.Cleanup[1];
        Runnable cleanup = () -> {
            if (activations.get(key) == result[0]) {
                activations.remove(key);
                revision++;
            }
            ScriptResourceTracker.untrack(owner, tracked[0]);
        };
        GuiInventoryActivation activation = new GuiInventoryActivation(owner, key, cleanup);
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

    public List<GuiInventoryActivation> activations() {
        return Collections.unmodifiableList(new ArrayList<>(activations.values()));
    }
}
