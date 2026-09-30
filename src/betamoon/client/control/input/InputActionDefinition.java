package betamoon.client.control.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One named action and its immutable default bindings. */
public final class InputActionDefinition {
    private final String name;
    private final List<InputBinding> bindings;

    public InputActionDefinition(String name, List<InputBinding> bindings) {
        if (!isValidName(name)) {
            throw new IllegalArgumentException(
                    "Input action names use lowercase letters, digits, '_' and '-': " + name);
        }
        if (bindings == null || bindings.isEmpty()) {
            throw new IllegalArgumentException("Input action requires at least one binding: " + name);
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
}
