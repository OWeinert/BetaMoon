package betamoon.gui.collection;

import betamoon.gui.action.GuiActionOrigin;
import betamoon.gui.component.GuiComponentSchema;
import betamoon.gui.screen.GuiActionDefinition;
import betamoon.gui.screen.GuiActionDefinition.Scope;
import betamoon.gui.screen.GuiActionQueue;
import betamoon.gui.screen.GuiActionTicket;
import betamoon.gui.screen.GuiActionTicket.Status;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiPreparationPhase;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.GuiSharedControlsTest;
import betamoon.gui.framework.HeadlessGuiFrames;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.widget.GuiStyledView;
import betamoon.interop.runtime.NativeContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

/**
 * Realized rows are actual shared-scene elements, not just a window
 * calculation.
 */
public final class GuiVirtualCollectionTest {
    public static void main(String[] arguments) {
        Factory factory = new Factory();
        GuiRowBudget budget = new GuiRowBudget();
        List<Throwable> failures = new ArrayList<>();
        GuiVirtualCollection view = new GuiVirtualCollection(budget, factory, failures::add);
        List<GuiCollectionModel.Record> records = new ArrayList<>();
        for (int index = 0; index < 4096; index++) {
            records.add(record(index, "row" + index));
        }
        GuiScene scene = new GuiScene();
        scene.updateEnvironment(null, null, 320, 200, 320, 200);
        view.setModel(GuiCollectionModel.prepare(records));
        scene.setContent(view);
        prepare(scene);
        require(view.realizedRows() == 12 && view.getChildren().size() == 12 && factory.builds == 12
                && budget.liveElements() == 24, "4096 records created more than the visible/overscan shared graph");
        TestRow first = (TestRow) view.rowElement("row0");
        first.edits = 7;
        for (int frame = 0; frame < 10; frame++) {
            prepare(scene);
        }
        require(factory.builds == 12 && failures.isEmpty(), "An idle collection polled its row factory");

        List<GuiCollectionModel.Record> invalid = new ArrayList<>(records);
        invalid.set(0, record(0, "changed"));
        invalid.set(1, record(1, "reject"));
        view.setModel(GuiCollectionModel.prepare(invalid));
        prepare(scene);
        require(failures.size() == 1 && view.rowElement("row0") == first && !first.closed
                && view.model().keys.get("row0").label.equals("row0") && budget.reservedElements() == 24,
                "A later failed row partly retired the old model/views or retained candidate reservations");
        int rejectedBuilds = factory.builds;
        view.setModel(GuiCollectionModel.prepare(invalid));
        prepare(scene);
        require(factory.builds == rejectedBuilds, "An unchanged failed row candidate retried every update");
        view.setModel(GuiCollectionModel.prepare(records));
        prepare(scene);
        require(view.rowElement("row0") == first && !first.closed, "Recovery replaced an unchanged keyed row");

        scene.getContext().requestFocus(first);
        scene.mousePressed(3, 3, 0);
        Collections.rotate(records, 100);
        view.setModel(GuiCollectionModel.prepare(records));
        prepare(scene);
        require(view.scrollOffset() == 2000 && view.rowElement("row0") == first && scene.getFocusedElement() == first
                && first.cancelled == 1, "Keyed reorder lost its anchor/focus or retained a moved-row gesture");
        require(scene.mouseReleased(3, 3, 0) && first.released == 0,
                "A reordered gesture produced a stale release action");

        scene.mousePressed(3, 3, 0);
        view.setScrollOffset(2001);
        prepare(scene);
        prepare(scene);
        require(first.cancelled == 2 && scene.getFocusedElement() == first && scene.mouseReleased(3, 3, 0)
                && first.released == 0, "A partial-row scroll kept a moved control's gesture or lost keyboard focus");
        view.setScrollOffset(2000);
        prepare(scene);

        view.setScrollOffset(4003);
        try (NativeContext callback = NativeContext.denied("row-test.lua")) {
            prepare(scene);
            require(view.scrollOffset() == 2000 && view.rowElement("row0") == first,
                    "A native callback realized rows before a fresh Java boundary");
        }
        prepare(scene);
        require(view.realizedRows() == 15 && view.rowElement("row0") == null && first.closed,
                "Scrolling retained an offscreen graph or realized the whole model");
        view.setScrollOffset(2000);
        prepare(scene);
        TestRow restored = (TestRow) view.rowElement("row0");
        require(restored != first && restored.edits == 7, "Row state followed a recycled view instead of its key");

        records.removeIf(record -> record.key.equals("row0"));
        view.setModel(GuiCollectionModel.prepare(records));
        prepare(scene);
        require(restored.closed && view.rowElement("row0") == null, "Removing a key retained its live row");
        records.add(100, record(0, "row0"));
        view.setModel(GuiCollectionModel.prepare(records));
        prepare(scene);
        require(((TestRow) view.rowElement("row0")).edits == 0, "Reinserted key recovered disposed local state");
        view.setHidden(true);
        prepare(scene);
        require(view.realizedRows() == 0 && budget.reservedElements() == 0,
                "A hidden viewport retained realized rows or their reservations");
        view.setHidden(false);
        prepare(scene);
        require(view.realizedRows() > 0 && failures.size() == 1,
                "Showing a viewport did not restore bounded realization");
        TestRow beforeRefresh = (TestRow) view.rowElement("row0");
        beforeRefresh.edits = 3;
        int beforeBuilds = factory.builds;
        int beforeCount = view.realizedRows();
        view.refreshRows();
        prepare(scene);
        require(beforeRefresh.closed && view.rowElement("row0") != beforeRefresh
                && ((TestRow) view.rowElement("row0")).edits == 3 && factory.builds == beforeBuilds + beforeCount,
                "Explicit refresh did not rebuild the complete visible/overscan candidate while retaining keyed state");
        view.close();
        view.close();
        require(budget.liveRows() == 0 && budget.reservedElements() == 0 && view.getChildren().isEmpty(),
                "Closing the host retained rows, input or reservations");
        budget.close();
        nestedRealization();
        cleanupFailures();
        dormantControlRows();
        actionOrigins();
        System.out.println("Virtual collections passed: 4096 records, bounded shared elements, Java-only idle replay, "
                + "atomic row rejection, scoped realization, keyed drafts/focus/capture and disposal.");
    }

    private static GuiCollectionModel.Record record(int index, String label) {
        return new GuiCollectionModel.Record("row" + index, null, label, true, Collections.emptyMap());
    }

    private static void actionOrigins() {
        GuiActionQueue queue = new GuiActionQueue(() -> 0);
        ActionActivation activation = new ActionActivation();
        GuiActionDefinition action = new GuiActionDefinition("apply", Scope.WORLD, GuiComponentSchema.EMPTY,
                new ZeroArgFunction() {
                    public LuaValue call() {
                        return NIL;
                    }
                });
        GuiRowBudget budget = new GuiRowBudget();
        List<Throwable> failures = new ArrayList<>();
        Factory factory = new Factory();
        factory.onPrepare = context -> {
            try (GuiActionOrigin.Frame source = GuiActionOrigin.enter(context.actionOrigins())) {
                require("origin_expired".equals(queue.submit(activation, action, Collections.emptyMap()).reason),
                        "An unaccepted row candidate submitted deferred intent");
            }
        };
        GuiVirtualCollection view = new GuiVirtualCollection(budget, factory, failures::add);
        List<GuiCollectionModel.Record> records = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            records.add(record(index, "row" + index));
        }
        view.setModel(GuiCollectionModel.prepare(records));
        GuiScene scene = new GuiScene();
        scene.updateEnvironment(null, null, 320, 200, 320, 200);
        scene.setContent(view);
        prepare(scene);
        GuiRowFactory.Context original = factory.contexts.get("row0");
        GuiActionTicket survives = submit(queue, activation, action, original);
        Collections.rotate(records, 5);
        view.setModel(GuiCollectionModel.prepare(records));
        prepare(scene);
        view.setScrollOffset(600);
        prepare(scene);
        require(view.rowElement("row0") == null, "Origin fixture failed to unrealize its row");
        queue.dispatch(Scope.WORLD);
        require(survives.status() == Status.SUCCEEDED, "Reorder/offscreen disposal cancelled accepted keyed intent");
        view.setScrollOffset(100);
        prepare(scene);
        GuiActionTicket removed = submit(queue, activation, action, factory.contexts.get("row0"));
        records.removeIf(record -> record.key.equals("row0"));
        view.setModel(GuiCollectionModel.prepare(records));
        prepare(scene);
        records.add(5, record(0, "row0"));
        view.setModel(GuiCollectionModel.prepare(records));
        prepare(scene);
        queue.dispatch(Scope.CLIENT);
        require(removed.status() == Status.CANCELLED && "origin_expired".equals(removed.reason()),
                "Removing/reinserting an identical key revived world intent or waited for a world boundary");
        GuiRowFactory.Context accepted = factory.contexts.get("row0");
        GuiActionTicket unchanged = submit(queue, activation, action, accepted);
        List<GuiCollectionModel.Record> invalid = new ArrayList<>(records);
        invalid.set(5, record(0, "changed"));
        invalid.set(6, record(1, "reject"));
        view.setModel(GuiCollectionModel.prepare(invalid));
        prepare(scene);
        queue.dispatch(Scope.WORLD);
        require(unchanged.status() == Status.SUCCEEDED && failures.size() == 1,
                "A rejected later candidate expired the accepted record's deferred intent");
        view.setModel(GuiCollectionModel.prepare(records));
        prepare(scene);
        GuiActionTicket changed = submit(queue, activation, action, accepted);
        records.set(5, record(0, "changed"));
        view.setModel(GuiCollectionModel.prepare(records));
        prepare(scene);
        queue.dispatch(Scope.CLIENT);
        require(changed.status() == Status.CANCELLED, "Accepted record replacement retained stale deferred intent");
        GuiActionTicket oldCode = submit(queue, activation, action, factory.contexts.get("row0"));
        TestRow row = (TestRow) view.rowElement("row0");
        row.refresh = true;
        factory.revision = new Object();
        prepare(scene);
        queue.dispatch(Scope.CLIENT);
        require(oldCode.status() == Status.CANCELLED, "Accepted callback replacement retained old callback intent");
        GuiActionTicket refreshed = submit(queue, activation, action, factory.contexts.get("row0"));
        view.setScrollOffset(600);
        prepare(scene);
        view.refreshRows();
        prepare(scene);
        queue.dispatch(Scope.CLIENT);
        require(refreshed.status() == Status.CANCELLED, "Explicit refresh failed to expire dormant callback intent");
        view.setScrollOffset(100);
        prepare(scene);
        GuiActionTicket closed = submit(queue, activation, action, factory.contexts.get("row0"));
        view.close();
        queue.dispatch(Scope.CLIENT);
        require(closed.status() == Status.CANCELLED && budget.reservedElements() == 0,
                "Closing the owning collection retained deferred world intent/resources");
        budget.close();
    }

    private static GuiActionTicket submit(GuiActionQueue queue, ActionActivation activation, GuiActionDefinition action,
            GuiRowFactory.Context context) {
        try (GuiActionOrigin.Frame source = GuiActionOrigin.enter(context.actionOrigins())) {
            GuiActionQueue.Submission submission = queue.submit(activation, action, Collections.emptyMap());
            require(submission.ticket != null, "Accepted row intent was refused: " + submission.reason);
            return submission.ticket;
        }
    }

    private static final class ActionActivation implements GuiActionQueue.Activation {
        public String owner() {
            return "row-origin-fixture.lua";
        }

        public long revision() {
            return 0;
        }

        public boolean active() {
            return true;
        }

        public String unavailable(Scope scope) {
            return null;
        }

        public Object execute(GuiActionDefinition action, Map<String, Object> payload) {
            return null;
        }

        public void failed(String action, Throwable failure) {
            throw new AssertionError(failure);
        }
    }

    private static void prepare(GuiScene scene) {
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
    }

    private static void nestedRealization() {
        GuiRowBudget budget = new GuiRowBudget();
        List<Throwable> failures = new ArrayList<>();
        int[] builds = {0};
        GuiRowFactory factory = new GuiRowFactory() {
            public Prepared prepare(Context context) {
                builds[0]++;
                GuiVirtualCollection nested = new GuiVirtualCollection(budget, this, failures::add);
                nested.setModel(GuiCollectionModel.prepare(Collections.singletonList(record(0, "nested"))));
                Row row = new Row() {
                    public GuiElement element() {
                        return nested;
                    }

                    public Map<String, Object> state() {
                        return Collections.emptyMap();
                    }

                    public void close() {
                        nested.close();
                    }
                };
                return prepared(row, budget.reserve(2));
            }
        };
        GuiVirtualCollection view = new GuiVirtualCollection(budget, factory, failures::add);
        view.setModel(GuiCollectionModel.prepare(Collections.singletonList(record(0, "nested"))));
        GuiScene scene = new GuiScene();
        scene.updateEnvironment(null, null, 320, 200, 320, 200);
        scene.setContent(view);
        for (int update = 0; update < 40; update++) {
            prepare(scene);
        }
        require(builds[0] == 32 && budget.liveRows() == 32 && failures.size() == 1,
                "Nested row factories exceeded the shared graph depth or retried a rejected boundary");
        view.close();
        require(budget.reservedElements() == 0, "Nested rows retained resources after outer close");
        budget.close();
    }

    private static void cleanupFailures() {
        for (boolean fatal : new boolean[]{false, true}) {
            GuiRowBudget budget = new GuiRowBudget();
            List<Throwable> failures = new ArrayList<>();
            Factory factory = new Factory();
            GuiVirtualCollection view = new GuiVirtualCollection(budget, factory, failures::add);
            view.setModel(GuiCollectionModel.prepare(Arrays.asList(record(0, "first"), record(1, "second"))));
            GuiScene scene = new GuiScene();
            scene.updateEnvironment(null, null, 320, 200, 320, 200);
            scene.setContent(view);
            prepare(scene);
            TestRow first = (TestRow) view.rowElement("row0");
            TestRow second = (TestRow) view.rowElement("row1");
            first.closeFailure = fatal ? new ThreadDeath() : new AssertionError("Cleanup fixture");
            boolean rethrown = false;
            try {
                view.close();
            } catch (ThreadDeath expected) {
                rethrown = true;
            }
            require(first.closed && second.closed && budget.reservedElements() == 0 && rethrown == fatal
                    && failures.size() == (fatal ? 0 : 1),
                    "One failed row cleanup skipped later resources or swallowed a fatal error");
            view.close();
            budget.close();
        }
    }

    private static void dormantControlRows() {
        GuiRowBudget budget = new GuiRowBudget();
        List<Throwable> failures = new ArrayList<>();
        Map<String, DraftRow> built = new LinkedHashMap<>();
        GuiRowFactory factory = context -> {
            GuiPresentationTree tree = GuiSharedControlsTest.document(GuiStyleSheet.MINIMAL, new ArrayList<>(),
                    LuaValue.NIL);
            GuiRowBudget.Reservation reservation = null;
            try {
                if (context.state.containsKey("views")) {
                    tree.restoreViewState(viewState(context.state.get("views")));
                }
                reservation = budget.reserve(8);
                DraftRow row = new DraftRow(tree);
                built.put(context.record.key, row);
                GuiRowFactory.Prepared candidate = prepared(row, reservation);
                return new GuiRowFactory.Prepared() {
                    public GuiRowFactory.Row candidate() {
                        return candidate.candidate();
                    }

                    public GuiRowBudget.Reservation reservation() {
                        return candidate.reservation();
                    }

                    public GuiRowFactory.Row commit() {
                        tree.activate();
                        return candidate.commit();
                    }

                    public void close() {
                        candidate.close();
                    }
                };
            } catch (RuntimeException | Error failure) {
                tree.close();
                if (reservation != null) {
                    reservation.close();
                }
                throw failure;
            }
        };
        GuiVirtualCollection view = new GuiVirtualCollection(budget, factory, failures::add);
        view.setMetrics(200, 2);
        List<GuiCollectionModel.Record> records = new ArrayList<>();
        for (int index = 0; index < 4096; index++) {
            records.add(record(index, "row" + index));
        }
        view.setModel(GuiCollectionModel.prepare(records));
        GuiScene scene = new GuiScene();
        scene.updateEnvironment(null, null, 320, 200, 320, 200);
        scene.setContent(view);
        prepare(scene);
        DraftRow original = built.get("row0");
        GuiStyledView textbox = (GuiStyledView) original.tree.element("textbox");
        textbox.editor().selectAll();
        textbox.editor().insert("saved local text");
        textbox.editor().commit(true);
        textbox.editor().move(1, false);
        textbox.editor().move(5, true);
        textbox.textOffset(1);
        GuiElement checkbox = original.tree.element("checkbox");
        scene.mousePressed(checkbox.getLeft() + 2, checkbox.getTop() + 2, 0);
        scene.mouseReleased(checkbox.getLeft() + 2, checkbox.getTop() + 2, 0);

        view.setScrollOffset(4003);
        prepare(scene);
        require(original.closed && view.rowElement("row0") == null && view.realizedRows() <= 6,
                "An offscreen row retained its live document instead of detached controls");
        view.setScrollOffset(0);
        prepare(scene);
        DraftRow restored = built.get("row0");
        GuiStyledView text = (GuiStyledView) restored.tree.element("textbox");
        require(restored != original && text.text().equals("saved local text") && text.editor().selectionStart() == 1
                && text.editor().selectionEnd() == 5 && text.textOffset() == 1
                && ((GuiStyledView) restored.tree.element("checkbox")).selected() && failures.isEmpty(),
                "Recreated keyed rows lost real editor drafts/selection/viewport or local checkbox state");
        view.close();
        require(budget.reservedElements() == 0 && restored.closed, "Dormant row graphs retained resources after close");
        budget.close();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> viewState(Object value) {
        return (Map<String, Object>) value;
    }

    private static final class DraftRow implements GuiRowFactory.Row {
        final GuiPresentationTree tree;
        boolean closed;

        DraftRow(GuiPresentationTree tree) {
            this.tree = tree;
        }

        public GuiElement element() {
            return tree.root();
        }

        public Map<String, Object> state() {
            return Collections.singletonMap("views", tree.captureViewState());
        }

        public void update(GuiContext context, int width, int height) {
            tree.update();
        }

        public void close() {
            if (!closed) {
                closed = true;
                tree.close();
            }
        }
    }

    private static GuiRowFactory.Prepared prepared(GuiRowFactory.Row row, GuiRowBudget.Reservation reservation) {
        return new GuiRowFactory.Prepared() {
            boolean committed;
            public GuiRowFactory.Row candidate() {
                return row;
            }

            public GuiRowBudget.Reservation reservation() {
                return reservation;
            }

            public GuiRowFactory.Row commit() {
                committed = true;
                return row;
            }

            public void close() {
                if (!committed) {
                    try {
                        row.close();
                    } finally {
                        reservation.close();
                    }
                }
            }
        };
    }

    private static final class Factory implements GuiRowFactory {
        int builds;
        Object revision;
        final Map<String, Context> contexts = new LinkedHashMap<>();
        Consumer<Context> onPrepare = context -> {
        };
        public Prepared prepare(Context context) {
            require(GuiPreparationPhase.active(), "Row factory did not enter Java preparation");
            builds++;
            onPrepare.accept(context);
            if (context.record.label.equals("reject")) {
                throw new IllegalArgumentException("Rejected row fixture");
            }
            TestRow row = new TestRow();
            row.revision = revision;
            contexts.put(context.record.key, context);
            row.edits = ((Number) context.state.getOrDefault("edits", 0)).intValue();
            GuiRowBudget.Reservation reservation = context.budget.reserve(2);
            return prepared(row, reservation);
        }
    }

    private static final class TestRow extends GuiElement implements GuiRowFactory.Row {
        int edits;
        int cancelled;
        int released;
        boolean closed;
        Error closeFailure;
        boolean refresh;
        Object revision;
        TestRow() {
            setFocusable(true);
        }

        public GuiElement element() {
            return this;
        }

        public Map<String, Object> state() {
            return Collections.singletonMap("edits", edits);
        }

        public boolean needsRefresh() {
            return refresh;
        }

        public Object actionRevision() {
            return revision;
        }

        protected Size measureElement(GuiContext context, Constraints constraints) {
            return constraints.constrain(new Size(320, 20));
        }

        protected boolean onInput(GuiContext context, GuiInputEvent event) {
            if (event.getType() == GuiInputEvent.Type.POINTER_DOWN) {
                context.capturePointer(this);
                return true;
            }
            if (event.getType() == GuiInputEvent.Type.POINTER_CANCEL) {
                cancelled++;
                return true;
            }
            if (event.getType() == GuiInputEvent.Type.POINTER_UP) {
                released++;
                return true;
            }
            return false;
        }

        public void close() {
            if (!closed) {
                closed = true;
                setVisible(false);
                if (closeFailure != null) {
                    throw closeFailure;
                }
            }
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
