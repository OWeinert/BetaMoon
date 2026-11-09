package betamoon.gui.hud;

import betamoon.gui.component.GuiComponentDefinition;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Notification templates are owned metadata; existing shows retain their
 * template snapshot.
 */
public final class GuiNotificationRegistry {
    private final Map<String, GuiNotificationDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, ScriptResourceTracker.Cleanup> cleanups = new LinkedHashMap<>();

    public void add(GuiNotificationDefinition definition) {
        LuaCallbackScope.requireUiStateWrite();
        if (!definition.available()) {
            throw new IllegalStateException("Notification template owner expired");
        }
        GuiNotificationDefinition previous = definitions.get(definition.key);
        if (previous != null && !previous.owner.equals(definition.owner)) {
            throw new IllegalArgumentException("Notification template belongs to another owner");
        }
        if (previous == null && definitions.size() >= 128) {
            throw new IllegalArgumentException("Notification registry exceeds 128 templates");
        }
        ScriptResourceTracker.Cleanup cleanup = () -> {
            if (definitions.get(definition.key) == definition) {
                definitions.remove(definition.key);
                cleanups.remove(definition.key);
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
    }

    public GuiNotificationDefinition get(String key) {
        GuiNotificationDefinition definition = definitions.get(GuiComponentDefinition.key(key));
        return definition != null && definition.available() ? definition : null;
    }

    public GuiNotificationDefinition getRequired(String key) {
        GuiNotificationDefinition definition = get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown notification template: " + key);
        }
        return definition;
    }
}
