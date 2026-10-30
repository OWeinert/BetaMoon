package betamoon.gui.screen;

import betamoon.gui.presentation.GuiPublishedData;
import java.util.Collections;

/**
 * One completion, retained as detached data after the originating screen
 * closes.
 */
public final class GuiActionTicket {
    public enum Status {
        QUEUED, RUNNING, SUCCEEDED, REFUSED, FAILED, CANCELLED
    }

    public final long identity;
    private Status status = Status.QUEUED;
    private Object result;
    private String reason;

    GuiActionTicket(long identity) {
        this.identity = identity;
    }

    public Status status() {
        return status;
    }

    public Object result() {
        return result;
    }

    public String reason() {
        return reason;
    }

    public boolean terminal() {
        return status != Status.QUEUED && status != Status.RUNNING;
    }

    void running() {
        if (status != Status.QUEUED) {
            throw new IllegalStateException("UI action ticket already dispatched");
        }
        status = Status.RUNNING;
    }

    void finish(Status outcome, Object value, String explanation) {
        if (terminal()) {
            return;
        }
        if (outcome == Status.QUEUED || outcome == Status.RUNNING) {
            throw new IllegalArgumentException("UI action completion must be terminal");
        }
        GuiPublishedData detached = new GuiPublishedData();
        detached.publish(Collections.singletonMap("result", value));
        result = detached.snapshot().get("result");
        reason = explanation == null ? null : explanation.substring(0, Math.min(1024, explanation.length()));
        status = outcome;
    }
}
