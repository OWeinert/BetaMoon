package betamoon.gui.hud;

import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Bounded owner-fair notification admission and monotonic UI expiry,
 * independent of painting.
 */
public final class GuiNotificationQueue {
    public interface Placement {
        Rect place(GuiNotification notification, List<Rect> occupied);
    }

    public static final class ShowResult {
        public final GuiNotification notification;
        public final String reason;

        ShowResult(GuiNotification notification, String reason) {
            this.notification = notification;
            this.reason = reason;
        }
    }

    private final LongSupplier clock;
    private final Map<Long, GuiNotification> entries = new LinkedHashMap<>();
    private final Map<GuiNotificationCorner, String> lastOwner = new EnumMap<>(GuiNotificationCorner.class);
    private long nextId;

    public GuiNotificationQueue(LongSupplier clock) {
        this.clock = clock;
    }

    long now() {
        return clock.getAsLong();
    }

    public ShowResult show(GuiNotificationDefinition definition, GuiNotificationOptions options, Object world,
            Object player) {
        LuaCallbackScope.requireUiStateWrite();
        if (!definition.available()) {
            return refused("owner_expired");
        }
        if (options.lifetime == GuiNotificationOptions.Lifetime.WORLD && (world == null || player == null)) {
            return refused("world_unavailable");
        }
        GuiNotification duplicate = duplicate(definition, options);
        if (duplicate != null && duplicate.options.lifetime == GuiNotificationOptions.Lifetime.WORLD
                && (duplicate.world.get() != world || duplicate.player.get() != player)) {
            cancel(duplicate, "world_replaced");
            duplicate = null;
        }
        if (duplicate != null) {
            if (duplicate.definition != definition) {
                return refused("definition_changed");
            }
            String reason = updateReason(duplicate, options);
            return reason == null ? new ShowResult(duplicate, null) : refused(reason);
        }
        if (!waitingCapacity(definition.owner)) {
            return refused("queue_capacity");
        }
        if (!dataFits(definition.owner, options, null)) {
            return refused("data_capacity");
        }
        GuiNotification entry = new GuiNotification(this, ++nextId, definition, options, world, player, now());
        ScriptExecution.withOwner(definition.owner, () -> {
            ScriptResourceTracker.track(entry.cleanup);
            return null;
        });
        entry.tracked = true;
        entries.put(entry.id, entry);
        return new ShowResult(entry, null);
    }

    private GuiNotification duplicate(GuiNotificationDefinition definition, GuiNotificationOptions options) {
        if (options.dedupKey == null) {
            return null;
        }
        for (GuiNotification entry : entries.values()) {
            if (entry.live() && entry.definition.owner.equals(definition.owner)
                    && entry.definition.key.equals(definition.key) && options.dedupKey.equals(entry.options.dedupKey)) {
                return entry;
            }
        }
        return null;
    }

    public boolean update(GuiNotification entry, GuiNotificationOptions candidate) {
        return updateReason(entry, candidate) == null;
    }

    public String updateReason(GuiNotification entry, GuiNotificationOptions candidate) {
        LuaCallbackScope.requireUiStateWrite();
        if (entries.get(entry.id) != entry || !entry.live()) {
            return "inactive";
        }
        if (!entry.definition.available()) {
            return "owner_expired";
        }
        if (candidate.lifetime != entry.options.lifetime || !same(candidate.dedupKey, entry.options.dedupKey)) {
            return "identity_changed";
        }
        if (!dataFits(entry.definition.owner, candidate, entry)) {
            return "data_capacity";
        }
        boolean moved = candidate.corner != entry.options.corner;
        if (moved && entry.admitted && !waitingCapacity(entry.definition.owner)) {
            return "queue_capacity";
        }
        entry.options = candidate;
        if (candidate.restartDuration) {
            entry.elapsed = 0;
            entry.advancedAt = now();
        }
        if (moved) {
            requeue(entry, true);
        }
        return null;
    }

    private boolean waitingCapacity(String owner) {
        int pending = 0;
        int owned = 0;
        for (GuiNotification entry : entries.values()) {
            if (!entry.admitted && entry.live()) {
                pending++;
                if (entry.definition.owner.equals(owner)) {
                    owned++;
                }
            }
        }
        return pending < 128 && owned < 32;
    }

    private static boolean same(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean dataFits(String owner, GuiNotificationOptions options, GuiNotification replacing) {
        long total = options.metadataBytes;
        long owned = total;
        for (GuiNotification entry : entries.values()) {
            if (entry != replacing) {
                total += entry.options.metadataBytes;
                if (entry.definition.owner.equals(owner)) {
                    owned += entry.options.metadataBytes;
                }
            }
        }
        return total <= 4 * 1024 * 1024 && owned <= 512 * 1024;
    }

    /**
     * Existing displayed slots are fitted first; new entries never displace another
     * live view.
     */
    public void advance(Object world, Object player, boolean presentationAvailable, Placement placement) {
        long now = now();
        for (GuiNotification entry : new ArrayList<>(entries.values())) {
            if (!entry.definition.available()) {
                cancel(entry, "owner_unloaded");
                continue;
            }
            if (entry.options.lifetime == GuiNotificationOptions.Lifetime.WORLD && (entry.world.get() != world
                    || entry.player.get() != player || world == null || player == null)) {
                cancel(entry, "world_replaced");
                continue;
            }
            long delta = Math.max(0, now - entry.advancedAt);
            entry.advancedAt = now;
            if (entry.ending != null) {
                if (now - entry.exitAt >= 120) {
                    finish(entry, entry.ending, entry.reason);
                }
            } else if (entry.displayed && presentationAvailable
                    && !(entry.options.pauseOnHover && entry.hovered || entry.options.pauseOnFocus && entry.focused)) {
                entry.elapsed += Math.min(delta, Long.MAX_VALUE - entry.elapsed);
                if (entry.elapsed >= entry.options.durationMillis) {
                    end(entry, GuiNotification.State.EXPIRED, "expired");
                }
            }
        }
        if (!presentationAvailable) {
            for (GuiNotification entry : new ArrayList<>(entries.values())) {
                if (entry.live() && entry.displayed) {
                    requeue(entry, false);
                }
            }
            return;
        }
        List<Rect> occupied = new ArrayList<>();
        for (GuiNotification entry : new ArrayList<>(entries.values())) {
            if (entry.admitted) {
                Rect placed = placement.place(entry, Collections.unmodifiableList(occupied));
                if (!fits(placed, occupied)) {
                    if (entry.ending != null) {
                        finish(entry, entry.ending, entry.reason);
                    } else {
                        requeue(entry, waitingCapacity(entry.definition.owner));
                    }
                } else {
                    entry.bounds = placed;
                    occupied.add(placed);
                }
            }
        }
        for (GuiNotificationCorner corner : GuiNotificationCorner.values()) {
            Set<String> blocked = new HashSet<>();
            while (visibleCount(corner, null) < 3 && visibleCount(null, null) < 12) {
                GuiNotification candidate = next(corner, blocked);
                if (candidate == null) {
                    break;
                }
                Rect placed = placement.place(candidate, Collections.unmodifiableList(occupied));
                if (!fits(placed, occupied)) {
                    blocked.add(candidate.definition.owner);
                    continue;
                }
                candidate.bounds = placed;
                candidate.admitted = true;
                candidate.advancedAt = now;
                occupied.add(placed);
                lastOwner.put(corner, candidate.definition.owner);
            }
        }
    }

    private static boolean fits(Rect candidate, List<Rect> occupied) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        for (Rect existing : occupied) {
            if (!existing.intersect(candidate).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private GuiNotification next(GuiNotificationCorner corner, Set<String> blocked) {
        GuiNotification fallback = null;
        String previous = lastOwner.get(corner);
        for (GuiNotification entry : entries.values()) {
            if (entry.live() && !entry.admitted && entry.options.corner == corner
                    && !blocked.contains(entry.definition.owner) && visibleCount(null, entry.definition.owner) < 6) {
                if (fallback == null) {
                    fallback = entry;
                }
                if (!entry.definition.owner.equals(previous)) {
                    return entry;
                }
            }
        }
        return fallback;
    }

    private int visibleCount(GuiNotificationCorner corner, String owner) {
        int count = 0;
        for (GuiNotification entry : entries.values()) {
            if (entry.admitted && (corner == null || entry.options.corner == corner)
                    && (owner == null || entry.definition.owner.equals(owner))) {
                count++;
            }
        }
        return count;
    }

    private void requeue(GuiNotification entry, boolean releaseSlot) {
        entry.bounds = Rect.EMPTY;
        if (releaseSlot) {
            entry.admitted = false;
        }
        entry.displayed = false;
        entry.state = GuiNotification.State.QUEUED;
        entry.hovered = false;
        entry.focused = false;
        revoke(entry);
    }

    public void end(GuiNotification entry, GuiNotification.State state, String reason) {
        if (entries.get(entry.id) != entry || !entry.live()) {
            return;
        }
        if (!entry.displayed || entry.bounds.isEmpty()) {
            finish(entry, state, reason);
            return;
        }
        entry.ending = state;
        entry.state = GuiNotification.State.EXITING;
        entry.reason = reason;
        entry.exitAt = now();
        revoke(entry);
    }

    private void revoke(GuiNotification entry) {
        GuiHudRuntime.stopInteraction(entry.activation);
        Runnable cleanup = entry.onEnd;
        entry.onEnd = null;
        if (cleanup != null) {
            cleanup.run();
        }
    }

    public void cancel(GuiNotification entry, String reason) {
        if (entries.get(entry.id) == entry) {
            finish(entry, GuiNotification.State.CANCELLED, reason);
        }
    }

    public void failed(GuiNotification entry, String reason) {
        if (entries.get(entry.id) == entry) {
            finish(entry, GuiNotification.State.FAILED, reason);
        }
    }

    private void finish(GuiNotification entry, GuiNotification.State state, String reason) {
        entries.remove(entry.id);
        entry.state = state;
        entry.reason = reason;
        entry.bounds = Rect.EMPTY;
        entry.admitted = false;
        try {
            revoke(entry);
        } finally {
            entry.onEnd = null;
            try {
                entry.activation.close();
            } finally {
                if (entry.tracked) {
                    ScriptResourceTracker.untrack(entry.definition.owner, entry.cleanup);
                    entry.tracked = false;
                }
            }
        }
    }

    public List<GuiNotification> admitted() {
        List<GuiNotification> visible = new ArrayList<>();
        for (GuiNotification entry : entries.values()) {
            if (!entry.bounds.isEmpty()) {
                visible.add(entry);
            }
        }
        return Collections.unmodifiableList(visible);
    }

    public int size() {
        return entries.size();
    }

    private static ShowResult refused(String reason) {
        return new ShowResult(null, reason);
    }
}
