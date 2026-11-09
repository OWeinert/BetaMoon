package betamoon.gui.hud;

import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fair admission, finite UI time, detached deduplication and owner/world
 * cleanup without rendering.
 */
final class GuiNotificationQueueTest {
    static void run() {
        try {
            fairAdmission();
            limitsAndAtomicUpdates();
            expiry();
            durationUpdates();
            nativeReservations();
            lifetimes();
        } finally {
            for (String owner : new String[]{"notice-a", "notice-b", "notice-c", "notice-d", "notice-e"}) {
                ScriptResourceTracker.unload(owner);
            }
        }
    }

    private static void fairAdmission() {
        long[] time = {0};
        GuiNotificationQueue queue = new GuiNotificationQueue(() -> time[0]);
        GuiNotificationDefinition a = definition("notice-a");
        GuiNotificationDefinition b = definition("notice-b");
        List<GuiNotification> first = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            first.add(show(queue, a, options("a" + index, GuiNotificationCorner.TOP_RIGHT)));
        }
        GuiNotification other = show(queue, b, options("b", GuiNotificationCorner.TOP_RIGHT));
        queue.advance(null, null, true, GuiNotificationQueueTest::place);
        List<GuiNotification> admitted = queue.admitted();
        require(admitted.size() == 3 && admitted.contains(first.get(0)) && admitted.contains(first.get(1))
                && admitted.contains(other) && first.get(2).bounds().isEmpty(),
                "Notification admission did not rotate owners while preserving each owner's FIFO order");
        require(admitted.get(0) == first.get(0) && admitted.get(1) == other && admitted.get(2) == first.get(1),
                "Paint/layout order discarded the fair admission sequence");
        int otherTop = other.bounds().getTop();
        queue.advance(null, null, true, GuiNotificationQueueTest::place);
        require(other.bounds().getTop() == otherTop,
                "A later logical update reshuffled already admitted owner-fair notification placement");
        require(admitted.stream().allMatch(value -> value.state() == GuiNotification.State.QUEUED),
                "Queue admission advertised visibility before a successful presentation");
        admitted.forEach(GuiNotification::rendered);
        require(other.state() == GuiNotification.State.VISIBLE,
                "Presentation acknowledgement did not expose visibility");
        ScriptResourceTracker.unload("notice-a");
        ScriptResourceTracker.unload("notice-b");
        GuiNotification blocked = show(queue, definition("notice-a"), options(null, GuiNotificationCorner.TOP_RIGHT));
        GuiNotification fitting = show(queue, definition("notice-b"), options(null, GuiNotificationCorner.TOP_RIGHT));
        queue.advance(null, null, true,
                (notification, occupied) -> notification == blocked ? null : place(notification, occupied));
        require(blocked.bounds().isEmpty() && !fitting.bounds().isEmpty(),
                "One unfittable owner's FIFO head blocked a different owner's usable notification");
        ScriptResourceTracker.unload("notice-a");
        ScriptResourceTracker.unload("notice-b");
        require(queue.size() == 0, "Unloaded owners retained admitted or waiting notifications");

        a = definition("notice-a");
        b = definition("notice-b");
        for (GuiNotificationCorner corner : GuiNotificationCorner.values()) {
            for (int index = 0; index < 4; index++) {
                show(queue, a, options("a" + corner.name + index, corner));
                show(queue, b, options("b" + corner.name + index, corner));
            }
        }
        queue.advance(null, null, true, GuiNotificationQueueTest::place);
        require(queue.admitted().size() == 12
                && queue.admitted().stream().filter(value -> value.definition.owner.equals("notice-a")).count() == 6,
                "Global/corner/visible-owner notification shares were not enforced");
        ScriptResourceTracker.unload("notice-a");
        ScriptResourceTracker.unload("notice-b");
    }

    private static void limitsAndAtomicUpdates() {
        GuiNotificationQueue queue = new GuiNotificationQueue(() -> 0);
        GuiNotificationDefinition a = definition("notice-a");
        GuiNotification original = show(queue, a, options("duplicate", GuiNotificationCorner.TOP_RIGHT));
        for (int index = 1; index < 32; index++) {
            show(queue, a, options(null, GuiNotificationCorner.TOP_RIGHT));
        }
        require(queue.show(a, options(null, GuiNotificationCorner.TOP_RIGHT), null, null).reason
                .equals("queue_capacity"), "Per-owner waiting capacity was not explicit");
        GuiNotificationOptions duplicate = new GuiNotificationOptions(Collections.singletonMap("message", "Updated"),
                1000, GuiNotificationCorner.TOP_RIGHT, GuiNotificationOptions.Urgency.HIGH,
                GuiNotificationOptions.Lifetime.CLIENT, "duplicate", false, false, false);
        require(queue.show(a, duplicate, null, null).notification == original && original.options() == duplicate,
                "A full waiting queue rejected atomic same-handle deduplication");
        require(queue.show(definition("notice-a"), duplicate, null, null).reason.equals("definition_changed")
                && original.options() == duplicate,
                "A new template registration silently changed the existing deduplicated template snapshot");
        for (String owner : new String[]{"notice-b", "notice-c", "notice-d"}) {
            GuiNotificationDefinition definition = definition(owner);
            for (int index = 0; index < 32; index++) {
                show(queue, definition, options(null, GuiNotificationCorner.TOP_RIGHT));
            }
        }
        require(queue.size() == 128
                && queue.show(definition("notice-e"), options(null, GuiNotificationCorner.TOP_RIGHT), null, null).reason
                        .equals("queue_capacity"),
                "Global waiting capacity did not isolate explicit refusal from existing entries");
        for (String owner : new String[]{"notice-a", "notice-b", "notice-c", "notice-d"}) {
            ScriptResourceTracker.unload(owner);
        }

        queue = new GuiNotificationQueue(() -> 0);
        a = definition("notice-a");
        original = show(queue, a, options("memory", GuiNotificationCorner.TOP_RIGHT));
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < 16000; index++) {
            text.append('a');
        }
        for (int index = 0; index < 31; index++) {
            show(queue, a, data(text.toString(), null));
        }
        GuiNotificationOptions before = original.options();
        require(queue.updateReason(original, data(text.toString(), "memory")).equals("data_capacity")
                && original.options() == before && queue.size() == 32,
                "Rejected deduplication changed data or exceeded its owner's detached memory budget");
        ScriptResourceTracker.unload("notice-a");

        queue = new GuiNotificationQueue(() -> 0);
        a = definition("notice-a");
        original = show(queue, a, options("moving", GuiNotificationCorner.TOP_RIGHT));
        queue.advance(null, null, true, GuiNotificationQueueTest::place);
        original.rendered();
        for (int index = 0; index < 32; index++) {
            show(queue, a, options(null, GuiNotificationCorner.TOP_RIGHT));
        }
        before = original.options();
        require(queue.updateReason(original, options("moving", GuiNotificationCorner.BOTTOM_RIGHT))
                .equals("queue_capacity") && original.options() == before && !original.bounds().isEmpty(),
                "Corner update bypassed waiting capacity or retired the previous view before admission");
        queue.advance(null, null, true, (notification, occupied) -> null);
        require(original.state() == GuiNotification.State.QUEUED && original.admitted && queue.size() == 33
                && queue.show(a, options(null, GuiNotificationCorner.TOP_RIGHT), null, null).reason
                        .equals("queue_capacity"),
                "Resize overflow lost its reserved slot or bypassed the waiting capacity");
        ScriptResourceTracker.unload("notice-a");
    }

    private static void expiry() {
        long[] time = {1000};
        GuiNotificationQueue queue = new GuiNotificationQueue(() -> time[0]);
        GuiNotificationDefinition definition = definition("notice-a");
        Map<String, Object> data = new HashMap<>();
        data.put("message", "Original");
        GuiNotification entry = show(queue, definition,
                new GuiNotificationOptions(data, 500, GuiNotificationCorner.TOP_RIGHT,
                        GuiNotificationOptions.Urgency.NORMAL, GuiNotificationOptions.Lifetime.CLIENT, null, false,
                        true, true));
        data.put("message", "Changed outside the queue");
        require(entry.options().data.get("message").equals("Original"),
                "Queued notification retained mutable caller data");
        time[0] = 10000;
        queue.advance(null, null, true, GuiNotificationQueueTest::place);
        require(entry.state() == GuiNotification.State.QUEUED, "Unpainted notification expired while preparing");
        entry.rendered();
        int[] ended = {0};
        entry.onEnd(() -> ended[0]++);
        entry.pause(true, false);
        time[0] += 100;
        queue.advance(null, null, true, GuiNotificationQueueTest::place);
        entry.pause(false, true);
        time[0] += 100;
        queue.advance(null, null, true, GuiNotificationQueueTest::place);
        require(entry.elapsed == 0, "Hover/focus pause charged visible lifetime");
        entry.pause(false, false);
        time[0] += 200;
        queue.advance(null, null, false, GuiNotificationQueueTest::place);
        require(entry.elapsed == 0, "Protected/unavailable presentation consumed a notification's visible lifetime");
        time[0] += 500;
        queue.advance(null, null, true, GuiNotificationQueueTest::place);
        require(entry.state() == GuiNotification.State.QUEUED && entry.elapsed == 0 && ended[0] == 1,
                "Resuming hidden notification charged time before presentation was restored");
        entry.rendered();
        entry.onEnd(() -> ended[0]++);
        time[0] += 500;
        queue.advance(null, null, true, GuiNotificationQueueTest::place);
        require(entry.state() == GuiNotification.State.EXITING && ended[0] == 2,
                "Expiry did not revoke input/actions before its exit transition");
        time[0] += 60;
        require(Math.abs(entry.opacity(time[0]) - 0.5) < 0.001,
                "Notification exit was not Java sampled on the UI clock");
        time[0] += 60;
        queue.advance(null, null, true, GuiNotificationQueueTest::place);
        require(entry.state() == GuiNotification.State.EXPIRED && queue.size() == 0 && !entry.activation.requested()
                && ended[0] == 2, "Expired notification retained queue capacity or an activation");
        ScriptResourceTracker.unload("notice-a");
    }

    private static void lifetimes() {
        GuiNotificationQueue queue = new GuiNotificationQueue(() -> 0);
        GuiNotificationDefinition definition = definition("notice-a");
        Object world = new Object();
        Object player = new Object();
        GuiNotificationOptions local = new GuiNotificationOptions(Collections.emptyMap(), 1000,
                GuiNotificationCorner.TOP_RIGHT, GuiNotificationOptions.Urgency.NORMAL,
                GuiNotificationOptions.Lifetime.WORLD, "world", true, false, false);
        require(queue.show(definition, local, null, null).reason.equals("world_unavailable"),
                "World-bound notification acquired a missing environment");
        GuiNotification bound = queue.show(definition, local, world, player).notification;
        GuiNotification client = show(queue, definition, options(null, GuiNotificationCorner.TOP_RIGHT));
        Object replacement = new Object();
        GuiNotification renewed = queue.show(definition, local, replacement, player).notification;
        require(renewed != bound && bound.state() == GuiNotification.State.CANCELLED,
                "Deduplication reused a notification from an expired world before the next logical tick");
        queue.advance(null, null, false, GuiNotificationQueueTest::place);
        require(renewed.state() == GuiNotification.State.CANCELLED && client.live(),
                "World change retained a world-bound entry or cancelled detached client data");
        try (LuaCallbackScope scope = LuaCallbackScope.binding()) {
            try {
                client.dismiss();
                throw new AssertionError("Read-only binding dismissed a notification");
            } catch (RuntimeException expected) {
                // UI state mutation remains phase controlled.
            }
        }
        ScriptResourceTracker.unload("notice-a");
        require(client.state() == GuiNotification.State.CANCELLED && queue.size() == 0,
                "Owner unload retained detached client notifications");
    }

    private static void durationUpdates() {
        long[] time = {0};
        GuiNotificationQueue queue = new GuiNotificationQueue(() -> time[0]);
        GuiNotification entry = show(queue, definition("notice-a"),
                options("duration", GuiNotificationCorner.TOP_RIGHT));
        queue.advance(null, null, true, GuiNotificationQueueTest::place);
        entry.rendered();
        time[0] = 400;
        queue.advance(null, null, true, GuiNotificationQueueTest::place);
        GuiNotificationOptions preserving = new GuiNotificationOptions(Collections.singletonMap("message", "New"), 1000,
                GuiNotificationCorner.TOP_RIGHT, GuiNotificationOptions.Urgency.NORMAL,
                GuiNotificationOptions.Lifetime.CLIENT, "duration", false, false, false);
        require(entry.update(preserving) && entry.elapsed == 400, "Non-restarting update reset visible lifetime");
        time[0] = 700;
        queue.advance(null, null, true, GuiNotificationQueueTest::place);
        require(entry.update(options("duration", GuiNotificationCorner.TOP_RIGHT)) && entry.elapsed == 0,
                "Explicit restarting update retained the previous expiry deadline");
        time[0] = 1699;
        queue.advance(null, null, true, GuiNotificationQueueTest::place);
        require(entry.state() == GuiNotification.State.VISIBLE,
                "Restarted notification expired before its new duration");
        time[0]++;
        queue.advance(null, null, true, GuiNotificationQueueTest::place);
        require(entry.state() == GuiNotification.State.EXITING,
                "Restarted notification missed its new expiry deadline");
        ScriptResourceTracker.unload("notice-a");
    }

    private static void nativeReservations() {
        GuiNotificationQueue queue = new GuiNotificationQueue(() -> 0);
        GuiNotificationDefinition definition = definition("notice-a");
        GuiNotification one = show(queue, definition, options(null, GuiNotificationCorner.TOP_LEFT));
        GuiNotification two = show(queue, definition, options(null, GuiNotificationCorner.TOP_LEFT));
        List<Rect> reserved = new ArrayList<>();
        reserved.add(Rect.fromPositionAndSize(156, 0, 164, 40));
        reserved.add(Rect.fromPositionAndSize(0, 94, 320, 98));
        reserved.add(Rect.fromPositionAndSize(64, 192, 192, 48));
        queue.advance(null, null, true, new GuiNotificationLayout(320, 240, reserved));
        require(queue.admitted().size() == 2 && one.bounds().getTop() == 4 && two.bounds().getTop() == 48,
                "Minimum viewport notification columns unnecessarily overlapped the achievement reservation");
        for (GuiNotification entry : queue.admitted()) {
            for (Rect nativeRegion : reserved) {
                require(entry.bounds().intersect(nativeRegion).isEmpty(),
                        "Notification placement covered native chat/hotbar/achievement presentation");
            }
        }
        reserved.add(Rect.fromPositionAndSize(0, 0, 320, 90));
        queue.advance(null, null, true, new GuiNotificationLayout(320, 240, reserved));
        require(queue.admitted().isEmpty() && one.state() == GuiNotification.State.QUEUED,
                "Insufficient viewport space covered engine/native diagnostics instead of preserving queued entries");
        ScriptResourceTracker.unload("notice-a");
    }

    private static GuiNotificationDefinition definition(String owner) {
        return new GuiNotificationDefinition(owner, "test:" + owner, "test:content", 160, 40,
                GuiNotificationCorner.TOP_RIGHT, false, Collections.emptySet());
    }

    private static GuiNotificationOptions options(String dedup, GuiNotificationCorner corner) {
        return new GuiNotificationOptions(Collections.emptyMap(), 1000, corner, GuiNotificationOptions.Urgency.NORMAL,
                GuiNotificationOptions.Lifetime.CLIENT, dedup, true, false, false);
    }

    private static GuiNotificationOptions data(String text, String dedup) {
        return new GuiNotificationOptions(Collections.singletonMap("message", text), 1000,
                GuiNotificationCorner.TOP_RIGHT, GuiNotificationOptions.Urgency.NORMAL,
                GuiNotificationOptions.Lifetime.CLIENT, dedup, true, false, false);
    }

    private static GuiNotification show(GuiNotificationQueue queue, GuiNotificationDefinition definition,
            GuiNotificationOptions options) {
        GuiNotificationQueue.ShowResult result = queue.show(definition, options, null, null);
        require(result.notification != null, "Unexpected notification admission refusal: " + result.reason);
        return result.notification;
    }

    private static Rect place(GuiNotification notification, List<Rect> occupied) {
        int x = notification.options().corner.ordinal() * 200;
        int y = 0;
        for (Rect rect : occupied) {
            if (rect.getLeft() == x) {
                y = Math.max(y, rect.getBottom() + 4);
            }
        }
        return Rect.fromPositionAndSize(x, y, 160, 40);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
