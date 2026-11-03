package betamoon.gui.screen;

import betamoon.gui.framework.GuiInputLifetime;

/**
 * Explicit owned native-host presentation claim; declarations alone acquire no
 * input.
 */
public class GuiPresentationActivation implements AutoCloseable {
    public final String owner;
    public final String key;
    public final GuiInputLifetime input = new GuiInputLifetime();
    private final Runnable cleanup;
    private boolean closed;
    private String status = "queued";
    private String reason;
    private GuiScreenActivation instance;

    protected GuiPresentationActivation(String owner, String key, Runnable cleanup) {
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
