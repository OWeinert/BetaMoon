package betamoon.gui.collection;

import betamoon.gui.component.GuiComponentSchema;
import betamoon.gui.component.GuiComponentStateStore;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPropertyDescriptor;
import betamoon.gui.presentation.GuiPropertyDescriptor.Kind;
import betamoon.gui.presentation.GuiPropertyDescriptor.Stage;
import betamoon.gui.presentation.GuiPublishedData;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;

/**
 * Admission rejects typed state before source publication, cache changes or
 * observer delivery.
 */
public final class GuiRowStateAdmissionTest {
    public static void main(String[] arguments) {
        sourceAdmission();
        stateAdmissionReentry();
        typedTransactions();
        realizedOwnership();
        System.out.println("Row state admission passed: detached preflight, event rollback, aggregate budgets, "
                + "observer isolation, live row identity, dormant restoration and stale publisher refusal.");
    }

    private static void sourceAdmission() {
        GuiPublishedData source = new GuiPublishedData();
        source.publish(Collections.singletonMap("value", "old"));
        Map<String, Object> accepted = source.snapshot();
        long revision = source.revision();
        expect(() -> source.publish(Collections.singletonMap("value", "new"), candidate -> {
            require(source.snapshot() == accepted, "Admission observed a partly committed source");
            throw new IllegalArgumentException("Rejected admission fixture");
        }));
        expect(() -> source.publish(Collections.singletonMap("value", "new"), candidate -> {
            source.publish(Collections.singletonMap("value", "nested"));
        }));
        require(source.snapshot() == accepted && source.revision() == revision,
                "Rejected/reentrant admission changed source values or revision");
        List<String> value = new ArrayList<>(Arrays.asList("detached"));
        source.publish(Collections.singletonMap("value", value), candidate -> value.set(0, "changed"));
        require(((List<?>) source.snapshot().get("value")).get(0).equals("detached"),
                "Admission retained the caller's mutable candidate values");
        source.publish(Collections.singletonMap("value", Arrays.asList("detached")), candidate -> {
            throw new AssertionError("No-op state invoked admission");
        });
    }

    private static GuiComponentSchema schema() {
        return new GuiComponentSchema(Collections.singletonMap("text", new GuiComponentSchema.Field(
                GuiPropertyDescriptor.value("text", Kind.STRING, "", Stage.SEMANTICS), false)));
    }

    private static void stateAdmissionReentry() {
        for (String operation : Arrays.asList("write", "event", "close")) {
            GuiComponentStateStore state = new GuiComponentStateStore();
            state.create("root", schema(), null);
            state.seal();
            Map<String, Object> accepted = state.snapshot();
            state.admitWith(candidate -> {
                if (operation.equals("write")) {
                    state.set("root", Collections.singletonMap("text", "nested"));
                } else if (operation.equals("event")) {
                    state.beginEvent();
                } else {
                    state.close();
                }
            });
            expect(() -> state.set("root", Collections.singletonMap("text", "proposed")));
            require(state.snapshot() == accepted && state.get("root", "text").equals(""),
                    "Reentrant admission mutated, closed or left a transaction in its own state store");
            state.beginEvent();
            state.finishEvent(false);
            state.close();
        }
    }

    private static void typedTransactions() {
        GuiCollectionModel model = new GuiCollectionModel();
        List<GuiCollectionModel.Record> records = new ArrayList<>();
        for (int index = 0; index < 501; index++) {
            records.add(record(index, "row" + index));
        }
        model.publish(records);
        GuiComponentStateStore state = new GuiComponentStateStore();
        expect(() -> state.admitWith(values -> {
        }));
        state.create("left", schema(), null);
        state.create("right", schema(), null);
        state.seal();
        model.publishRowState("row0", state.snapshot());
        state.admitWith(values -> model.publishRowState("row0", values));
        expect(() -> state.admitWith(values -> {
        }));
        List<String> changes = new ArrayList<>();
        state.observe(new GuiPresentationHost.Observer() {
            public void changed(String source, Object previous, Object value) {
                changes.add(source);
                require(model.rowState("row0").equals(state.snapshot()),
                        "Observers saw mismatched typed and cached row state");
            }

            public void event(String type, Supplier<LuaTable> payload) {
            }
        });
        state.set("left", Collections.singletonMap("text", "saved"));
        Map<String, Object> stable = state.snapshot();
        long stableRevision = model.revision();
        expect(() -> state.set("left", Collections.singletonMap("text", repeat(20000))));
        require(state.snapshot() == stable && model.revision() == stableRevision && changes.size() == 1
                && state.get("left", "text").equals("saved"), "Rejected single-row edit partly published");

        state.beginEvent();
        state.set("left", Collections.singletonMap("text", repeat(9000)));
        state.set("right", Collections.singletonMap("text", repeat(9000)));
        expect(() -> state.finishEvent(true));
        require(state.snapshot() == stable && model.revision() == stableRevision && changes.size() == 1
                && state.get("left", "text").equals("saved") && state.get("right", "text").equals(""),
                "Aggregate event rejection committed some fields, cache values or observers");
        state.beginEvent();
        state.set("left", Collections.singletonMap("text", "first"));
        state.set("right", Collections.singletonMap("text", "second"));
        state.finishEvent(true);
        require(changes.size() == 3 && model.rowState("row0").equals(state.snapshot()),
                "Valid event state did not commit as one complete cache snapshot");

        String payload = repeat(10000);
        int filled = 1;
        while (filled < records.size()) {
            try {
                model.publishRowState("row" + filled, Collections.singletonMap("pad", payload));
                filled++;
            } catch (IllegalArgumentException full) {
                break;
            }
        }
        require(filled > 400 && filled < records.size(), "Fixture did not reach the aggregate dormant budget");
        stable = state.snapshot();
        stableRevision = model.revision();
        expect(() -> state.set("left", Collections.singletonMap("text", payload)));
        require(state.snapshot() == stable && model.revision() == stableRevision && changes.size() == 3,
                "A live edit bypassed other rows' retained state budget");
        model.publishRowState("row1", Collections.emptyMap());
        state.set("left", Collections.singletonMap("text", payload));
        require(changes.size() == 4 && model.rowState("row0").equals(state.snapshot()),
                "Released dormant storage did not restore live state admission");
        state.close();
        state.close();
    }

    private static void realizedOwnership() {
        GuiRowBudget budget = new GuiRowBudget();
        Map<String, StateRow> latest = new LinkedHashMap<>();
        List<Throwable> failures = new ArrayList<>();
        GuiVirtualCollection view = new GuiVirtualCollection(budget, context -> {
            if (context.previous != null) {
                StateRow previous = (StateRow) context.previous;
                expect(() -> previous.context.publishState(Collections.emptyMap()));
            }
            StateRow row = new StateRow(context);
            latest.put(context.record.key, row);
            expect(() -> context.publishState(row.state()));
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
        view.setMetrics(20, 0);
        List<GuiCollectionModel.Record> records = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            records.add(record(index, "row" + index));
        }
        view.setModel(GuiCollectionModel.prepare(records));
        GuiScene scene = new GuiScene();
        scene.updateEnvironment(null, null, 320, 100, 320, 100);
        scene.setContent(view);
        scene.prepare();
        StateRow first = latest.get("row0");
        try (LuaCallbackScope readOnly = LuaCallbackScope.binding()) {
            expect(() -> first.context.publishState(Collections.emptyMap()));
        }
        first.state.set("root", Collections.singletonMap("text", "keyed"));
        view.setScrollOffset(200);
        scene.prepare();
        require(view.rowState("row0").get("root::text").equals("keyed"),
                "A live typed state edit was not preserved when its row unmounted");
        expect(() -> first.context.publishState(Collections.singletonMap("root::text", "stale")));
        view.setScrollOffset(0);
        scene.prepare();
        StateRow restored = latest.get("row0");
        require(restored != first && restored.state.get("root", "text").equals("keyed"),
                "Returning row did not restore the admitted typed snapshot");
        expect(() -> first.context.publishState(Collections.emptyMap()));
        records.set(0, record(0, "changed"));
        view.setModel(GuiCollectionModel.prepare(records));
        scene.prepare();
        require(latest.get("row0") != restored, "Changed record did not replace its realized state owner");
        expect(() -> restored.context.publishState(Collections.emptyMap()));
        StateRow current = latest.get("row0");
        view.close();
        expect(() -> current.context.publishState(Collections.emptyMap()));
        require(budget.reservedElements() == 0 && failures.isEmpty(), "State ownership checks leaked a row or failed");
        budget.close();
    }

    private static final class StateRow extends GuiElement implements GuiRowFactory.Row {
        final GuiComponentStateStore state = new GuiComponentStateStore();
        final GuiRowFactory.Context context;
        StateRow(GuiRowFactory.Context context) {
            this.context = context;
            state.restore(context.state);
            state.create("root", schema(), null);
            state.seal();
            state.admitWith(context::publishState);
        }

        public GuiElement element() {
            return this;
        }

        public Map<String, Object> state() {
            return state.snapshot();
        }

        public void close() {
            state.close();
        }
    }

    private static GuiCollectionModel.Record record(int index, String label) {
        return new GuiCollectionModel.Record("row" + index, null, label, true, Collections.emptyMap());
    }

    private static String repeat(int length) {
        char[] text = new char[length];
        Arrays.fill(text, 's');
        return new String(text);
    }

    private static void expect(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | LuaError expected) {
            return;
        }
        throw new AssertionError("Invalid state admission was accepted");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
