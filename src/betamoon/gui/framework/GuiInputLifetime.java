package betamoon.gui.framework;

/**
 * Host lifecycle eligibility is Java data, including while a displayed frame is
 * retained.
 */
public final class GuiInputLifetime implements AutoCloseable {
    private boolean active;
    private boolean closed;

    public boolean active() {
        return active && !closed;
    }

    public void activate() {
        if (closed) {
            throw new IllegalStateException("UI input lifetime is closed");
        }
        active = true;
    }

    public void close() {
        active = false;
        closed = true;
    }
}
