package betamoon.gui.hud;

import betamoon.gui.component.GuiComponentLimits;
import betamoon.gui.presentation.GuiPublishedData;
import java.util.Map;
import java.util.Objects;

/**
 * Fully validated detached candidate; queue mutation happens only after
 * admission.
 */
public final class GuiNotificationOptions {
    public enum Lifetime {
        CLIENT, WORLD
    }
    public enum Urgency {
        LOW, NORMAL, HIGH
    }

    public final Map<String, Object> data;
    public final long durationMillis;
    public final GuiNotificationCorner corner;
    public final Urgency urgency;
    public final Lifetime lifetime;
    public final String dedupKey;
    public final boolean restartDuration;
    public final boolean pauseOnHover;
    public final boolean pauseOnFocus;
    public final long metadataBytes;

    public GuiNotificationOptions(Map<String, Object> data, long durationMillis, GuiNotificationCorner corner,
            Urgency urgency, Lifetime lifetime, String dedupKey, boolean restartDuration, boolean pauseOnHover,
            boolean pauseOnFocus) {
        if (durationMillis < 500 || durationMillis > 60000
                || dedupKey != null && (dedupKey.trim().isEmpty() || dedupKey.length() > 160)) {
            throw new IllegalArgumentException("Notifications require 500..60000 ms and a bounded optional dedup key");
        }
        GuiPublishedData detached = new GuiPublishedData();
        detached.publish(data);
        GuiComponentLimits.Values budget = new GuiComponentLimits.Values(16384, 1024);
        budget.add(detached.snapshot());
        this.data = detached.snapshot();
        this.durationMillis = durationMillis;
        this.corner = Objects.requireNonNull(corner, "Notification corner");
        this.urgency = Objects.requireNonNull(urgency, "Notification urgency");
        this.lifetime = Objects.requireNonNull(lifetime, "Notification lifetime");
        this.dedupKey = dedupKey;
        this.restartDuration = restartDuration;
        this.pauseOnHover = pauseOnHover;
        this.pauseOnFocus = pauseOnFocus;
        metadataBytes = budget.estimatedBytes() + 512;
    }
}
