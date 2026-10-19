package betamoon.gui.presentation;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** Successful normalized source writes only; closing a subscription releases its screen. */
public final class GuiValueObserver {
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    public AutoCloseable subscribe(Listener listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void changed(String key, Object previous, Object value) {
        if (!Objects.equals(previous, value)) {
            for (Listener listener : listeners) {
                listener.changed(key, previous, value);
            }
        }
    }

    @FunctionalInterface
    public interface Listener {
        void changed(String key, Object previous, Object value);
    }
}
