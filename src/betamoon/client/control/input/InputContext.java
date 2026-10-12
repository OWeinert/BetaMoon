package betamoon.client.control.input;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Owner-scoped layer participating in named action and native-family routing.
 */
public final class InputContext implements AutoCloseable {
    private final InputRouter router;
    private final InputMapRegistration registration;
    private final String owner;
    private final long token;
    private final long sequence;
    private final int priority;
    private final EnumSet<InputFamily> captures;
    private final Map<String, InputActionListener> listeners = new HashMap<String, InputActionListener>();
    private boolean active = true;

    InputContext(InputRouter router, InputMapRegistration registration, String owner, long token, long sequence,
            int priority, EnumSet<InputFamily> captures) {
        this.router = router;
        this.registration = registration;
        this.owner = owner;
        this.token = token;
        this.sequence = sequence;
        this.priority = priority;
        this.captures = captures.clone();
    }

    public void on(String action, InputActionListener listener) {
        requireActive();
        if (registration.definition().action(action) == null) {
            throw new IllegalArgumentException(
                    "Unknown input action '" + action + "' in " + registration.definition().key());
        }
        if (listener == null) {
            listeners.remove(action);
        } else {
            listeners.put(action, listener);
        }
    }

    InputActionListener listener(String action) {
        return listeners.get(action);
    }

    boolean captures(InputFamily family) {
        return active && captures.contains(family);
    }

    void disableAfterFailure() {
        active = false;
        listeners.clear();
    }

    private void requireActive() {
        if (!active) {
            throw new IllegalStateException("Input context is no longer active");
        }
    }

    public boolean isActive() {
        return active;
    }

    public String owner() {
        return owner;
    }

    public long token() {
        return token;
    }

    long sequence() {
        return sequence;
    }

    public int priority() {
        return priority;
    }

    InputMapRegistration registration() {
        return registration;
    }

    @Override
    public void close() {
        if (!active) {
            return;
        }
        active = false;
        listeners.clear();
        router.removeContext(this);
    }
}
