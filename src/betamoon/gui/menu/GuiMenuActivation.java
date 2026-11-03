package betamoon.gui.menu;

import betamoon.gui.framework.GuiInputLifetime;
import betamoon.gui.screen.GuiScreenActivation;

/** Explicit owned claim; registration alone never changes a native menu. */
public final class GuiMenuActivation implements AutoCloseable {
    public final String owner;
    public final String key;
    public final GuiInputLifetime input = new GuiInputLifetime();
    private final Runnable cleanup;
    private boolean closed;
    private String status = "queued";
    private String reason;
    private GuiScreenActivation instance;

    GuiMenuActivation(String owner, String key, Runnable cleanup) {
        this.owner = owner;
        this.key = key;
        this.cleanup = cleanup;
        input.activate();
    }

    public boolean requested() {
        return !closed;
    }

    public String status() {
        return closed ? "closed" : status;
    }

    public String reason() {
        return reason;
    }

    public GuiScreenActivation instance() {
        return instance != null && instance.active() ? instance : null;
    }

    public void publish(String status, String reason, GuiScreenActivation instance) {
        if (!closed) {
            this.status = status;
            this.reason = reason;
            this.instance = instance;
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            input.close();
            instance = null;
            cleanup.run();
        }
    }
}
