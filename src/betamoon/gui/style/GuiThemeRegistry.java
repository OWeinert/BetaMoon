package betamoon.gui.style;

import betamoon.gui.component.GuiComponentDefinition;
import betamoon.gui.component.GuiComponentLimits;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GuiThemeRegistry {
    private final Map<String, Entry> definitions = new LinkedHashMap<>();
    public void add(GuiThemeDefinition definition) {
        LuaCallbackScope.requireUiStateWrite();
        definition.requireActive();
        Entry previous = definitions.get(definition.key);
        if (previous != null && !previous.definition.owner.equals(definition.owner)) {
            throw new IllegalArgumentException("Theme key belongs to another owner");
        }
        if (previous == null && definitions.size() >= 256) {
            throw new IllegalArgumentException("Theme registry exceeds 256 definitions");
        }
        long total = definition.metadataBytes;
        long owner = total;
        for (Entry entry : definitions.values()) {
            if (entry != previous) {
                total += entry.definition.metadataBytes;
                if (entry.definition.owner.equals(definition.owner)) {
                    owner += entry.definition.metadataBytes;
                }
            }
        }
        if (total > GuiComponentLimits.REGISTRY_METADATA_BYTES || owner > GuiComponentLimits.OWNER_METADATA_BYTES) {
            throw new IllegalArgumentException("Theme metadata byte budget exceeded");
        }
        Entry next = new Entry(definition);
        ScriptExecution.withOwner(definition.owner, () -> {
            ScriptResourceTracker.track(next.cleanup);
            return null;
        });
        definitions.put(definition.key, next);
        if (previous != null) {
            ScriptResourceTracker.untrack(previous.definition.owner, previous.cleanup);
        }
    }

    public GuiThemeDefinition get(String key) {
        Entry entry = definitions.get(GuiComponentDefinition.key(key));
        if (entry == null) {
            return null;
        }
        entry.definition.requireActive();
        return entry.definition;
    }

    public GuiThemeDefinition getRequired(String key) {
        GuiThemeDefinition value = get(key);
        if (value == null) {
            throw new IllegalArgumentException("Unknown theme: " + key);
        }
        return value;
    }
    private final class Entry {
        final GuiThemeDefinition definition;
        final ScriptResourceTracker.Cleanup cleanup;
        Entry(GuiThemeDefinition definition) {
            this.definition = definition;
            cleanup = () -> {
                if (definitions.get(definition.key) == this) {
                    definitions.remove(definition.key);
                }
            };
        }
    }
}
