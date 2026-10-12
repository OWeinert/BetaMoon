package betamoon.client.control.input;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compiled named-action map, independent from callbacks and client state. */
public final class InputMapDefinition {
    public static final int MAX_ACTIONS = 128;

    private final ContentKey key;
    private final Map<String, InputActionDefinition> actions;
    private final String label;
    private final String description;
    private final boolean configurable;

    public InputMapDefinition(ContentKey key, List<InputActionDefinition> actions) {
        this(key, actions, null, null, true);
    }

    public InputMapDefinition(ContentKey key, List<InputActionDefinition> actions, String label, String description,
            boolean configurable) {
        if (key == null || !ContentType.INPUT_MAP.equals(key.type())) {
            throw new IllegalArgumentException("Input map key must have type 'input_map'");
        }
        if (actions == null || actions.isEmpty() || actions.size() > MAX_ACTIONS) {
            throw new IllegalArgumentException("Input maps require 1.." + MAX_ACTIONS + " actions");
        }
        LinkedHashMap<String, InputActionDefinition> copy = new LinkedHashMap<String, InputActionDefinition>();
        for (InputActionDefinition action : actions) {
            if (action == null || copy.put(action.name(), action) != null) {
                throw new IllegalArgumentException("Input map contains a null or duplicate action");
            }
            rejectOverlappingDefaults(copy, action);
        }
        this.key = key;
        this.actions = Collections.unmodifiableMap(copy);
        if (label != null && (label.trim().isEmpty() || label.length() > 128)) {
            throw new IllegalArgumentException("Input map label must have 1..128 characters");
        }
        if (description != null && (description.trim().isEmpty() || description.length() > 512)) {
            throw new IllegalArgumentException("Input map description must have 1..512 characters");
        }
        this.label = label;
        this.description = description;
        this.configurable = configurable;
    }

    private static void rejectOverlappingDefaults(Map<String, InputActionDefinition> actions,
            InputActionDefinition candidate) {
        for (InputActionDefinition existing : actions.values()) {
            if (existing == candidate) {
                continue;
            }
            for (InputBinding left : existing.bindings()) {
                for (InputBinding right : candidate.bindings()) {
                    if (left.conflictsWith(right)) {
                        throw new IllegalArgumentException("Input binding " + right + " for action '" + candidate.name()
                                + "' overlaps " + left + " on action '" + existing.name() + "'");
                    }
                }
            }
        }
    }

    public ContentKey key() {
        return key;
    }

    public InputActionDefinition action(String name) {
        return actions.get(name);
    }

    public List<InputActionDefinition> actions() {
        return Collections.unmodifiableList(new ArrayList<InputActionDefinition>(actions.values()));
    }

    public String label() {
        return label == null ? key.name().replace('_', ' ') : label;
    }

    public String description() {
        return description;
    }

    public boolean configurable() {
        return configurable;
    }
}
