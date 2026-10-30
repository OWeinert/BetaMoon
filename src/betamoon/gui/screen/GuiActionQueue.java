package betamoon.gui.screen;

import betamoon.gui.presentation.GuiPublishedData;
import betamoon.gui.screen.GuiActionDefinition.Scope;
import betamoon.gui.screen.GuiActionTicket.Status;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Fair owner queues dispatched only by their matching logical client/world
 * boundary.
 */
public final class GuiActionQueue {
    public interface Activation {
        String owner();

        long revision();

        boolean active();

        /** Null means eligible; refusal codes must not retain native views. */
        String unavailable(Scope scope);

        Object execute(GuiActionDefinition action, Map<String, Object> payload);

        void failed(String action, Throwable failure);
    }

    public static final class Submission {
        public final GuiActionTicket ticket;
        public final String reason;
        private Submission(GuiActionTicket ticket, String reason) {
            this.ticket = ticket;
            this.reason = reason;
        }
    }

    private static final int CAPACITY = 128;
    private static final int OWNER_CAPACITY = 32;
    private static final int ACTIVATION_CAPACITY = 16;
    private final Map<String, Deque<Request>> owners = new LinkedHashMap<>();
    private final Deque<String> order = new ArrayDeque<>();
    private final LongSupplier clock;
    private long identity;
    private int size;
    private boolean dispatching;

    public GuiActionQueue() {
        this(System::nanoTime);
    }

    public GuiActionQueue(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "action clock");
    }

    public int pending() {
        return size;
    }

    public Submission submit(Activation activation, GuiActionDefinition action, Map<String, Object> payload) {
        LuaCallbackScope.requireUiStateWrite();
        Objects.requireNonNull(activation, "UI action activation");
        Objects.requireNonNull(action, "UI action definition");
        if (!activation.active()) {
            return refused("screen_closed");
        }
        if (action.scope == Scope.PLAYER_DATA) {
            return refused("player_data_unavailable");
        }
        String unavailable = activation.unavailable(action.scope);
        if (unavailable != null) {
            return refused(unavailable);
        }
        Map<String, Object> normalized = action.payload.normalize(payload);
        GuiPublishedData detached = new GuiPublishedData();
        detached.publish(normalized);
        Deque<Request> owner = owners.get(activation.owner());
        int activationSize = 0;
        if (owner != null) {
            for (Request request : owner) {
                if (request.activation == activation) {
                    activationSize++;
                }
            }
        }
        if (size >= CAPACITY || owner != null && owner.size() >= OWNER_CAPACITY
                || activationSize >= ACTIVATION_CAPACITY) {
            return refused("action_queue_full");
        }
        if (owner == null) {
            owner = new ArrayDeque<>();
            owners.put(activation.owner(), owner);
            order.addLast(activation.owner());
        }
        GuiActionTicket ticket = new GuiActionTicket(++identity);
        owner.addLast(new Request(activation, action, detached.snapshot(), ticket));
        size++;
        return new Submission(ticket, null);
    }

    public void cancel(Activation activation, String reason) {
        List<Request> removed = new ArrayList<>();
        for (Deque<Request> owner : owners.values()) {
            owner.removeIf(request -> {
                if (request.activation != activation) {
                    return false;
                }
                removed.add(request);
                return true;
            });
        }
        for (Request request : removed) {
            size--;
            request.ticket.finish(Status.CANCELLED, null, reason);
        }
        prune();
    }

    /**
     * At most eight callbacks/2 ms per boundary; a callback itself is trusted Lua,
     * not preemptible.
     */
    public void dispatch(Scope boundary) {
        if (dispatching || boundary == Scope.PLAYER_DATA) {
            return;
        }
        dispatching = true;
        long started = clock.getAsLong();
        int attempts = order.size();
        int executed = 0;
        // Only work present on entry is eligible; callbacks cannot refill their own
        // slice indefinitely.
        long eligibleIdentity = identity;
        try {
            while (!order.isEmpty() && attempts-- > 0 && executed < 8) {
                if (executed > 0 && clock.getAsLong() - started >= 2_000_000) {
                    break;
                }
                String name = order.removeFirst();
                Deque<Request> owner = owners.get(name);
                Request selected = null;
                for (Request request : owner) {
                    if (request.action.scope == boundary && request.ticket.identity <= eligibleIdentity) {
                        selected = request;
                        break;
                    }
                }
                if (selected != null) {
                    owner.remove(selected);
                    size--;
                }
                if (owner.isEmpty()) {
                    owners.remove(name);
                } else {
                    order.addLast(name);
                }
                if (selected != null) {
                    run(selected);
                    executed++;
                    attempts = Math.max(attempts, order.size());
                }
            }
        } finally {
            dispatching = false;
        }
    }

    private void run(Request request) {
        Activation activation = request.activation;
        try {
            if (!activation.active() || activation.revision() != request.revision) {
                request.ticket.finish(Status.CANCELLED, null, "stale_screen_revision");
                return;
            }
            String unavailable = activation.unavailable(request.action.scope);
            if (unavailable != null) {
                request.ticket.finish(Status.REFUSED, null, unavailable);
                return;
            }
            request.ticket.running();
            Object result = activation.execute(request.action, request.payload);
            request.ticket.finish(Status.SUCCEEDED, result, null);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            request.ticket.finish(Status.FAILED, null, "fatal_action_failure");
            throw fatal;
        } catch (Throwable failure) {
            request.ticket.finish(Status.FAILED, null, "action_failed");
            activation.failed(request.action.name, failure);
        }
    }

    private void prune() {
        owners.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        order.removeIf(name -> !owners.containsKey(name));
    }

    private static Submission refused(String reason) {
        return new Submission(null, reason);
    }

    private static final class Request {
        final Activation activation;
        final GuiActionDefinition action;
        final long revision;
        final Map<String, Object> payload;
        final GuiActionTicket ticket;
        Request(Activation activation, GuiActionDefinition action, Map<String, Object> payload,
                GuiActionTicket ticket) {
            this.activation = activation;
            this.action = action;
            this.revision = activation.revision();
            this.payload = payload;
            this.ticket = ticket;
        }
    }
}
