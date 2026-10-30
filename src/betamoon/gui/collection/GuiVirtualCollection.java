package betamoon.gui.collection;

import betamoon.gui.action.GuiActionOrigin;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiInputLifetime;
import betamoon.gui.framework.GuiPreparationPhase;
import betamoon.gui.presentation.GuiViewStateAdmission;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Fixed-height row realization mounts ordinary elements into the host's
 * existing parent graph.
 */
public final class GuiVirtualCollection extends GuiContainer implements AutoCloseable {
    private final GuiRowBudget budget;
    private final GuiRowFactory factory;
    private final Consumer<Throwable> failures;
    private GuiCollectionModel model = new GuiCollectionModel();
    private Map<String, Entry> rows = new LinkedHashMap<>();
    private Map<String, RecordOrigins> actionOrigins = new LinkedHashMap<>();
    private GuiCollectionModel.Snapshot requested;
    private GuiCollectionWindow window;
    private int requestedOffset;
    private int rowHeight = 20;
    private int overscan = 2;
    private int appliedOverscan;
    private int appliedWidth;
    private long refreshRevision;
    private long appliedRefreshRevision;
    private boolean explicitScroll;
    private boolean dirty = true;
    private boolean closed;
    private boolean staging;
    private List<Object> rejected;
    private GuiElement restoreFocus;

    public GuiVirtualCollection(GuiRowBudget budget, GuiRowFactory factory, Consumer<Throwable> failures) {
        this.budget = Objects.requireNonNull(budget, "row budget");
        this.factory = Objects.requireNonNull(factory, "row factory");
        this.failures = Objects.requireNonNull(failures, "row failure handler");
    }

    public void setModel(GuiCollectionModel.Snapshot value) {
        LuaCallbackScope.requireUiStateWrite();
        requireOpen();
        Objects.requireNonNull(value, "collection model");
        GuiCollectionModel.Snapshot previous = requested == null ? model.snapshot() : requested;
        if (previous.records.equals(value.records)) {
            return;
        }
        requested = value;
        dirty = true;
    }

    public void setMetrics(int rowHeight, int overscan) {
        LuaCallbackScope.requireUiStateWrite();
        requireOpen();
        if (rowHeight < 1 || rowHeight > 1024 || overscan < 0 || overscan > 8) {
            throw new IllegalArgumentException("Collection requires a row height 1..1024 and overscan 0..8");
        }
        if (this.rowHeight != rowHeight || this.overscan != overscan) {
            this.rowHeight = rowHeight;
            this.overscan = overscan;
            dirty = true;
        }
    }

    public void setScrollOffset(int offset) {
        LuaCallbackScope.requireUiStateWrite();
        requireOpen();
        int maximum = Math.max(0, model.visible().size() * rowHeight - getHeight());
        int next = Math.max(0, Math.min(offset, maximum));
        if (requestedOffset != next) {
            requestedOffset = next;
            explicitScroll = true;
            dirty = true;
        }
    }

    public void refreshRows() {
        LuaCallbackScope.requireUiStateWrite();
        requireOpen();
        refreshRevision++;
        dirty = true;
        rejected = null;
    }

    public GuiCollectionModel.Snapshot model() {
        return model.snapshot();
    }

    public int realizedRows() {
        return rows.size();
    }

    public int scrollOffset() {
        return window == null ? 0 : window.offset;
    }

    public GuiElement rowElement(String key) {
        Entry row = rows.get(key);
        return row == null ? null : row.row.element();
    }

    public Map<String, Object> rowState(String key) {
        Entry row = rows.get(key);
        return row == null ? model.rowState(key) : row.row.state();
    }

    @Override
    protected void prepareStructureElement(GuiContext context) {
        if (closed || staging || model.hasStateReservations() || !GuiPreparationPhase.active()) {
            return;
        }
        int height = visibleThroughParents() ? getHeight() : 0;
        boolean refresh = false;
        List<Object> rowInputs = new ArrayList<>();
        if (height > 0) {
            try {
                for (Entry entry : new ArrayList<>(rows.values())) {
                    entry.row.update(context, getWidth(), rowHeight);
                    if (entry.row.needsRefresh()) {
                        refresh = true;
                        rowInputs.add(entry.record.key);
                        rowInputs.add(entry.row.refreshStamp());
                    }
                }
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                failures.accept(failure);
                return;
            }
        }
        if (!dirty && !refresh && window != null && window.viewportHeight == height && window.rowHeight == rowHeight
                && appliedWidth == getWidth() && appliedOverscan == overscan) {
            return;
        }
        List<Object> attempt = Arrays.asList(requested, requestedOffset, explicitScroll, getWidth(), height, rowHeight,
                overscan, refreshRevision, rowInputs);
        if (attempt.equals(rejected)) {
            return;
        }
        List<Staged> staged = new ArrayList<>();
        staging = true;
        try {
            if (height > 0) {
                requireBoundedNesting();
            }
            GuiCollectionModel next = model.fork(requested == null ? model.snapshot() : requested);
            Map<String, RecordOrigins> nextOrigins = prepareActionOrigins(next.snapshot());
            for (Entry entry : rows.values()) {
                if (next.snapshot().keys.containsKey(entry.record.key)) {
                    next.publishRowState(entry.record.key, entry.row.state());
                }
            }
            GuiCollectionWindow nextWindow = window == null || explicitScroll
                    ? GuiCollectionWindow.create(next.visible(), rowHeight, height, overscan, requestedOffset)
                    : window.reconcile(next.visible(), rowHeight, height, overscan);
            Map<String, Entry> nextRows = new LinkedHashMap<>();
            Set<GuiElement> elements = Collections.newSetFromMap(new IdentityHashMap<GuiElement, Boolean>());
            for (int index = nextWindow.first; index < nextWindow.lastExclusive; index++) {
                GuiCollectionModel.Row record = next.visible().get(index);
                Entry previous = rows.get(record.record.key);
                Entry entry = previous;
                if (entry == null || refreshRevision != appliedRefreshRevision || !entry.record.equals(record.record)
                        || entry.depth != record.depth || entry.row.needsRefresh()) {
                    GuiRowFactory.Prepared prepared;
                    Object stateIdentity = new Object();
                    GuiRowFactory.Context rowContext = new GuiRowFactory.Context(context, budget, record.record,
                            record.depth, getWidth(), rowHeight, next.rowState(record.record.key),
                            previous == null ? null : previous.row,
                            values -> publishRowState(record.record.key, stateIdentity, values),
                            values -> reserveRowState(record.record.key, stateIdentity, values));
                    try (GuiRowBudget.Preparation preparation = budget.prepareFactory()) {
                        prepared = factory.prepare(rowContext);
                    }
                    if (prepared == null) {
                        throw new IllegalArgumentException("Row factory returned no prepared row");
                    }
                    Staged candidate = new Staged(prepared);
                    staged.add(candidate);
                    budget.requirePrepared(prepared.reservation());
                    GuiRowFactory.Row row = Objects.requireNonNull(prepared.candidate(), "prepared row");
                    if (row.element() == null || row.element().getParent() != null) {
                        throw new IllegalArgumentException("Row factory must prepare an unattached root");
                    }
                    Object revision = row.actionRevision();
                    RecordOrigins origins = nextOrigins.get(record.record.key)
                            .withCallbacks(revision == null ? factory : revision, refreshRevision);
                    nextOrigins.put(record.record.key, origins);
                    rowContext.bindActionOrigins(origins.recordOrigin, origins.callbackOrigin);
                    entry = new Entry(record.record, record.depth, row, prepared.reservation(), stateIdentity,
                            rowContext);
                    candidate.entry = entry;
                    next.publishRowState(record.record.key, row.state());
                }
                if (!elements.add(entry.row.element())) {
                    throw new IllegalArgumentException("Row roots must be unique");
                }
                nextRows.put(record.record.key, entry);
            }
            for (Staged candidate : staged) {
                candidate.prepared.validate();
            }
            requireOpen();
            commit(context, next, nextWindow, nextRows, nextOrigins, staged);
            requested = null;
            requestedOffset = nextWindow.offset;
            explicitScroll = false;
            dirty = false;
            rejected = null;
            appliedWidth = getWidth();
            appliedOverscan = overscan;
            appliedRefreshRevision = refreshRevision;
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            rejected = attempt;
            failures.accept(failure);
        } finally {
            try {
                closeCandidates(staged);
            } finally {
                staging = false;
            }
        }
    }

    private void closeCandidates(List<Staged> staged) {
        Throwable cleanup = null;
        for (Staged candidate : staged) {
            try {
                candidate.close();
            } catch (RuntimeException | Error failure) {
                cleanup = collectCleanup(cleanup, failure);
            }
        }
        reportCleanup(cleanup);
    }

    private void commit(GuiContext context, GuiCollectionModel next, GuiCollectionWindow nextWindow,
            Map<String, Entry> nextRows, Map<String, RecordOrigins> nextOrigins, List<Staged> staged) {
        List<GuiRowBudget.Reservation> additions = new ArrayList<>();
        List<GuiRowBudget.Reservation> removals = new ArrayList<>();
        GuiElement focused = null;
        Entry focusedRow = null;
        for (Entry previous : rows.values()) {
            GuiElement match = focused(context, previous.row.element());
            if (match != null) {
                focused = match;
                focusedRow = previous;
            }
            if (nextRows.get(previous.record.key) != previous) {
                removals.add(previous.reservation);
            }
        }
        for (Staged candidate : staged) {
            additions.add(candidate.entry.reservation);
        }
        budget.activateReplacing(additions, removals);
        try {
            for (Staged candidate : staged) {
                GuiRowFactory.Row committed = candidate.prepared.commit();
                if (committed != candidate.entry.row) {
                    throw new IllegalStateException("Prepared row changed identity on commit");
                }
                candidate.entry.input.activate();
                candidate.committed = true;
            }
            for (Map.Entry<String, RecordOrigins> previous : actionOrigins.entrySet()) {
                previous.getValue().retireUnless(nextOrigins.get(previous.getKey()));
            }
            for (RecordOrigins origins : nextOrigins.values()) {
                origins.activate();
            }
            for (Entry previous : rows.values()) {
                if (nextRows.get(previous.record.key) != previous) {
                    previous.close();
                }
            }
            model = next;
            window = nextWindow;
            rows = nextRows;
            actionOrigins = nextOrigins;
            for (Staged candidate : staged) {
                candidate.entry.context.adopt();
            }
            List<GuiElement> children = new ArrayList<>();
            for (Entry entry : nextRows.values()) {
                children.add(entry.mount);
            }
            reconcileChildren(children);
            if (focusedRow != null) {
                Entry replacement = nextRows.get(focusedRow.record.key);
                if (replacement != null && replacement != focusedRow) {
                    restoreFocus = replacement.row.compatibleFocus(focusedRow.row, focused);
                }
            }
            requestLayout();
        } catch (RuntimeException | Error failure) {
            Throwable cleanup = null;
            try {
                close();
            } catch (RuntimeException | Error error) {
                cleanup = collectCleanup(cleanup, error);
            }
            for (Staged candidate : staged) {
                if (candidate.entry != null) {
                    try {
                        candidate.entry.close();
                    } catch (RuntimeException | Error error) {
                        cleanup = collectCleanup(cleanup, error);
                    }
                }
            }
            if (cleanup != null) {
                Throwable combined = collectCleanup(failure, cleanup);
                if (combined != failure) {
                    throw (Error) combined;
                }
            }
            throw failure;
        }
    }

    private Map<String, RecordOrigins> prepareActionOrigins(GuiCollectionModel.Snapshot snapshot) {
        Map<String, RecordOrigins> next = new LinkedHashMap<>();
        for (GuiCollectionModel.Record record : snapshot.records) {
            RecordOrigins previous = actionOrigins.get(record.key);
            RecordOrigins origins = previous != null && previous.record.equals(record)
                    ? previous
                    : new RecordOrigins(record);
            if (origins.callbackOrigin != null && origins.refresh != refreshRevision) {
                origins = origins.withCallbacks(origins.callbackRevision, refreshRevision);
            }
            next.put(record.key, origins);
        }
        return next;
    }

    @Override
    protected void prepareVisualsElement(GuiContext context) {
        if (restoreFocus != null) {
            GuiElement target = restoreFocus;
            restoreFocus = null;
            context.requestFocus(target);
        }
    }

    private static GuiElement focused(GuiContext context, GuiElement element) {
        if (context.isFocused(element)) {
            return element;
        }
        if (element instanceof GuiContainer) {
            for (GuiElement child : ((GuiContainer) element).getChildren()) {
                GuiElement found = focused(context, child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean visibleThroughParents() {
        for (GuiElement element = this; element != null; element = element.getParent()) {
            if (!element.isVisible() || element.isCollapsed() || element.getOpacity() == 0) {
                return false;
            }
        }
        return true;
    }

    private void requireBoundedNesting() {
        int rowDepth = 0;
        for (GuiElement element = getParent(); element != null; element = element.getParent()) {
            if (element instanceof RowMount && ++rowDepth >= 32) {
                throw new IllegalArgumentException("Collection rows exceed 32 nested realization levels");
            }
        }
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        if (window == null) {
            return;
        }
        int index = window.first;
        for (Entry entry : rows.values()) {
            int top = window.rowTop(index++);
            entry.mount.setHidden(top + window.rowHeight <= 0 || top >= getHeight());
            entry.mount.arrange(context,
                    Rect.fromPositionAndSize(getLeft(), getTop() + top, getWidth(), window.rowHeight));
        }
    }

    @Override
    protected Rect childInputClip() {
        return getBounds();
    }

    @Override
    protected void renderBeforeChildren(GuiContext context) {
        context.getRenderer().pushClip(getBounds());
    }

    @Override
    protected void renderAfterChildren(GuiContext context) {
        context.getRenderer().popClip();
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        if (event.getType() == GuiInputEvent.Type.SCROLL && contains(event.getMouseX(), event.getMouseY())) {
            int previous = requestedOffset;
            long target = previous - (long) event.getWheelDelta() / 8;
            setScrollOffset((int) Math.max(0, Math.min(Integer.MAX_VALUE, target)));
            return previous != requestedOffset;
        }
        return false;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Collection view has closed");
        }
    }

    private void publishRowState(String key, Object identity, Map<String, ?> values) {
        requireStateOwner(key, identity);
        model.publishRowState(key, values);
    }

    private GuiViewStateAdmission.Proposal reserveRowState(String key, Object identity, Map<String, Object> values) {
        requireStateOwner(key, identity);
        GuiCollectionModel.StateReservation reservation = model.reserveRowState(key, values);
        return new GuiViewStateAdmission.Proposal() {
            public void commit(Map<String, Object> accepted) {
                publishRowState(key, identity, accepted);
                reservation.commit();
            }

            public void close() {
                reservation.close();
            }
        };
    }

    private void requireStateOwner(String key, Object identity) {
        requireOpen();
        if (staging) {
            throw new IllegalStateException("Row state publication cannot reenter collection candidate staging");
        }
        Entry entry = rows.get(key);
        if (entry == null || entry.closed || entry.stateIdentity != identity) {
            throw new IllegalStateException("Row state publication expired with its realized view");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (RecordOrigins origins : actionOrigins.values()) {
            origins.retireUnless(null);
        }
        actionOrigins.clear();
        Throwable cleanup = null;
        try {
            for (Entry entry : rows.values()) {
                try {
                    entry.close();
                } catch (RuntimeException | Error failure) {
                    cleanup = collectCleanup(cleanup, failure);
                }
            }
        } finally {
            rows.clear();
            requested = null;
            restoreFocus = null;
            rejected = null;
            window = null;
            model = new GuiCollectionModel();
            clear();
        }
        reportCleanup(cleanup);
    }

    private static boolean isFatal(Throwable failure) {
        return failure instanceof VirtualMachineError || failure instanceof ThreadDeath;
    }

    private static Throwable collectCleanup(Throwable previous, Throwable failure) {
        if (previous == null) {
            return failure;
        }
        if (isFatal(failure) && !isFatal(previous)) {
            return collectCleanup(failure, previous);
        }
        if (previous != failure && previous.getSuppressed().length < 16) {
            previous.addSuppressed(failure);
        }
        return previous;
    }

    private void reportCleanup(Throwable failure) {
        if (isFatal(failure)) {
            throw (Error) failure;
        }
        if (failure != null) {
            failures.accept(failure);
        }
    }

    private static final class Entry implements AutoCloseable {
        final GuiCollectionModel.Record record;
        final int depth;
        final GuiRowFactory.Row row;
        final GuiRowBudget.Reservation reservation;
        final GuiInputLifetime input = new GuiInputLifetime();
        final RowMount mount;
        final Object stateIdentity;
        final GuiRowFactory.Context context;
        boolean closed;
        Entry(GuiCollectionModel.Record record, int depth, GuiRowFactory.Row row, GuiRowBudget.Reservation reservation,
                Object stateIdentity, GuiRowFactory.Context context) {
            this.record = record;
            this.depth = depth;
            this.row = row;
            this.reservation = reservation;
            this.stateIdentity = stateIdentity;
            this.context = context;
            mount = new RowMount(row.element());
            mount.inputLifetime(input);
        }

        public void close() {
            if (!closed) {
                closed = true;
                input.close();
                try {
                    row.close();
                } finally {
                    reservation.close();
                }
            }
        }
    }

    private static final class RecordOrigins {
        final GuiCollectionModel.Record record;
        final GuiActionOrigin recordOrigin;
        final GuiActionOrigin callbackOrigin;
        final Object callbackRevision;
        final long refresh;

        RecordOrigins(GuiCollectionModel.Record record) {
            this(record, GuiActionOrigin.pending(), null, null, -1);
        }

        RecordOrigins(GuiCollectionModel.Record record, GuiActionOrigin recordOrigin, GuiActionOrigin callbackOrigin,
                Object callbackRevision, long refresh) {
            this.record = record;
            this.recordOrigin = recordOrigin;
            this.callbackOrigin = callbackOrigin;
            this.callbackRevision = callbackRevision;
            this.refresh = refresh;
        }

        RecordOrigins withCallbacks(Object revision, long nextRefresh) {
            return Objects.equals(callbackRevision, revision) && refresh == nextRefresh
                    ? this
                    : new RecordOrigins(record, recordOrigin, GuiActionOrigin.pending(), revision, nextRefresh);
        }

        void activate() {
            recordOrigin.activate();
            if (callbackOrigin != null) {
                callbackOrigin.activate();
            }
        }

        void retireUnless(RecordOrigins next) {
            if (next == null || recordOrigin != next.recordOrigin) {
                recordOrigin.invalidate();
            }
            if (callbackOrigin != null && (next == null || callbackOrigin != next.callbackOrigin)) {
                callbackOrigin.invalidate();
            }
        }
    }

    private static final class RowMount extends GuiContainer {
        private Rect arranged;

        RowMount(GuiElement element) {
            add(element);
        }

        @Override
        protected void arrangeElement(GuiContext context) {
            Rect bounds = getBounds();
            if (arranged != null && (arranged.getLeft() != bounds.getLeft() || arranged.getTop() != bounds.getTop()
                    || arranged.getWidth() != bounds.getWidth() || arranged.getHeight() != bounds.getHeight())) {
                context.cancelGesturesIn(this);
            }
            arranged = bounds;
            super.arrangeElement(context);
        }

        @Override
        protected Rect childInputClip() {
            return getBounds();
        }

        @Override
        protected void renderBeforeChildren(GuiContext context) {
            context.getRenderer().pushClip(getBounds());
        }

        @Override
        protected void renderAfterChildren(GuiContext context) {
            context.getRenderer().popClip();
        }
    }

    private static final class Staged implements AutoCloseable {
        final GuiRowFactory.Prepared prepared;
        Entry entry;
        boolean committed;
        Staged(GuiRowFactory.Prepared prepared) {
            this.prepared = prepared;
        }

        public void close() {
            try {
                prepared.close();
            } finally {
                if (!committed && entry != null) {
                    entry.close();
                }
            }
        }
    }
}
