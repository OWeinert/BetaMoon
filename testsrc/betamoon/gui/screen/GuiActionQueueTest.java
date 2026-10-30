package betamoon.gui.screen;

import betamoon.gui.component.GuiComponentSchema;
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
