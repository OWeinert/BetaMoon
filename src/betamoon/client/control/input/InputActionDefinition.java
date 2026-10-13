package betamoon.client.control.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.EnumSet;

/** One named action and its immutable default bindings. */
public final class InputActionDefinition {
    private final String name;
    private final List<InputBinding> bindings;
    private final String label;
    private final String description;
    private final boolean configurable;
    private final EnumSet<InputActivationMode> modes;
    private final InputActivationMode defaultMode;

    public InputActionDefinition(String name, List<InputBinding> bindings) {
        this(name, bindings, null, null, true, EnumSet.of(InputActivationMode.HOLD), InputActivationMode.HOLD);
    }

    public InputActionDefinition(String name, List<InputBinding> bindings, String label, String description,
            boolean configurable, EnumSet<InputActivationMode> modes, InputActivationMode defaultMode) {
        if (!isValidName(name)) {
            throw new IllegalArgumentException(
                    "Input action names use lowercase letters, digits, '_' and '-': " + name);
        }
        if (bindings == null || bindings.isEmpty() || bindings.size() > 8) {
            throw new IllegalArgumentException("Input action requires 1..8 default bindings: " + name);
        }
        ArrayList<InputBinding> copy = new ArrayList<InputBinding>();
        for (InputBinding binding : bindings) {
            if (binding == null) {
                throw new IllegalArgumentException("Input action bindings cannot contain null: " + name);
            }
            for (InputBinding existing : copy) {
                if (binding.conflictsWith(existing)) {
                    throw new IllegalArgumentException(
                            "Overlapping input bindings for action '" + name + "': " + existing + " and " + binding);
                }
            }
            copy.add(binding);
        }
        this.name = name;
        this.bindings = Collections.unmodifiableList(copy);
        this.label = presentation(label, 128, "Action label");
        this.description = presentation(description, 512, "Action description");
        this.configurable = configurable;
        if (modes == null || modes.isEmpty() || defaultMode == null || !modes.contains(defaultMode)) {
            throw new IllegalArgumentException("Input action default mode must be in its allowed modes: " + name);
        }
        this.modes = modes.clone();
        this.defaultMode = defaultMode;
    }

    private static String presentation(String value, int limit, String field) {
        if (value != null && (value.trim().isEmpty() || value.length() > limit)) {
            throw new IllegalArgumentException(field + " must have 1.." + limit + " characters");
        }
        return value;
    }

    private static boolean isValidName(String value) {
        if (value == null || value.length() == 0 || value.length() > 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (!(character >= 'a' && character <= 'z') && !(character >= '0' && character <= '9') && character != '_'
                    && character != '-') {
                return false;
            }
        }
        return true;
    }

    public String name() {
        return name;
    }

    public List<InputBinding> bindings() {
        return bindings;
    }

    public String label() {
        return label == null ? name.replace('_', ' ') : label;
    }

    public String description() {
        return description;
    }

    public boolean configurable() {
        return configurable;
    }

    public EnumSet<InputActivationMode> modes() {
        return modes.clone();
    }

    public InputActivationMode defaultMode() {
        return defaultMode;
    }
}
