package betamoon.gui.screen;

import betamoon.gui.component.GuiAuthoredNode;
import betamoon.gui.component.GuiComponentDefinition;
import betamoon.gui.component.GuiComponentLimits;
import betamoon.gui.component.GuiComponentSchema;
import betamoon.gui.presentation.GuiCapability;
import betamoon.gui.graphics.GuiResourceDefinitions;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.luaj.vm2.LuaValue;

/**
 * A screen declaration owns contracts; activations own scenes, state and native
 * leases.
 */
public final class GuiScreenDefinition {
    public enum Context {
        CLIENT, WORLD
    }

    public final String owner;
    public final String key;
    public final String theme;
    public final Context context;
    public final boolean pauseGame;
    public final int updateInterval;
    public final GuiAuthoredNode root;
    public final GuiComponentSchema state;
    public final GuiComponentSchema data;
    public final Map<String, GuiActionDefinition> actions;
    public final Map<String, LuaValue> callbacks;
    public final GuiResourceDefinitions resources;
    public final Set<GuiCapability> requirements;
    public final long metadataBytes;
    private final ScriptResourceTracker.OwnerGeneration ownerState;
    private final long generation;

    public GuiScreenDefinition(String owner, String key, Context context, boolean pauseGame, int updateInterval,
            String theme, GuiAuthoredNode root, GuiComponentSchema state, GuiComponentSchema data,
            Map<String, GuiActionDefinition> actions, Map<String, LuaValue> callbacks, GuiResourceDefinitions resources,
            Set<GuiCapability> requirements) {
        this.owner = Objects.requireNonNull(owner, "screen owner");
        this.key = GuiComponentDefinition.key(key);
        this.context = Objects.requireNonNull(context, "screen context");
        this.root = Objects.requireNonNull(root, "screen root");
        this.state = Objects.requireNonNull(state, "screen state schema");
        this.data = Objects.requireNonNull(data, "screen data schema");
        state.normalize(Collections.emptyMap());
        data.normalize(Collections.emptyMap());
        if (updateInterval < 1 || updateInterval > 1200 || actions == null || actions.size() > 32 || callbacks == null
                || callbacks.size() > 4 || requirements == null || theme == null || theme.length() > 255) {
            throw new IllegalArgumentException("Invalid screen contracts, callback interval or action capacity");
        }
        for (Map.Entry<String, GuiActionDefinition> action : actions.entrySet()) {
            if (action.getValue() == null || !action.getKey().equals(action.getValue().name)) {
                throw new IllegalArgumentException("Screen action identity mismatch");
            }
        }
        for (Map.Entry<String, LuaValue> callback : callbacks.entrySet()) {
            if (!(callback.getKey().equals("open") || callback.getKey().equals("refresh")
                    || callback.getKey().equals("update") || callback.getKey().equals("close"))
                    || callback.getValue() == null || !callback.getValue().isfunction()) {
                throw new IllegalArgumentException("Screen callbacks use open, refresh, update or close functions");
            }
        }
        this.resources = Objects.requireNonNull(resources, "screen resources");
        this.pauseGame = pauseGame;
        this.updateInterval = updateInterval;
        this.theme = theme;
        this.actions = Collections.unmodifiableMap(new LinkedHashMap<>(actions));
        this.callbacks = Collections.unmodifiableMap(new LinkedHashMap<>(callbacks));
        this.requirements = Collections.unmodifiableSet(new LinkedHashSet<>(requirements));
        long bytes = state.metadataBytes + data.metadataBytes + resources.metadataBytes
                + GuiComponentLimits.authoredMetadata(root);
        for (GuiActionDefinition action : actions.values()) {
            bytes += action.payload.metadataBytes + 128;
        }
        if (bytes > 8 * 1024 * 1024) {
            throw new IllegalArgumentException("Screen metadata exceeds 8 MiB");
        }
        metadataBytes = bytes;
        ownerState = ScriptResourceTracker.ownerToken(owner);
        generation = ownerState.current();
    }

    public boolean available() {
        return ownerState.matches(generation);
    }

    public void requireActive() {
        if (!available()) {
            throw new IllegalStateException(key + ": screen owner generation expired");
        }
    }
}
