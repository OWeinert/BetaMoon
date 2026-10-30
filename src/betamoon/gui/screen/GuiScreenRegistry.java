package betamoon.gui.screen;

import betamoon.gui.component.GuiComponentDefinition;
import betamoon.gui.component.GuiComponentLimits;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Definitions remain independent from navigation, scene ownership and action
 * dispatch.
 */
public final class GuiScreenRegistry {
    private final Map<String, Entry> definitions = new LinkedHashMap<>();
    private long revision;

    public void add(GuiScreenDefinition definition) {
        LuaCallbackScope.requireUiStateWrite();
        definition.requireActive();
        Entry previous = definitions.get(definition.key);
        if (previous != null && !previous.definition.owner.equals(definition.owner)) {
            throw new IllegalArgumentException("Screen key belongs to another owner: " + definition.key);
        }
        if (previous == null && definitions.size() >= 256) {
            throw new IllegalArgumentException("Screen registry exceeds 256 definitions");
        }
        long total = definition.metadataBytes;
        long owner = total;
        for (Entry entry : definitions.values()) {
            if (entry == previous) {
                continue;
            }
            total += entry.definition.metadataBytes;
            if (entry.definition.owner.equals(definition.owner)) {
                owner += entry.definition.metadataBytes;
            }
        }
        if (total > GuiComponentLimits.REGISTRY_METADATA_BYTES || owner > GuiComponentLimits.OWNER_METADATA_BYTES) {
            throw new IllegalArgumentException("Screen registry metadata budget exceeded");
        }
        Entry next = new Entry(definition);
        // Tracking succeeds before publication, so a rejected registration cannot
        // replace a usable definition.
        ScriptExecution.withOwner(definition.owner, () -> {
            ScriptResourceTracker.track(next.cleanup);
            return null;
        });
        definitions.put(definition.key, next);
        if (previous != null) {
            ScriptResourceTracker.untrack(previous.definition.owner, previous.cleanup);
        }
        revision++;
    }

    public GuiScreenDefinition get(String key) {
        Entry entry = definitions.get(GuiComponentDefinition.key(key));
        if (entry == null) {
            return null;
        }
        entry.definition.requireActive();
        return entry.definition;
    }

    public GuiScreenDefinition getRequired(String key) {
        GuiScreenDefinition definition = get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown screen: " + key);
        }
        return definition;
    }

    public long revision() {
        return revision;
    }

    public Map<String, GuiScreenDefinition> snapshot() {
        Map<String, GuiScreenDefinition> result = new LinkedHashMap<>();
        for (Entry entry : definitions.values()) {
            result.put(entry.definition.key, entry.definition);
        }
        return Collections.unmodifiableMap(result);
    }

    private final class Entry {
        final GuiScreenDefinition definition;
        final ScriptResourceTracker.Cleanup cleanup;
        Entry(GuiScreenDefinition definition) {
            this.definition = definition;
            cleanup = () -> {
                if (definitions.get(definition.key) == this) {
                    definitions.remove(definition.key);
                    revision++;
                }
            };
        }
    }
}
