package betamoon.client.control.input;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Runtime registration for one immutable action map. */
public final class InputMapRegistration implements AutoCloseable {
    private final InputRouter router;
    private final InputMapDefinition definition;
    private final String owner;
    private final Map<String, List<InputBinding>> rebound = new HashMap<String, List<InputBinding>>();
    private final Map<String, List<InputBinding>> savedBindings = new HashMap<String, List<InputBinding>>();
    private final Map<String, InputActivationMode> selectedModes = new HashMap<String, InputActivationMode>();
    private boolean active = true;

    InputMapRegistration(InputRouter router, InputMapDefinition definition, String owner) {
        this.router = router;
        this.definition = definition;
        this.owner = owner;
        InputPreferenceStore preferences = router.preferences();
        if (preferences != null) {
            for (InputActionDefinition action : definition.actions()) {
                InputPreferenceStore.Preference saved = preferences.get(definition.key(), action.name());
                if (saved == null) {
                    continue;
                }
                if (saved.bindings() != null) {
                    try {
                        List<InputBinding> candidate = unique(saved.bindings());
                        InputActivationMode effectiveMode = saved.mode() != null && action.modes().contains(saved.mode())
                                ? saved.mode() : action.defaultMode();
                        validateBindingsForMode(action.name(), candidate, effectiveMode);
                        savedBindings.put(action.name(), Collections.unmodifiableList(candidate));
                    } catch (IllegalArgumentException ignored) {
                        // A changed declaration wins over an incompatible saved override.
                    }
                }
                if (saved.mode() != null && action.modes().contains(saved.mode())) {
                    selectedModes.put(action.name(), saved.mode());
                }
            }
            boolean removed;
            do {
                removed = false;
                for (InputActionDefinition action : definition.actions()) {
                    List<InputBinding> candidate = savedBindings.get(action.name());
                    if (candidate == null) {
                        continue;
                    }
                    try {
                        rejectConflicts(action.name(), candidate);
                    } catch (IllegalArgumentException ignored) {
                        savedBindings.remove(action.name());
                        removed = true;
                    }
                }
            } while (removed);
        }
    }

    public InputContext activate(int priority, EnumSet<InputFamily> captures) {
        requireActive();
        return router.activate(this, priority, captures == null ? EnumSet.noneOf(InputFamily.class) : captures);
    }

    public InputActionState state(String action) {
        requireActive();
        if (definition.action(action) == null) {
            throw new IllegalArgumentException("Unknown input action '" + action + "' in " + definition.key());
        }
        return router.state(this, action);
    }

    public void rebind(String action, List<InputBinding> bindings, boolean allowConflicts) {
        requireActive();
        requireAction(action);
        if (bindings == null || bindings.isEmpty() || bindings.size() > 8) {
            throw new IllegalArgumentException("Input actions require 1..8 effective bindings");
        }
        List<InputBinding> copy = unique(bindings);
        validateBindingsForMode(action, copy, mode(action));
        if (!allowConflicts) {
            rejectConflicts(action, copy);
        }
        rebound.put(action, Collections.unmodifiableList(copy));
        router.refreshBindings(this, action);
    }

    public void resetBinding(String action) {
        requireActive();
        requireAction(action);
        if (savedBindings.containsKey(action)) {
            rebound.put(action, definition.action(action).bindings());
            router.refreshBindings(this, action);
        } else if (rebound.remove(action) != null) {
            router.refreshBindings(this, action);
        }
    }

    public List<InputBinding> bindings(String action) {
        requireAction(action);
        List<InputBinding> replacement = rebound.get(action);
        if (replacement != null) {
            return replacement;
        }
        List<InputBinding> saved = savedBindings.get(action);
        return saved == null ? definition.action(action).bindings() : saved;
    }

    public void setPreferredBindings(String action, List<InputBinding> bindings, boolean allowConflicts) {
        requireActive();
        requireAction(action);
        if (bindings == null || bindings.size() > 8) {
            throw new IllegalArgumentException("Saved input bindings require 0..8 entries");
        }
        List<InputBinding> copy = unique(bindings);
        validateBindingsForMode(action, copy, mode(action));
        if (!allowConflicts) {
            rejectConflicts(action, copy);
        }
        InputPreferenceStore preferences = requirePreferences();
        try {
            preferences.setBindings(definition.key(), action, copy);
        } catch (IOException error) {
            throw new IllegalStateException("Input preference save failed: " + error.getMessage(), error);
        }
        savedBindings.put(action, Collections.unmodifiableList(copy));
        rebound.remove(action);
        router.refreshBindings(this, action);
    }

    public void resetPreferredBindings(String action) {
        requireActive();
        requireAction(action);
        try {
            requirePreferences().resetBindings(definition.key(), action);
        } catch (IOException error) {
            throw new IllegalStateException("Input preference save failed: " + error.getMessage(), error);
        }
        savedBindings.remove(action);
        rebound.remove(action);
        router.refreshBindings(this, action);
    }

    public InputActivationMode mode(String action) {
        requireAction(action);
        InputActivationMode selected = selectedModes.get(action);
        return selected == null ? definition.action(action).defaultMode() : selected;
    }

    public void setMode(String action, InputActivationMode mode) {
        requireActive();
        requireAction(action);
        if (mode == null || !definition.action(action).modes().contains(mode)) {
            throw new IllegalArgumentException("Mode is not allowed for input action '" + action + "'");
        }
        validateBindingsForMode(action, bindings(action), mode);
        try {
            requirePreferences().setMode(definition.key(), action, mode);
        } catch (IOException error) {
            throw new IllegalStateException("Input preference save failed: " + error.getMessage(), error);
        }
        selectedModes.put(action, mode);
        router.refreshMode(this, action);
    }

    public void resetMode(String action) {
        requireActive();
        requireAction(action);
        try {
            requirePreferences().resetMode(definition.key(), action);
        } catch (IOException error) {
            throw new IllegalStateException("Input preference save failed: " + error.getMessage(), error);
        }
        selectedModes.remove(action);
        router.refreshMode(this, action);
    }

    public void resetAllPreferences() {
        requireActive();
        try {
            requirePreferences().resetMap(definition.key());
        } catch (IOException error) {
            throw new IllegalStateException("Input preference save failed: " + error.getMessage(), error);
        }
        savedBindings.clear();
        selectedModes.clear();
        rebound.clear();
        for (InputActionDefinition action : definition.actions()) {
            router.refreshBindings(this, action.name());
        }
    }

    private InputPreferenceStore requirePreferences() {
        InputPreferenceStore preferences = router.preferences();
        if (preferences == null) {
            throw new IllegalStateException("Saved input preferences are unavailable");
        }
        return preferences;
    }

    private void requireAction(String action) {
        if (definition.action(action) == null) {
            throw new IllegalArgumentException("Unknown input action '" + action + "' in " + definition.key());
        }
    }

    private static void validateBindingsForMode(String action, List<InputBinding> bindings, InputActivationMode mode) {
        if (mode == InputActivationMode.TOGGLE) {
            for (InputBinding binding : bindings) {
                if (binding.device() == InputDeviceEvent.Device.MOUSE_WHEEL) {
                    throw new IllegalArgumentException("Wheel binding cannot use toggle mode for action '" + action + "'");
                }
            }
        }
    }

    private static List<InputBinding> unique(List<InputBinding> bindings) {
        List<InputBinding> result = new ArrayList<InputBinding>();
        for (InputBinding binding : bindings) {
            if (binding == null) {
                throw new IllegalArgumentException("Input bindings cannot contain null");
            }
            for (InputBinding existing : result) {
                if (binding.conflictsWith(existing)) {
                    throw new IllegalArgumentException("Overlapping input bindings: " + existing + " and " + binding);
                }
            }
            result.add(binding);
        }
        return result;
    }

    private void rejectConflicts(String action, List<InputBinding> candidate) {
        for (InputActionDefinition existing : definition.actions()) {
            if (existing.name().equals(action)) {
                continue;
            }
            for (InputBinding left : candidate) {
                for (InputBinding right : bindings(existing.name())) {
                    if (left.conflictsWith(right)) {
                        throw new IllegalArgumentException(
                                "Input binding " + left + " conflicts with action '" + existing.name() + "'");
                    }
                }
            }
        }
    }

    private void requireActive() {
        if (!active) {
            throw new IllegalStateException("Input map is no longer registered");
        }
    }

    public InputMapDefinition definition() {
        return definition;
    }

    public String owner() {
        return owner;
    }

    public boolean isActive() {
        return active;
    }

    void deactivate() {
        active = false;
        rebound.clear();
        savedBindings.clear();
        selectedModes.clear();
    }

    @Override
    public void close() {
        if (active) {
            router.unregister(this);
        }
    }
}
