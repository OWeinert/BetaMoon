package betamoon.gui.action;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Causal validity for deferred UI intent. It grants no Lua, native, gameplay or
 * scheduling authority.
 */
public final class GuiActionOrigin {
    private enum State {
        PENDING, READY, EXPIRED
    }

    private static final int MAX_SCOPES = 64;
    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Frame NOOP = new Frame(Collections.emptyList(), null);
    private final BooleanSupplier alive;
    private final boolean mutable;
    private final boolean event;
    private volatile State state;

    private GuiActionOrigin(State state, BooleanSupplier alive, boolean mutable, boolean event) {
        this.state = state;
        this.alive = alive;
        this.mutable = mutable;
        this.event = event;
    }

    public static GuiActionOrigin pending() {
        return new GuiActionOrigin(State.PENDING, () -> true, true, false);
    }

    public static GuiActionOrigin lifetime(BooleanSupplier alive) {
        return new GuiActionOrigin(State.READY, Objects.requireNonNull(alive, "UI action lifetime"), false, false);
    }

    public void activate() {
        if (!mutable || event || state == State.EXPIRED) {
            throw new IllegalStateException("This UI action origin cannot be activated");
        }
        state = State.READY;
    }

    public void invalidate() {
        if (!mutable || event) {
            throw new IllegalStateException("This UI action origin belongs to its callback or external lifetime");
        }
        state = State.EXPIRED;
    }

    public boolean expired() {
        return state == State.EXPIRED || !alive.getAsBoolean();
    }

    public boolean pendingEvent() {
        return event && state == State.PENDING && !expired();
    }

    public boolean maySubmit() {
        return !expired() && (state == State.READY || event);
    }

    private boolean redundantOutcome() {
        return event && state == State.READY;
    }

    public static Frame event() {
        GuiActionOrigin outcome = new GuiActionOrigin(State.PENDING, () -> true, false, true);
        return push(Collections.singletonList(outcome), outcome);
    }

    public static void rejectEvent() {
        for (Frame frame : FRAMES.get()) {
            if (frame.outcome != null) {
                if (frame.outcome.state == State.PENDING) {
                    frame.outcome.state = State.EXPIRED;
                }
                return;
            }
        }
    }

    public static Frame enter(Collection<GuiActionOrigin> origins) {
        return enter(origins, null);
    }

    public static Frame enter(Collection<GuiActionOrigin> origins, GuiActionOrigin additional) {
        Set<GuiActionOrigin> selected = new LinkedHashSet<>();
        for (GuiActionOrigin origin : origins) {
            if (origin != null && !origin.redundantOutcome()) {
                selected.add(origin);
            }
        }
        if (additional != null && !additional.redundantOutcome()) {
            selected.add(additional);
        }
        return selected.isEmpty() ? NOOP : push(new ArrayList<>(selected), null);
    }

    private static Frame push(List<GuiActionOrigin> origins, GuiActionOrigin outcome) {
        Deque<Frame> frames = FRAMES.get();
        if (frames.size() >= MAX_SCOPES || origins.size() > MAX_SCOPES) {
            throw new IllegalStateException("UI action origin scope limit exceeded: 64");
        }
        Frame frame = new Frame(Collections.unmodifiableList(origins), outcome);
        frames.push(frame);
        return frame;
    }

    public static List<GuiActionOrigin> capture() {
        Set<GuiActionOrigin> selected = new LinkedHashSet<>();
        for (Frame frame : FRAMES.get()) {
            for (GuiActionOrigin origin : frame.origins) {
                if (!origin.redundantOutcome()) {
                    selected.add(origin);
                }
            }
        }
        if (selected.size() > MAX_SCOPES) {
            throw new IllegalStateException("Deferred UI intent exceeds 64 origin lifetimes");
        }
        return selected.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(selected));
    }

    public static final class Frame implements AutoCloseable {
        private final List<GuiActionOrigin> origins;
        private final GuiActionOrigin outcome;
        private final Thread thread = Thread.currentThread();
        private boolean closed;
        private boolean committed;

        private Frame(List<GuiActionOrigin> origins, GuiActionOrigin outcome) {
            this.origins = origins;
            this.outcome = outcome;
        }

        private void requireCurrent() {
            if (closed || Thread.currentThread() != thread || FRAMES.get().peek() != this) {
                throw new IllegalStateException("UI action origin scopes close on their thread in reverse order");
            }
        }

        public void commit() {
            requireCurrent();
            if (outcome == null || committed) {
                throw new IllegalStateException("Only an unfinished event outcome can commit");
            }
            if (outcome.state == State.PENDING) {
                outcome.state = State.READY;
            }
            committed = true;
        }

        @Override
        public void close() {
            if (this == NOOP || closed) {
                return;
            }
            requireCurrent();
            FRAMES.get().pop();
            if (outcome != null && !committed) {
                outcome.state = State.EXPIRED;
            }
            closed = true;
        }
    }
}
