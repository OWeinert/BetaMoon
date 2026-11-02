package betamoon.gui.collection;

import betamoon.gui.action.GuiActionOrigin;
import betamoon.gui.drawing.GuiPaintBudget;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiFocusViewport;
import betamoon.gui.graphics.GraphicsClip;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.widget.GuiScrollView;
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
import java.util.LinkedHashSet;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/**
 * Fixed-height row realization mounts ordinary elements into the host's
 * existing parent graph.
 */
public final class GuiVirtualCollection extends GuiContainer implements AutoCloseable, GuiFocusViewport {
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
    private String requestedFocus;
    private boolean selectionRequested;
    private String requestedSelection;
    private boolean expansionRequested;
    private Set<String> requestedExpansion = Collections.emptySet();
    private String requestedReveal;
    private String requestedAnchor;
    private int requestedAnchorInset;
    private boolean projectionRequested;
    private List<String> requestedProjection;
    private GuiViewStateAdmission stateAdmission;
    private long intentRevision;
    private long acceptedIntentRevision;

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
        intentRevision++;
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
            intentRevision++;
            dirty = true;
        }
    }

    public void setScrollOffset(int offset) {
        LuaCallbackScope.requireUiStateWrite();
        requireOpen();
        if (requestedAnchor != null) {
            requestedAnchor = null;
            requestedAnchorInset = 0;
            explicitScroll = true;
            intentRevision++;
            dirty = true;
        }
        int maximum = Math.max(0, model.visible().size() * rowHeight - Math.max(1, getHeight()));
        int next = Math.max(0, Math.min(offset, maximum));
        if (requestedOffset != next) {
            requestedOffset = next;
            intentRevision++;
            explicitScroll = true;
            dirty = true;
        }
    }

    public void scrollToStart() {
        LuaCallbackScope.requireUiStateWrite();
        requireOpen();
        requestedOffset = 0;
        requestedAnchor = null;
        requestedAnchorInset = 0;
        explicitScroll = true;
        intentRevision++;
        dirty = true;
    }

    public void refreshRows() {
        LuaCallbackScope.requireUiStateWrite();
        requireOpen();
        refreshRevision++;
        intentRevision++;
        dirty = true;
        rejected = null;
    }

    public GuiCollectionModel.Snapshot model() {
        return model.snapshot();
    }

    public String selection() {
        return selectionRequested ? requestedSelection : model.selected();
    }

    public Set<String> expansion() {
        return expansionRequested ? requestedExpansion : model.expanded();
    }

    public List<GuiCollectionModel.Row> visibleRows() {
        return model.visible();
    }

    public void setSelection(String key) {
        LuaCallbackScope.requireUiStateWrite();
        requireOpen();
        requireKey(key);
        if (!Objects.equals(selection(), key)) {
            requestedSelection = key;
            selectionRequested = true;
            intentRevision++;
            dirty = true;
        }
    }

    public void setExpansion(Set<String> keys) {
        LuaCallbackScope.requireUiStateWrite();
        requireOpen();
        if (keys == null || keys.size() > GuiCollectionModel.MAX_RECORDS) {
            throw new IllegalArgumentException("Collection expansion requires at most 4096 keys");
        }
        for (String key : keys) {
            requireKey(key);
            if (key == null) {
                throw new IllegalArgumentException("Expanded collection keys cannot be null");
            }
        }
        if (!expansion().equals(keys)) {
            requestedExpansion = Collections.unmodifiableSet(new LinkedHashSet<>(keys));
            expansionRequested = true;
            intentRevision++;
            dirty = true;
        }
    }

    public void setProjection(List<String> keys) {
        LuaCallbackScope.requireUiStateWrite();
        requireOpen();
        if (!Objects.equals(requestedProjection, keys)) {
            if (keys != null && keys.size() > GuiCollectionModel.MAX_RECORDS) {
                throw new IllegalArgumentException("Collection projection exceeds 4096 keys");
            }
            requestedProjection = keys == null ? null : Collections.unmodifiableList(new ArrayList<>(keys));
            projectionRequested = true;
            intentRevision++;
            dirty = true;
        }
    }

    public void reveal(String key) {
        LuaCallbackScope.requireUiStateWrite();
        requireOpen();
        requireKey(key);
        requestedReveal = key;
        intentRevision++;
        dirty = true;
    }

    public void admitStateWith(GuiViewStateAdmission admission) {
        LuaCallbackScope.requireUiStateWrite();
        requireOpen();
        if (stateAdmission != null || staging || model.hasStateReservations()) {
            throw new IllegalStateException("Install collection state admission once outside preparation/proposals");
        }
        stateAdmission = Objects.requireNonNull(admission, "collection state admission");
        intentRevision++;
    }

    public Map<String, Object> captureState() {
        Map<String, Object> values = state(model, selection(), expansion(),
                explicitScroll ? requestedOffset : scrollOffset(),
                window == null || explicitScroll ? rowHeight : window.rowHeight);
        if (requestedAnchor == null) {
            return values;
        }
        Map<String, Object> pending = new LinkedHashMap<>(values);
        pending.put("schema", 1);
        pending.put("anchor", requestedAnchor);
        pending.put("inset", requestedAnchorInset);
        return Collections.unmodifiableMap(pending);
    }

    private static Map<String, Object> state(GuiCollectionModel model, String selection, Set<String> expansion,
            int offset, int rowHeight) {
        Map<String, Object> rows = model.rowStates();
        if (selection == null && expansion.isEmpty() && offset == 0 && rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("schema", 1);
        if (selection != null) {
            values.put("selection", selection);
        }
        if (!expansion.isEmpty()) {
            values.put("expanded", Collections.unmodifiableList(new ArrayList<>(expansion)));
        }
        if (offset != 0) {
            values.put("offset", offset);
            int index = offset / rowHeight;
            if (index < model.visible().size()) {
                values.put("anchor", model.visible().get(index).record.key);
                values.put("inset", offset % rowHeight);
            }
        }
        if (!rows.isEmpty()) {
            values.put("rows", rows);
        }
        return Collections.unmodifiableMap(values);
    }

    public Runnable prepareStateRestore(Map<String, Object> values) {
        LuaCallbackScope.requireUiStateWrite();
        requireOpen();
        if (staging || window != null || model.hasStateReservations()) {
            throw new IllegalStateException("Restore collection state before realizing its candidate rows");
        }
        GuiCollectionModel next = new GuiCollectionModel();
        next.publish(requested == null ? model.snapshot() : requested);
        int offset = 0;
        String anchor = null;
        int inset = 0;
        if (!values.isEmpty()) {
            for (String field : values.keySet()) {
                if (!Arrays.asList("schema", "selection", "expanded", "offset", "rows", "anchor", "inset")
                        .contains(field)) {
                    throw new IllegalArgumentException("Unknown collection state field: " + field);
                }
            }
            if (!(values.get("schema") instanceof Number) || ((Number) values.get("schema")).doubleValue() != 1) {
                throw new IllegalArgumentException("Collection state requires schema 1");
            }
            Object selected = values.get("selection");
            if (selected != null && !(selected instanceof String)) {
                throw new IllegalArgumentException("Saved collection selection requires a key");
            }
            requireKey((String) selected);
            GuiCollectionModel.Record record = next.snapshot().keys.get(selected);
            if (record != null && record.enabled) {
                next.select(record.key);
            }
            Object expanded = values.get("expanded");
            if (expanded != null) {
                if (!(expanded instanceof List) || ((List<?>) expanded).size() > GuiCollectionModel.MAX_RECORDS) {
                    throw new IllegalArgumentException("Saved collection expansion requires bounded keys");
                }
                for (Object key : (List<?>) expanded) {
                    if (!(key instanceof String)) {
                        throw new IllegalArgumentException("Saved collection expansion requires string keys");
                    }
                    requireKey((String) key);
                    if (next.snapshot().keys.containsKey(key) && next.snapshot().branch((String) key)) {
                        next.expand((String) key, true);
                    }
                }
            }
            if (values.containsKey("offset")) {
                Object number = values.get("offset");
                if (!(number instanceof Number)) {
                    throw new IllegalArgumentException("Saved collection offset requires an integer");
                }
                double value = ((Number) number).doubleValue();
                if (!Double.isFinite(value) || value < 0 || value > Integer.MAX_VALUE || value != Math.rint(value)) {
                    throw new IllegalArgumentException("Saved collection offset requires a bounded integer");
                }
                offset = (int) value;
            }
            if (values.containsKey("anchor")) {
                if (!(values.get("anchor") instanceof String)) {
                    throw new IllegalArgumentException("Saved collection anchor requires a key");
                }
                anchor = (String) values.get("anchor");
                requireKey(anchor);
            }
            if (values.containsKey("inset")) {
                Object number = values.get("inset");
                if (anchor == null || !(number instanceof Number) || !Double.isFinite(((Number) number).doubleValue())
                        || ((Number) number).doubleValue() != Math.rint(((Number) number).doubleValue())
                        || ((Number) number).doubleValue() < 0 || ((Number) number).doubleValue() > 1023) {
                    throw new IllegalArgumentException("Saved collection inset requires an anchor and integer 0..1023");
                }
                inset = ((Number) number).intValue();
            }
            if (values.containsKey("rows")) {
                for (Map.Entry<String, Object> entry : stateRecord(values.get("rows")).entrySet()) {
                    if (next.snapshot().keys.containsKey(entry.getKey())) {
                        next.publishRowState(entry.getKey(), stateRecord(entry.getValue()));
                    }
                }
            }
        }
        int restoredOffset = offset;
        String restoredAnchor = anchor;
        int restoredInset = inset;
        long preparedRevision = intentRevision;
        return () -> {
            requireOpen();
            if (staging || window != null || model.hasStateReservations() || intentRevision != preparedRevision) {
                throw new IllegalStateException("Collection candidate changed before state restoration");
            }
            model = next;
            intentRevision++;
            requested = null;
            selectionRequested = false;
            requestedSelection = null;
            expansionRequested = false;
            requestedExpansion = Collections.emptySet();
            requestedReveal = null;
            requestedOffset = restoredOffset;
            requestedAnchor = restoredAnchor;
            requestedAnchorInset = restoredInset;
            explicitScroll = true;
            dirty = true;
        };
    }

    public void retainFrom(GuiVirtualCollection previous) {
        prepareStateRestore(previous.captureState()).run();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stateRecord(Object value) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Saved collection state requires detached records");
        }
        for (Object key : ((Map<?, ?>) value).keySet()) {
            if (!(key instanceof String)) {
                throw new IllegalArgumentException("Saved collection state requires string keys");
            }
        }
        return (Map<String, Object>) value;
    }

    private static void requireKey(String key) {
        if (key != null && (key.isEmpty() || key.length() > 255)) {
            throw new IllegalArgumentException("Collection state requires a bounded nonempty key");
        }
    }

    public boolean recordEvent(String key, BooleanSupplier event) {
        requireOpen();
        RecordOrigins origins = actionOrigins.get(key);
        if (origins == null || !origins.record.enabled) {
            return false;
        }
        List<GuiActionOrigin> values = origins.callbackOrigin == null
                ? Collections.singletonList(origins.recordOrigin)
                : Arrays.asList(origins.recordOrigin, origins.callbackOrigin);
        try (GuiActionOrigin.Frame frame = GuiActionOrigin.enter(values)) {
            return event.getAsBoolean();
        }
    }

    public void focusRow(String key) {
        LuaCallbackScope.requireUiStateWrite();
        requireOpen();
        requireKey(key);
        requestedFocus = key;
    }

    public long intentRevision() {
        return intentRevision;
    }

    public long acceptedIntentRevision() {
        return acceptedIntentRevision;
    }

    public int realizedRows() {
        return rows.size();
    }

    public int scrollOffset() {
        return window == null ? 0 : window.offset;
    }

    /** Called by the owning presentation adapter's Java frame sampler. */
    public void sampleRows() {
        if (!closed) {
            for (Entry row : rows.values()) {
                row.row.sample();
            }
        }
    }

    public GuiElement rowElement(String key) {
        Entry row = rows.get(key);
        return row == null ? null : row.row.element();
    }

    public Map<String, Object> rowState(String key) {
        Entry row = rows.get(key);
        return row == null || !row.row.hasLiveState() ? model.rowState(key) : row.row.state();
    }

    @Override
    protected void prepareStructureElement(GuiContext context) {
        if (closed || staging || model.hasStateReservations() || stateAdmission != null && stateAdmission.pending()
                || !GuiPreparationPhase.active()) {
            return;
        }
        int height = visibleThroughParents() && context.intersectsViewport(this) ? getHeight() : 0;
        boolean refresh = false;
        List<Object> rowInputs = new ArrayList<>();
        if (height > 0) {
            try {
                for (Entry entry : new ArrayList<>(rows.values())) {
                    entry.row.update(context, getWidth(), window == null ? rowHeight : window.rowHeight);
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
                overscan, refreshRevision, rowInputs, selectionRequested, requestedSelection, expansionRequested,
                requestedExpansion, requestedReveal, requestedAnchor, requestedAnchorInset, projectionRequested,
                requestedProjection, stateRevision());
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
            if (selectionRequested) {
                GuiCollectionModel.Record selected = next.snapshot().keys.get(requestedSelection);
                next.select(selected != null && selected.enabled ? requestedSelection : null);
            }
            if (expansionRequested) {
                for (String key : new ArrayList<>(next.expanded())) {
                    next.expand(key, false);
                }
                for (String key : requestedExpansion) {
                    if (next.snapshot().keys.containsKey(key) && next.snapshot().branch(key)) {
                        next.expand(key, true);
                    }
                }
            }
            if (projectionRequested) {
                next.project(requestedProjection);
            }
            Map<String, RecordOrigins> nextOrigins = prepareActionOrigins(next.snapshot());
            for (Entry entry : rows.values()) {
                if (entry.row.hasLiveState() && next.snapshot().keys.containsKey(entry.record.key)) {
                    next.publishRowState(entry.record.key, entry.row.state());
                }
            }
            GuiCollectionWindow nextWindow = window == null || explicitScroll
                    ? GuiCollectionWindow.create(next.visible(), rowHeight, height, overscan, requestedOffset)
                    : window.reconcile(next.visible(), rowHeight, height, overscan);
            if (requestedAnchor != null) {
                for (int index = 0; index < next.visible().size(); index++) {
                    if (next.visible().get(index).record.key.equals(requestedAnchor)) {
                        nextWindow = GuiCollectionWindow.create(next.visible(), rowHeight, height, overscan,
                                index * rowHeight + Math.min(rowHeight - 1, requestedAnchorInset));
                        break;
                    }
                }
            }
            if (requestedReveal != null) {
                for (int index = 0; index < next.visible().size(); index++) {
                    if (next.visible().get(index).record.key.equals(requestedReveal)) {
                        nextWindow = GuiCollectionWindow.create(next.visible(), rowHeight, height, overscan,
                                nextWindow.revealOffset(index));
                        break;
                    }
                }
            }
            Map<String, Entry> nextRows = new LinkedHashMap<>();
            Set<GuiElement> elements = Collections.newSetFromMap(new IdentityHashMap<GuiElement, Boolean>());
            for (int index = nextWindow.first; index < nextWindow.lastExclusive; index++) {
                GuiCollectionModel.Row record = next.visible().get(index);
                Entry previous = rows.get(record.record.key);
                Entry entry = previous;
                boolean selected = record.record.key.equals(next.selected());
                boolean expanded = next.expanded().contains(record.record.key);
                if (entry == null || refreshRevision != appliedRefreshRevision || !entry.record.equals(record.record)
                        || entry.depth != record.depth || entry.row.needsRefresh()
                        || window != null && window.rowHeight != rowHeight || entry.context.selected != selected
                        || entry.context.expanded != expanded) {
                    GuiRowFactory.Prepared prepared;
                    Object stateIdentity = new Object();
                    GuiRowFactory.Context rowContext = new GuiRowFactory.Context(context, budget, record.record,
                            record.depth, getWidth(), rowHeight, next.rowState(record.record.key),
                            previous == null ? null : previous.row,
                            values -> publishRowState(record.record.key, stateIdentity, values),
                            rowProposals(record.record.key, stateIdentity), selected, expanded);
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
            acceptedIntentRevision = intentRevision;
            requested = null;
            selectionRequested = false;
            requestedSelection = null;
            expansionRequested = false;
            requestedExpansion = Collections.emptySet();
            requestedReveal = null;
            projectionRequested = false;
            requestedAnchor = null;
            requestedAnchorInset = 0;
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
        Map<String, Object> state = stateAdmission == null
                ? Collections.emptyMap()
                : state(next, next.selected(), next.expanded(), nextWindow.offset, nextWindow.rowHeight);
        try (GuiViewStateAdmission.Proposal proposal = admit(state)) {
            commitAccepted(context, next, nextWindow, nextRows, nextOrigins, staged);
            proposal.commit(state);
        }
    }

    private GuiViewStateAdmission.Proposal admit(Map<String, Object> values) {
        return stateAdmission == null
                ? GuiViewStateAdmission.UNRESTRICTED
                : Objects.requireNonNull(stateAdmission.begin(values), "collection state proposal");
    }

    private void commitAccepted(GuiContext context, GuiCollectionModel next, GuiCollectionWindow nextWindow,
            Map<String, Entry> nextRows, Map<String, RecordOrigins> nextOrigins, List<Staged> staged) {
        List<GuiRowBudget.Reservation> additions = new ArrayList<>();
        List<GuiRowBudget.Reservation> removals = new ArrayList<>();
        List<GuiPaintBudget> paintAdditions = new ArrayList<>();
        List<GuiPaintBudget> paintRemovals = new ArrayList<>();
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
                if (previous.row.paintBudget() != null && previous.row.paintBudget().active()) {
                    paintRemovals.add(previous.row.paintBudget());
                }
            }
        }
        for (Staged candidate : staged) {
            additions.add(candidate.entry.reservation);
            if (candidate.entry.row.paintBudget() != null) {
                paintAdditions.add(candidate.entry.row.paintBudget());
            }
        }
        GuiPaintBudget.validateReplacing(paintAdditions, paintRemovals);
        budget.activateReplacing(additions, removals);
        try {
            GuiPaintBudget.activateReplacing(paintAdditions, paintRemovals);
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
        if (requestedFocus != null) {
            GuiElement target = rowElement(requestedFocus);
            requestedFocus = null;
            if (target != null && target.isFocusable()) {
                context.requestFocus(target);
            }
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
            if (event.isShiftDown()) {
                for (GuiContainer parent = getParent(); parent != null; parent = parent.getParent()) {
                    if (parent instanceof GuiScrollView && ((GuiScrollView) parent).canScrollHorizontally()) {
                        return false;
                    }
                }
            }
            int previous = requestedOffset;
            long target = previous - (long) event.getWheelDelta() / 8;
            return requestScrollOffset((int) Math.max(0, Math.min(Integer.MAX_VALUE, target)));
        }
        return false;
    }

    @Override
    public GraphicsClip revealFocus(GraphicsClip bounds) {
        int before = requestedOffset;
        double delta = bounds.top < getTop()
                ? bounds.top - getTop()
                : bounds.bottom > getBottom() ? Math.min(bounds.top - getTop(), bounds.bottom - getBottom()) : 0;
        if (delta != 0) {
            requestScrollOffset((int) Math.max(0, Math.min(Integer.MAX_VALUE, before + delta)));
        }
        int shift = before - requestedOffset;
        return new GraphicsClip(Math.max(getLeft(), Math.min(getRight(), bounds.left)),
                Math.max(getTop(), Math.min(getBottom(), bounds.top + shift)),
                Math.max(getLeft(), Math.min(getRight(), bounds.right)),
                Math.max(getTop(), Math.min(getBottom(), bounds.bottom + shift)));
    }

    public int contentHeight() {
        return model.visible().size() * (window == null ? rowHeight : window.rowHeight);
    }

    public int requestedScrollOffset() {
        return requestedOffset;
    }

    public boolean requestScrollOffset(int offset) {
        LuaCallbackScope.requireUiStateWrite();
        requireOpen();
        int maximum = Math.max(0, model.visible().size() * rowHeight - Math.max(1, getHeight()));
        int next = Math.max(0, Math.min(maximum, offset));
        if (next == requestedOffset) {
            return false;
        }
        Map<String, Object> prospective = state(model, selection(), expansion(), next, rowHeight);
        try (GuiViewStateAdmission.Proposal proposal = admit(prospective)) {
            setScrollOffset(next);
            proposal.commit(captureState());
            return true;
        } catch (GuiViewStateAdmission.Refusal refused) {
            return false;
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Collection view has closed");
        }
    }

    private void publishRowState(String key, Object identity, Map<String, ?> values) {
        requireStateOwner(key, identity);
        if (stateAdmission == null) {
            model.publishRowState(key, values);
            return;
        }
        try (GuiCollectionModel.StateReservation local = model.reserveRowState(key, values);
                GuiViewStateAdmission.Proposal ancestor = admit(captureState())) {
            ancestor.commit(captureState());
            local.commit();
        }
    }

    private GuiViewStateAdmission.Proposal reserveRowState(String key, Object identity, Map<String, Object> values) {
        requireStateOwner(key, identity);
        GuiCollectionModel.StateReservation reservation = model.reserveRowState(key, values);
        GuiViewStateAdmission.Proposal ancestor;
        try {
            ancestor = stateAdmission == null ? GuiViewStateAdmission.UNRESTRICTED : admit(captureState());
        } catch (RuntimeException | Error failure) {
            reservation.close();
            throw failure;
        }
        return new GuiViewStateAdmission.Proposal() {
            public void commit(Map<String, Object> accepted) {
                publishRowState(key, identity, accepted);
                ancestor.commit(stateAdmission == null ? Collections.emptyMap() : captureState());
                reservation.commit();
            }

            public void close() {
                try {
                    ancestor.close();
                } finally {
                    reservation.close();
                }
            }
        };
    }

    private Object stateRevision() {
        return Collections.unmodifiableList(
                Arrays.asList(model.revision(), stateAdmission == null ? null : stateAdmission.revision()));
    }

    private GuiViewStateAdmission rowProposals(String key, Object identity) {
        return new GuiViewStateAdmission() {
            public Proposal begin(Map<String, Object> values) {
                return reserveRowState(key, identity, values);
            }

            public Object revision() {
                return stateRevision();
            }

            public boolean pending() {
                return model.hasStateReservations() || stateAdmission != null && stateAdmission.pending();
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
            requestedSelection = null;
            requestedExpansion = Collections.emptySet();
            requestedReveal = null;
            stateAdmission = null;
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
            mount.setEnabled(record.enabled);
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
