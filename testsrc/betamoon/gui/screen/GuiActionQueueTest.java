package betamoon.gui.screen;

import betamoon.gui.component.GuiComponentSchema;
import betamoon.gui.action.GuiActionOrigin;
import betamoon.gui.screen.GuiActionDefinition.Scope;
import betamoon.gui.screen.GuiActionTicket.Status;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

/**
 * Exercises acceptance, fairness and completion through actual queue dispatch.
 */
public final class GuiActionQueueTest {
    private static final GuiActionDefinition CLIENT = action(Scope.CLIENT);
    private static final GuiActionDefinition WORLD = action(Scope.WORLD);

    public static void main(String[] arguments) {
        fairDispatch();
        lifetimeAndFailures();
        capacityAndReadOnly();
        originOutcomes();
        originCapacityAndFollowups();
        System.out.println("UI actions passed: bounded owner fairness, logical boundaries, detached results, "
                + "reentrancy, refusal, stale revisions, cancellation and exactly-once completion.");
    }

    private static void fairDispatch() {
        GuiActionQueue queue = new GuiActionQueue(() -> 0);
        List<String> order = new ArrayList<>();
        Activation first = new Activation("first", order);
        Activation second = new Activation("second", order);
        for (int index = 0; index < 6; index++) {
            queue.submit(first, CLIENT, Collections.emptyMap());
        }
        GuiActionTicket other = queue.submit(second, CLIENT, Collections.emptyMap()).ticket;
        queue.dispatch(Scope.WORLD);
        require(order.isEmpty(), "Client action ran at a world boundary");
        queue.dispatch(Scope.CLIENT);
        require(order.size() == 7 && order.get(1).equals("second") && other.status() == Status.SUCCEEDED,
                "One owner monopolized the dispatch slice");
        require(((Map<?, ?>) other.result()).get("owner").equals("second"), "Result was not detached");
        first.nested = () -> queue.submit(first, CLIENT, Collections.emptyMap());
        queue.submit(first, CLIENT, Collections.emptyMap());
        queue.dispatch(Scope.CLIENT);
        require(queue.pending() == 1 && order.size() == 8,
                "A callback ran its newly submitted action in the same boundary");
        first.nested = null;
        queue.dispatch(Scope.CLIENT);
        require(queue.pending() == 0 && order.size() == 9, "Deferred request did not run at the next boundary");
        queue.dispatch(Scope.CLIENT);
        require(order.size() == 9, "A terminal request ran twice");
    }

    private static void lifetimeAndFailures() {
        GuiActionQueue queue = new GuiActionQueue(() -> 0);
        Activation activation = new Activation("lifetime", new ArrayList<>());
        GuiActionTicket stale = queue.submit(activation, CLIENT, Collections.emptyMap()).ticket;
        activation.revision++;
        queue.dispatch(Scope.CLIENT);
        require(stale.status() == Status.CANCELLED, "Stale screen action reached its successor");
        GuiActionTicket paused = queue.submit(activation, WORLD, Collections.emptyMap()).ticket;
        activation.paused = true;
        queue.dispatch(Scope.WORLD);
        require(paused.status() == Status.REFUSED && paused.reason().equals("world_paused"),
                "Paused action did not revalidate");
        require(queue.submit(activation, WORLD, Collections.emptyMap()).reason.equals("world_paused"),
                "Paused request was accepted");
        activation.paused = false;
        activation.fail = true;
        GuiActionTicket failure = queue.submit(activation, CLIENT, Collections.emptyMap()).ticket;
        queue.dispatch(Scope.CLIENT);
        require(failure.status() == Status.FAILED && activation.failures == 1,
                "Action failure did not terminate/report once");
        activation.fail = false;
        GuiActionTicket cancelled = queue.submit(activation, CLIENT, Collections.emptyMap()).ticket;
        queue.cancel(activation, "screen_closed");
        queue.dispatch(Scope.CLIENT);
        require(cancelled.status() == Status.CANCELLED && failure.status() == Status.FAILED && queue.pending() == 0,
                "Cancellation changed terminal results or left queued requests");
        require(queue.submit(activation, action(Scope.PLAYER_DATA), Collections.emptyMap()).reason
                .equals("player_data_unavailable"), "Player data scope silently gained another scope");
    }

    private static void capacityAndReadOnly() {
        GuiActionQueue queue = new GuiActionQueue(() -> 0);
        Activation activation = new Activation("capacity", new ArrayList<>());
        for (int index = 0; index < 16; index++) {
            require(queue.submit(activation, CLIENT, Collections.emptyMap()).ticket != null, "Early capacity refusal");
        }
        require(queue.submit(activation, CLIENT, Collections.emptyMap()).reason.equals("action_queue_full"),
                "Activation exceeded its share");
        try (LuaCallbackScope binding = LuaCallbackScope.binding()) {
            try {
                queue.submit(activation, CLIENT, Collections.emptyMap());
                throw new AssertionError("Binding submitted an action");
            } catch (LuaError expected) {
            }
        }
        queue.dispatch(Scope.CLIENT);
        require(queue.pending() == 8, "Logical boundary exceeded eight callbacks");
        queue.cancel(activation, "owner_unload");
        require(queue.pending() == 0, "Owner cleanup retained capacity");
        long[] time = {0};
        GuiActionQueue timed = new GuiActionQueue(() -> time[0]);
        activation.nested = () -> time[0] += 3_000_000;
        timed.submit(activation, CLIENT, Collections.emptyMap());
        timed.submit(activation, CLIENT, Collections.emptyMap());
        timed.dispatch(Scope.CLIENT);
        require(timed.pending() == 1, "Callback cost did not end the dispatch slice");
        timed.cancel(activation, "done");
    }

    private static GuiActionDefinition action(Scope scope) {
        return new GuiActionDefinition("apply", scope, GuiComponentSchema.EMPTY, new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return NIL;
            }
        });
    }

    private static void originOutcomes() {
        GuiActionQueue queue = new GuiActionQueue(() -> 0);
        Activation activation = new Activation("origins", new ArrayList<>());
        GuiActionOrigin record = GuiActionOrigin.pending();
        try (GuiActionOrigin.Frame source = GuiActionOrigin.enter(Collections.singletonList(record))) {
            require(queue.submit(activation, CLIENT, Collections.emptyMap()).reason.equals("origin_expired"),
                    "An unaccepted record submitted authoritative intent");
        }
        record.activate();
        GuiActionTicket accepted;
        try (GuiActionOrigin.Frame source = GuiActionOrigin.enter(Collections.singletonList(record));
                GuiActionOrigin.Frame event = GuiActionOrigin.event()) {
            accepted = queue.submit(activation, CLIENT, Collections.emptyMap()).ticket;
            queue.dispatch(Scope.CLIENT);
            require(accepted.status() == Status.QUEUED && activation.order.isEmpty(),
                    "Deferred intent executed before its UI event finished validation");
            event.commit();
        }
        queue.dispatch(Scope.CLIENT);
        require(accepted.status() == Status.SUCCEEDED, "Accepted event did not release its deferred intent");
        GuiActionTicket rejected;
        try (GuiActionOrigin.Frame event = GuiActionOrigin.event()) {
            rejected = queue.submit(activation, WORLD, Collections.emptyMap()).ticket;
            GuiActionOrigin.rejectEvent();
            event.commit();
        }
        queue.dispatch(Scope.CLIENT);
        require(rejected.status() == Status.CANCELLED && rejected.reason().equals("origin_expired"),
                "An explicitly refused UI event revived its intent or waited for a world tick to cancel");
        GuiActionTicket nested;
        GuiActionTicket recovered;
        try (GuiActionOrigin.Frame outer = GuiActionOrigin.event()) {
            try (GuiActionOrigin.Frame inner = GuiActionOrigin.event()) {
                nested = queue.submit(activation, CLIENT, Collections.emptyMap()).ticket;
            }
            recovered = queue.submit(activation, CLIENT, Collections.emptyMap()).ticket;
            outer.commit();
        }
        queue.dispatch(Scope.CLIENT);
        require(nested.status() == Status.CANCELLED && recovered.status() == Status.SUCCEEDED,
                "A caught nested callback failure retained its intent or cancelled the recovered caller's intent");
        GuiActionTicket expired;
        try (GuiActionOrigin.Frame source = GuiActionOrigin.enter(Collections.singletonList(record))) {
            expired = queue.submit(activation, CLIENT, Collections.emptyMap()).ticket;
        }
        record.invalidate();
        queue.dispatch(Scope.CLIENT);
        require(expired.status() == Status.CANCELLED, "Expired record lifetime reached a later callback");
        require(GuiActionOrigin.capture().isEmpty(), "UI origin scopes leaked after callback completion");
    }

    private static void originCapacityAndFollowups() {
        GuiActionQueue queue = new GuiActionQueue(() -> 0);
        Activation activation = new Activation("origin_capacity", new ArrayList<>());
        for (int index = 0; index < 16; index++) {
            GuiActionOrigin record = GuiActionOrigin.pending();
            record.activate();
            try (GuiActionOrigin.Frame source = GuiActionOrigin.enter(Collections.singletonList(record))) {
                require(queue.submit(activation, CLIENT, Collections.emptyMap()).ticket != null,
                        "A valid record could not use its owning screen's queue share");
            }
        }
        require(queue.submit(activation, CLIENT, Collections.emptyMap()).reason.equals("action_queue_full"),
                "Different row origins bypassed their shared activation capacity");
        queue.cancel(activation, "screen_closed");
        require(queue.pending() == 0, "Root cancellation failed to cancel record-origin requests");

        List<GuiActionTicket> followups = new ArrayList<>();
        activation.nested = () -> {
            followups.add(queue.submit(activation, CLIENT, Collections.emptyMap()).ticket);
            throw new IllegalArgumentException("Follow-up fixture failure");
        };
        GuiActionTicket failed = queue.submit(activation, CLIENT, Collections.emptyMap()).ticket;
        queue.dispatch(Scope.CLIENT);
        activation.nested = null;
        queue.dispatch(Scope.CLIENT);
        require(failed.status() == Status.FAILED && followups.get(0).status() == Status.CANCELLED,
                "A failed action left its newly submitted follow-up eligible");
        int[] remaining = {130};
        activation.nested = () -> {
            if (--remaining[0] > 0) {
                require(queue.submit(activation, CLIENT, Collections.emptyMap()).ticket != null,
                        "Completed outcome tokens accumulated across a valid deferred action chain");
            }
        };
        queue.submit(activation, CLIENT, Collections.emptyMap());
        for (int boundary = 0; boundary < 130; boundary++) {
            queue.dispatch(Scope.CLIENT);
        }
        require(remaining[0] == 0 && queue.pending() == 0,
                "A valid long action chain leaked origin scopes or stalled at the scope limit");
    }

    private static final class Activation implements GuiActionQueue.Activation {
        final String owner;
        final List<String> order;
        long revision;
        boolean paused;
        boolean fail;
        int failures;
        Runnable nested;
        Activation(String owner, List<String> order) {
            this.owner = owner;
            this.order = order;
        }

        public String owner() {
            return owner;
        }

        public long revision() {
            return revision;
        }

        public boolean active() {
            return true;
        }

        public String unavailable(Scope scope) {
            return scope == Scope.WORLD && paused ? "world_paused" : null;
        }

        public Object execute(GuiActionDefinition action, Map<String, Object> payload) {
            if (fail) {
                throw new IllegalArgumentException("action fixture");
            }
            order.add(owner);
            if (nested != null) {
                nested.run();
            }
            return Collections.singletonMap("owner", owner);
        }

        public void failed(String name, Throwable failure) {
            failures++;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
