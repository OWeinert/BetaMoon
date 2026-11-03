package betamoon.gui.framework;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Host lifecycle eligibility is Java data, including while a displayed frame is
 * retained.
 */
public final class GuiInputLifetime implements AutoCloseable {
    private boolean active;
    private boolean closed;
    private final BooleanSupplier owner;

    public GuiInputLifetime() {
        this(() -> true);
    }

    /**
     * Java-only host eligibility; no Lua supplier is retained or sampled by this
     * gate.
     */
    public GuiInputLifetime(BooleanSupplier owner) {
        this.owner = Objects.requireNonNull(owner, "input lifetime owner");
    }

    public boolean active() {
        return active && !closed && owner.getAsBoolean();
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
