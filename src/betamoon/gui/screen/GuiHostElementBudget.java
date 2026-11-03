package betamoon.gui.screen;

import betamoon.gui.collection.GuiRowBudget;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reversible static-root reservations sharing a host's dynamic-row capacity.
 */
public final class GuiHostElementBudget {
    private final GuiRowBudget rows;
    private final Map<Object, Integer> current = new IdentityHashMap<>();
    private final Map<Object, Claim> pending = new IdentityHashMap<>();

    public GuiHostElementBudget(GuiRowBudget rows) {
        this.rows = Objects.requireNonNull(rows, "host row capacity");
    }

    public Claim prepare(Object owner, int elements) {
        Objects.requireNonNull(owner, "document capacity owner");
        if (elements < 1 || elements > GuiRowBudget.MAX_LIVE_ELEMENTS || pending.containsKey(owner)) {
            throw new IllegalArgumentException("Invalid or overlapping document capacity proposal");
        }
        Claim claim = new Claim(owner, elements);
        pending.put(owner, claim);
        try {
            publish();
        } catch (RuntimeException failure) {
            pending.remove(owner);
            throw failure;
        }
        return claim;
    }

    public void remove(Object owner) {
        if (pending.containsKey(owner)) {
            throw new IllegalStateException("Abort a pending document proposal before removing its owner");
        }
        current.remove(owner);
        publish();
    }

    private void publish() {
        int live = 0;
        int reserved = 0;
        for (Map.Entry<Object, Integer> value : current.entrySet()) {
            reserved += value.getValue();
            if (!pending.containsKey(value.getKey())) {
                live += value.getValue();
            }
        }
        for (Claim claim : pending.values()) {
            live += claim.elements;
            reserved += claim.elements;
        }
        rows.externalElements(live, reserved);
    }

    public final class Claim implements AutoCloseable {
        private final Object owner;
        private final int elements;
        private boolean finished;

        private Claim(Object owner, int elements) {
            this.owner = owner;
            this.elements = elements;
        }

        public void commit() {
            requireCurrent();
            Integer previous = current.put(owner, elements);
            pending.remove(owner);
            try {
                publish();
            } catch (RuntimeException failure) {
                if (previous == null) {
                    current.remove(owner);
                } else {
                    current.put(owner, previous);
                }
                pending.put(owner, this);
                throw failure;
            }
            finished = true;
        }

        private void requireCurrent() {
            if (finished || pending.get(owner) != this) {
                throw new IllegalStateException("Document capacity proposal is stale or already finished");
            }
        }

        @Override
        public void close() {
            if (!finished) {
                requireCurrent();
                pending.remove(owner);
                try {
                    publish();
                } catch (RuntimeException failure) {
                    pending.put(owner, this);
                    throw failure;
                }
                finished = true;
            }
        }
    }
}
