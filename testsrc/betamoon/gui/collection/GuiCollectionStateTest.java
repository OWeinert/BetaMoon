package betamoon.gui.collection;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.HeadlessGuiFrames;
import betamoon.gui.presentation.GuiViewStateAdmission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keyed selection/expansion snapshots and containing-row admission wrap real
 * realization transactions.
 */
public final class GuiCollectionStateTest {
    public static void main(String[] arguments) {
        keyedState();
        ancestorAdmission();
        restoredAnchor();
        System.out.println("Collection state passed: keyed selection/expansion/reveal, detached restoration, "
                + "remove/reinsert pruning and containing-row edit/proposal/structure admission.");
    }

    private static void keyedState() {
        Fixture fixture = new Fixture();
        List<GuiCollectionModel.Record> records = Arrays.asList(record("a", null, "A"), record("b", "a", "B"),
                record("c", null, "C"), record("d", null, "D"));
        fixture.view.setModel(GuiCollectionModel.prepare(records));
        fixture.view.setSelection("b");
        fixture.view.setExpansion(Collections.singleton("a"));
        fixture.view.reveal("b");
        fixture.prepare();
        Row b = fixture.rows.get("b");
        require(b.context.selected && b.context.depth == 1 && fixture.rows.get("a").context.expanded
                && fixture.view.selection().equals("b"),
                "Keyed selection/expansion did not reach realized row contexts");
        b.publish(Collections.singletonMap("draft", "saved"));
        fixture.scene.getContext().requestFocus(b);
        fixture.view.setSelection("a");
        fixture.prepare();
        require(fixture.scene.getFocusedElement() == fixture.view.rowElement("b"),
                "Selection-context refresh lost compatible local focus");
        fixture.view.setSelection("b");
        fixture.view.setExpansion(Collections.emptySet());
        fixture.prepare();
        require(fixture.view.rowElement("b") == null && fixture.view.selection().equals("b")
                && fixture.view.rowState("b").get("draft").equals("saved"),
                "Collapsing a branch discarded its selected key or dormant row state");
        Map<String, Object> saved = fixture.view.captureState();
        Fixture restored = new Fixture();
        restored.view.setModel(GuiCollectionModel
                .prepare(Arrays.asList(records.get(2), records.get(0), records.get(1), records.get(3))));
        Runnable staleRestore = restored.view.prepareStateRestore(saved);
        restored.view.setSelection("a");
        try {
            staleRestore.run();
            throw new AssertionError("A stale restoration discarded newer collection intent");
        } catch (IllegalStateException expected) {
        }
        restored.view.prepareStateRestore(saved).run();
        restored.view.setExpansion(Collections.singleton("a"));
        restored.view.reveal("b");
        restored.prepare();
        require(restored.view.selection().equals("b") && restored.rows.get("b").values.get("draft").equals("saved"),
                "A fresh/reordered collection did not restore keyed dormant state");
        restored.view
                .setModel(GuiCollectionModel.prepare(Arrays.asList(records.get(0), records.get(2), records.get(3))));
        restored.prepare();
        restored.view.setModel(GuiCollectionModel.prepare(records));
        restored.view.setExpansion(Collections.singleton("a"));
        restored.prepare();
        require(restored.view.selection() == null && restored.view.rowState("b").isEmpty(),
                "Reinserting a removed key revived selection or disposed local row state");
        require(fixture.failures.isEmpty() && restored.failures.isEmpty(), "Valid keyed collection state failed");
        restored.close();
        fixture.close();
    }

    private static void restoredAnchor() {
        try (Fixture original = new Fixture(); Fixture restored = new Fixture(); Fixture removed = new Fixture()) {
            List<GuiCollectionModel.Record> records = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                records.add(record("r" + i, null, "Row " + i));
            }
            original.view.setModel(GuiCollectionModel.prepare(records));
            original.prepare();
            original.view.setScrollOffset(45);
            original.prepare();
            Map<String, Object> state = original.view.captureState();
            List<GuiCollectionModel.Record> reordered = new ArrayList<>(records);
            GuiCollectionModel.Record anchor = reordered.remove(2);
            reordered.add(5, anchor);
            restored.view.setModel(GuiCollectionModel.prepare(reordered));
            restored.view.setMetrics(30, 0);
            restored.view.prepareStateRestore(state).run();
            restored.prepare();
            require(restored.view.scrollOffset() == 155
                    && restored.view.rowElement("r2").getTop() == restored.view.getTop() - 5,
                    "Refresh restored an index instead of its visible key/inset after reorder and metric change");
            records.remove(2);
            removed.view.setModel(GuiCollectionModel.prepare(records));
            removed.view.prepareStateRestore(state).run();
            removed.prepare();
            require(removed.view.scrollOffset() == 45,
                    "Removed restore anchor did not use its clamped numeric fallback");
        }
    }

    private static void ancestorAdmission() {
        Fixture fixture = new Fixture();
        fixture.view
                .setModel(GuiCollectionModel.prepare(Arrays.asList(record("a", null, "A"), record("b", null, "B"))));
        fixture.prepare();
        GuiCollectionModel parent = new GuiCollectionModel();
        parent.publish(Collections.singletonList(record("owner", null, "Owner")));
        String[] padding = {text(14000)};
        parent.publishRowState("owner", parentState(padding[0], fixture.view.captureState()));
        fixture.view.admitStateWith(new GuiViewStateAdmission() {
            public Proposal begin(Map<String, Object> prospective) {
                GuiCollectionModel.StateReservation reservation = parent.reserveRowState("owner",
                        parentState(padding[0], prospective));
                return new Proposal() {
                    public void commit(Map<String, Object> accepted) {
                        parent.publishRowState("owner", parentState(padding[0], accepted));
                        reservation.commit();
                    }

                    public void close() {
                        reservation.close();
                    }
                };
            }

            public Object revision() {
                return parent.revision();
            }

            public boolean pending() {
                return parent.hasStateReservations();
            }
        });
        Row a = fixture.rows.get("a");
        Map<String, Object> previous = fixture.view.captureState();
        expect(() -> a.publish(Collections.singletonMap("draft", text(3000))));
        require(fixture.view.captureState().equals(previous)
                && parent.rowState("owner").get("collection").equals(previous),
                "A containing-row refusal left accepted child/ancestor state changed");
        expect(() -> a.context.reserveState(Collections.singletonMap("draft", text(3000))));
        require(fixture.view.captureState().equals(previous) && !parent.hasStateReservations(),
                "A refused nested proposal leaked state or ancestor reservations");
        Map<String, Object> accepted = Collections.singletonMap("draft", "accepted");
        try (GuiViewStateAdmission.Proposal proposal = a.context.reserveState(accepted)) {
            require(parent.hasStateReservations(), "A child edit did not retain containing-row rollback capacity");
            proposal.commit(accepted);
            a.values = accepted;
        }
        require(parent.rowState("owner").get("collection").equals(fixture.view.captureState())
                && !parent.hasStateReservations(), "Accepted nested state did not commit/release its containing row");
        int beforePending = fixture.builds;
        try (GuiCollectionModel.StateReservation proposal = parent.reserveRowState("owner", parent.rowState("owner"))) {
            fixture.view.refreshRows();
            fixture.prepare();
            require(fixture.builds == beforePending, "A pending ancestor edit allowed descendant row realization");
        }
        fixture.prepare();
        Row current = (Row) fixture.view.rowElement("a");
        fixture.view.setModel(
                GuiCollectionModel.prepare(Arrays.asList(record("a", null, "large"), record("b", null, "B"))));
        fixture.prepare();
        require(fixture.failures.size() == 1 && fixture.view.rowElement("a") == current && !current.closed
                && fixture.view.model().keys.get("a").label.equals("A") && !parent.hasStateReservations(),
                "A structurally refused nested row retired accepted views/model or leaked an ancestor proposal");
        int beforeRetry = fixture.builds;
        fixture.prepare();
        require(fixture.builds == beforeRetry, "An unchanged ancestor-budget rejection retried factories on idle");
        padding[0] = "";
        parent.publishRowState("owner", parentState(padding[0], fixture.view.captureState()));
        fixture.prepare();
        require(fixture.view.model().keys.get("a").label.equals("large") && fixture.builds > beforeRetry
                && fixture.failures.size() == 1,
                "Freeing containing-row cache capacity did not retry its rejected descendant");
        fixture.close();
    }

    private static Map<String, Object> parentState(String padding, Map<String, Object> collection) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("padding", padding);
        values.put("collection", collection);
        return values;
    }

    private static final class Fixture implements AutoCloseable {
        final GuiRowBudget budget = new GuiRowBudget();
        final Map<String, Row> rows = new LinkedHashMap<>();
        final List<Throwable> failures = new ArrayList<>();
        int builds;
        final GuiVirtualCollection view = new GuiVirtualCollection(budget, context -> {
            builds++;
            Row row = new Row(context);
            rows.put(context.record.key, row);
            GuiRowBudget.Reservation reservation = budget.reserve(2);
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
                        row.close();
                        reservation.close();
                    }
                }
            };
        }, failures::add);
        final GuiScene scene = new GuiScene();

        Fixture() {
            view.setMetrics(20, 0);
            scene.updateEnvironment(null, null, 100, 40, 100, 40);
            scene.setContent(view);
        }

        void prepare() {
            scene.prepare();
            HeadlessGuiFrames.layout(scene);
        }

        public void close() {
            view.close();
            require(budget.reservedElements() == 0, "Collection state fixture retained row reservations");
            budget.close();
        }
    }

    private static final class Row extends GuiElement implements GuiRowFactory.Row {
        final GuiRowFactory.Context context;
        Map<String, Object> values;
        boolean closed;

        Row(GuiRowFactory.Context context) {
            this.context = context;
            values = context.record.label.equals("large")
                    ? Collections.singletonMap("draft", text(3000))
                    : context.state;
            setFocusable(true);
        }

        void publish(Map<String, Object> changes) {
            context.publishState(changes);
            values = changes;
        }

        public GuiElement element() {
            return this;
        }

        public Map<String, Object> state() {
            return values;
        }

        public GuiElement compatibleFocus(GuiRowFactory.Row previous, GuiElement focused) {
            return this;
        }

        protected Size measureElement(GuiContext context, Constraints constraints) {
            return constraints.constrain(new Size(100, 20));
        }

        public void close() {
            closed = true;
            setVisible(false);
        }
    }

    private static GuiCollectionModel.Record record(String key, String parent, String label) {
        return new GuiCollectionModel.Record(key, parent, label, true, Collections.emptyMap());
    }

    private static String text(int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, 'x');
        return new String(chars);
    }

    private static void expect(Runnable operation) {
        try {
            operation.run();
        } catch (GuiViewStateAdmission.Refusal expected) {
            return;
        }
        throw new AssertionError("An edit beyond the containing-row budget was accepted");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
