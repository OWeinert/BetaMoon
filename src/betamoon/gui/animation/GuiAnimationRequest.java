package betamoon.gui.animation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Aggregate completion is independent of the lifetimes of the individual property tracks. */
public final class GuiAnimationRequest {
    public final int id;
    public final Set<String> properties;
    private final Set<String> remaining;
    private final Listener listener;
    private String status = "pending";
    private boolean reported;

    public GuiAnimationRequest(int id, Set<String> properties, Listener listener) {
        this.id = id;
        this.properties = Collections.unmodifiableSet(new LinkedHashSet<>(properties));
        this.remaining = new LinkedHashSet<>(properties);
        this.listener = listener;
    }

    public String status() {
        return status;
    }

    public void commit() {
        if ("pending".equals(status)) {
            status = "running";
        }
        reportIfComplete();
    }

    public void property(String property, boolean completed, String reason) {
        if (!remaining.remove(property)) {
            return;
        }
        listener.property(this, property, completed, reason);
        if (!completed && !reported) {
            status = "interrupted";
            report(false, reason);
        } else {
            reportIfComplete();
        }
    }

    public void cancel(String reason) {
        if (!reported) {
            status = "canceled";
            report(false, reason);
        }
    }

    private void reportIfComplete() {
        if (remaining.isEmpty() && !"pending".equals(status) && !reported) {
            status = "completed";
            report(true, null);
        }
    }

    private void report(boolean complete, String reason) {
        reported = true;
        listener.result(this, complete, reason);
    }

    public interface Listener {
        void property(GuiAnimationRequest request, String property, boolean completed, String reason);
        void result(GuiAnimationRequest request, boolean completed, String reason);
    }
}
