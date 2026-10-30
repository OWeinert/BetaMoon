package betamoon.gui.collection;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiPreparationPhase;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.HeadlessGuiFrames;
import betamoon.interop.runtime.NativeContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        System.out.println("Virtual collections passed: 4096 records, bounded shared elements, Java-only idle replay, "
                + "atomic row rejection, scoped realization, keyed drafts/focus/capture and disposal.");
    }

    private static GuiCollectionModel.Record record(int index, String label) {
        return new GuiCollectionModel.Record("row" + index, null, label, true, Collections.emptyMap());
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
        public Prepared prepare(Context context) {
            require(GuiPreparationPhase.active(), "Row factory did not enter Java preparation");
            builds++;
            if (context.record.label.equals("reject")) {
                throw new IllegalArgumentException("Rejected row fixture");
            }
            TestRow row = new TestRow();
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
        TestRow() {
            setFocusable(true);
        }

        public GuiElement element() {
            return this;
        }

        public Map<String, Object> state() {
            return Collections.singletonMap("edits", edits);
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
