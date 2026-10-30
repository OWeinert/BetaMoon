package betamoon.gui.collection;

import betamoon.gui.framework.GuiPreparationPhase;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * One host's live rows and temporary candidates share a bounded element budget.
 */
public final class GuiRowBudget implements AutoCloseable {
    public static final int MAX_LIVE_ROWS = 1024;
    public static final int MAX_LIVE_ELEMENTS = 8192;
    public static final int MAX_RESERVED_ELEMENTS = 16384;
    private final Set<Reservation> reservations = new HashSet<>();
    private int reservedElements;
    private int liveElements;
    private int liveRows;
    private boolean closed;
    private int preparationDepth;

    public Preparation prepareFactory() {
        requireOpen();
        if (!GuiPreparationPhase.active() || preparationDepth >= 32) {
            throw new IllegalStateException("Row factories require Java logical preparation within 32 nested levels");
        }
        return new Preparation(this, ++preparationDepth);
    }

    public Reservation reserve(int elements) {
        requireOpen();
        if (elements < 1 || elements > MAX_LIVE_ELEMENTS || elements > MAX_RESERVED_ELEMENTS - reservedElements) {
            throw new IllegalArgumentException("Row candidates exceed the host's 16384 reserved element budget");
        }
        Reservation reservation = new Reservation(this, elements);
        reservations.add(reservation);
        reservedElements += elements;
        return reservation;
    }

    /**
     * Admit the complete row delta before changing any live token or retiring any
     * predecessor.
     */
    public void activateReplacing(Collection<Reservation> candidates, Collection<Reservation> predecessors) {
        requireOpen();
        Set<Reservation> next = checked(candidates, false);
        Set<Reservation> previous = checked(predecessors, true);
        int elements = liveElements;
        for (Reservation old : previous) {
            elements -= old.elements;
        }
        for (Reservation candidate : next) {
            elements += candidate.elements;
        }
        int rows = liveRows - previous.size() + next.size();
        if (elements > MAX_LIVE_ELEMENTS || rows > MAX_LIVE_ROWS) {
            throw new IllegalArgumentException("Host exceeds 8192 live row elements or 1024 live rows");
        }
        for (Reservation old : previous) {
            old.active = false;
        }
        for (Reservation candidate : next) {
            candidate.active = true;
        }
        liveElements = elements;
        liveRows = rows;
    }

    private Set<Reservation> checked(Collection<Reservation> values, boolean active) {
        if (values == null || values.size() > MAX_LIVE_ROWS) {
            throw new IllegalArgumentException("Invalid row reservation batch");
        }
        Set<Reservation> result = new HashSet<>();
        for (Reservation reservation : values) {
            if (reservation == null || reservation.owner != this || reservation.closed || reservation.active != active
                    || !result.add(reservation)) {
                throw new IllegalArgumentException(
                        "Row reservations must be unique, owned and in their expected phase");
            }
        }
        return result;
    }

    void requirePrepared(Reservation reservation) {
        requireOpen();
        if (reservation == null || reservation.owner != this || reservation.closed || reservation.active) {
            throw new IllegalArgumentException("Prepared row must hold an unused reservation from its host");
        }
    }

    public int reservedElements() {
        return reservedElements;
    }

    public int liveElements() {
        return liveElements;
    }

    public int liveRows() {
        return liveRows;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Row budget has closed with its host");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            for (Reservation reservation : reservations) {
                reservation.closed = true;
                reservation.active = false;
            }
            reservations.clear();
            reservedElements = 0;
            liveElements = 0;
            liveRows = 0;
        }
    }

    public static final class Reservation implements AutoCloseable {
        private final GuiRowBudget owner;
        private final int elements;
        private boolean active;
        private boolean closed;

        private Reservation(GuiRowBudget owner, int elements) {
            this.owner = owner;
            this.elements = elements;
        }

        public boolean active() {
            return active && !closed;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                owner.reservations.remove(this);
                owner.reservedElements -= elements;
                if (active) {
                    active = false;
                    owner.liveElements -= elements;
                    owner.liveRows--;
                }
            }
        }
    }

    public static final class Preparation implements AutoCloseable {
        private final GuiRowBudget owner;
        private final int depth;
        private final Thread thread = Thread.currentThread();
        private boolean closed;

        private Preparation(GuiRowBudget owner, int depth) {
            this.owner = owner;
            this.depth = depth;
        }

        @Override
        public void close() {
            if (!closed) {
                if (Thread.currentThread() != thread || owner.preparationDepth != depth) {
                    throw new IllegalStateException(
                            "Row factory preparation closes in reverse order on its owning thread");
                }
                owner.preparationDepth--;
                closed = true;
            }
        }
    }
}
