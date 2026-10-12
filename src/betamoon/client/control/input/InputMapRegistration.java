package betamoon.client.control.input;

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
    private boolean active = true;

    InputMapRegistration(InputRouter router, InputMapDefinition definition, String owner) {
        this.router = router;
        this.definition = definition;
        this.owner = owner;
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
        if (!allowConflicts) {
            rejectConflicts(action, copy);
        }
        rebound.put(action, Collections.unmodifiableList(copy));
        router.refresh(this);
    }

    public void resetBinding(String action) {
        requireActive();
        requireAction(action);
        if (rebound.remove(action) != null) {
            router.refresh(this);
        }
    }

    public List<InputBinding> bindings(String action) {
        requireAction(action);
        List<InputBinding> replacement = rebound.get(action);
        return replacement == null ? definition.action(action).bindings() : replacement;
    }

    private void requireAction(String action) {
        if (definition.action(action) == null) {
            throw new IllegalArgumentException("Unknown input action '" + action + "' in " + definition.key());
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
    }

    @Override
    public void close() {
        if (active) {
            router.unregister(this);
        }
    }
}
