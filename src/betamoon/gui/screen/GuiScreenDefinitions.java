package betamoon.gui.screen;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Definition lookup without requiring an inline host to publish global
 * registrations.
 */
public interface GuiScreenDefinitions {
    GuiScreenDefinition getRequired(String key);

    Map<String, GuiScreenDefinition> snapshot();

    static Inline inline(GuiScreenDefinitions parent, String key, Supplier<GuiScreenDefinition> definition) {
        return new Inline(parent, key, definition);
    }

    /**
     * Closing releases the supplier so stale activation handles cannot retain a
     * native host.
     */
    final class Inline implements GuiScreenDefinitions, AutoCloseable {
        private final GuiScreenDefinitions parent;
        private final String key;
        private Supplier<GuiScreenDefinition> definition;

        private Inline(GuiScreenDefinitions parent, String key, Supplier<GuiScreenDefinition> definition) {
            this.parent = Objects.requireNonNull(parent, "parent definitions");
            this.key = Objects.requireNonNull(key, "inline key");
            this.definition = Objects.requireNonNull(definition, "inline definition");
        }

        public GuiScreenDefinition getRequired(String requested) {
            if (!key.equals(requested)) {
                return parent.getRequired(requested);
            }
            GuiScreenDefinition current = current();
            if (current == null) {
                throw new IllegalArgumentException("Inline document is unavailable: " + key);
            }
            current.requireActive();
            return current;
        }

        private GuiScreenDefinition current() {
            GuiScreenDefinition current = definition == null ? null : definition.get();
            if (current != null && !key.equals(current.key)) {
                throw new IllegalArgumentException("Inline document identity changed");
            }
            return current;
        }

        public Map<String, GuiScreenDefinition> snapshot() {
            Map<String, GuiScreenDefinition> values = new LinkedHashMap<>(parent.snapshot());
            GuiScreenDefinition current = current();
            if (current == null) {
                values.remove(key);
            } else {
                values.put(key, current);
            }
            return Collections.unmodifiableMap(values);
        }

        public void close() {
            definition = null;
        }
    }
}
