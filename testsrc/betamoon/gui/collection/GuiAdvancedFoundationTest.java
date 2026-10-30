package betamoon.gui.collection;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Insets;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiGridLayout;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.HeadlessGuiFrames;
import betamoon.gui.widget.GuiScrollView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Data and geometry checks; row-factory/Lua integration is a separate P6 gate.
 */
public final class GuiAdvancedFoundationTest {
    public static void main(String[] arguments) {
        atomicModels();
        virtualWindows();
        gridGeometry();
        nestedScroll();
        System.out.println("Advanced foundations passed: atomic keyed trees/state, bounded 4096-record windows, "
                + "reorder anchors, responsive grids and exhausted nested scrolling.");
    }

    private static GuiCollectionModel.Record row(String key, String parent) {
        return new GuiCollectionModel.Record(key, parent, key, true, Collections.emptyMap());
    }

    private static void atomicModels() {
        GuiCollectionModel model = new GuiCollectionModel();
        model.publish(Arrays.asList(row("child", "root"), row("root", null), row("other", null)));
        require(model.visible().size() == 2, "Collapsed trees realized descendants");
        model.expand("root", true);
        Set<String> expansion = model.expanded();
        require(model.visible().get(1).record.key.equals("child") && model.visible().get(1).depth == 1,
                "Parent-later records or stable sibling order failed");
        model.select("child");
        model.publishRowState("child", Collections.singletonMap("draft", "saved"));
        long revision = model.revision();
        GuiCollectionModel.Snapshot previous = model.snapshot();
        expect(() -> model.publish(Arrays.asList(row("duplicate", null), row("duplicate", null))));
        expect(() -> model.publish(Collections.singletonList(row("missing", "absent"))));
        expect(() -> model.publish(Arrays.asList(row("a", "b"), row("b", "a"))));
        List<GuiCollectionModel.Record> deep = new ArrayList<>();
        for (int index = 0; index < 33; index++) {
            deep.add(row("depth" + index, index == 0 ? null : "depth" + (index - 1)));
        }
        expect(() -> model.publish(deep));
        require(GuiCollectionModel.prepare(deep.subList(0, 32)).records.size() == 32,
                "Valid maximum-depth tree was rejected");
        require(model.snapshot() == previous && model.revision() == revision && model.selected().equals("child")
                && model.rowState("child").get("draft").equals("saved"),
                "Invalid candidate partly changed the current model");
        model.expand("root", false);
        require(expansion.contains("root") && model.selected().equals("child") && model.visible().size() == 2,
                "Collapse mutated a prior snapshot or destroyed keyed selection/state");
        model.publish(Arrays.asList(row("other", null), row("root", null), row("child", "root")));
        require(model.rowState("child").get("draft").equals("saved"), "Reorder reassigned keyed state");
        model.publish(Arrays.asList(row("other", null), row("root", null)));
        require(model.selected() == null, "Removed selected key was silently reassigned");
        expect(() -> model.rowState("child"));
        model.publish(Arrays.asList(row("root", null), row("child", "root")));
        require(model.rowState("child").isEmpty(), "A removed/reinserted key recovered disposed state");
        model.select("root");
        model.publish(
                Arrays.asList(new GuiCollectionModel.Record("root", null, "Disabled", false, Collections.emptyMap()),
                        row("child", "root")));
        require(model.selected() == null && !model.select("root"), "Disabled selection was retained or accepted");
        long stable = model.revision();
        require(!model.publishRowState("root", Collections.emptyMap()) && model.revision() == stable,
                "Empty row state allocated or invalidated an unchanged model");
        expect(() -> model.publishRowState("root", Collections.singletonMap("borrowed", new Object())));
        char[] large = new char[17000];
        Arrays.fill(large, 'x');
        expect(() -> model.publishRowState("root", Collections.singletonMap("oversized", new String(large))));
        require(model.revision() == stable && model.rowState("root").isEmpty(), "Invalid row state partly published");
        GuiCollectionModel.Snapshot candidate = GuiCollectionModel
                .prepare(Collections.singletonList(row("candidate", null)));
        require(candidate.visible(Collections.emptySet()).get(0).record.key.equals("candidate")
                && model.snapshot().keys.containsKey("root"), "Candidate preflight published model ownership");
    }

    private static void virtualWindows() {
        GuiCollectionModel model = new GuiCollectionModel();
        List<GuiCollectionModel.Record> records = new ArrayList<>();
        for (int index = 0; index < 4096; index++) {
            records.add(row("row" + index, null));
        }
        model.publish(records);
        GuiCollectionWindow window = GuiCollectionWindow.create(model.visible(), 20, 200, 2, 2003);
        require(window.first == 98 && window.lastExclusive == 113 && window.realized() == 15
                && window.anchor.equals("row100") && window.rowTop(100) == -3,
                "4096-record viewport did not bound visible/overscan realization");
        Collections.rotate(records, 37);
        model.publish(records);
        window = window.reconcile(model.visible(), 20, 200, 2);
        require(window.anchor.equals("row100") && window.offset == 2743, "Reorder lost the visible key/inset anchor");
        GuiCollectionWindow hidden = GuiCollectionWindow.create(model.visible(), 20, 0, 8, window.offset);
        require(hidden.realized() == 0 && hidden.anchor.equals(window.anchor),
                "A hidden viewport realized overscan or lost its anchor");
        GuiCollectionWindow end = GuiCollectionWindow.create(model.visible(), 20, 200, 2, Integer.MAX_VALUE);
        require(end.offset == 81720 && end.lastExclusive == 4096 && end.realized() <= 12,
                "Collection scroll exceeded the model extent");
        int reveal = window.revealOffset(0);
        require(reveal == 0 && window.revealOffset(4095) == 81720, "Keyboard reveal offset was outside its target row");
        expect(() -> GuiCollectionWindow.create(model.visible(), 1, 16384, 8, 0));
        expect(() -> GuiCollectionWindow.create(model.visible(), 0, 200, 2, 0));
        expect(() -> end.rowTop(0));
        expect(() -> end.reconcile(null, 20, 200, 2));
        List<GuiCollectionModel.Record> excessive = new ArrayList<>(records);
        excessive.add(row("extra", null));
        expect(() -> model.publish(excessive));
        require(model.snapshot().records.size() == 4096, "Oversized model replaced the usable collection");
        char[] text = new char[10000];
        Arrays.fill(text, 's');
        String draft = new String(text);
        int accepted = 0;
        long beforeFailure = 0;
        while (accepted < 1024) {
            beforeFailure = model.revision();
            try {
                model.publishRowState("row" + accepted, Collections.singletonMap("draft", draft));
                accepted++;
            } catch (IllegalArgumentException full) {
                break;
            }
        }
        require(accepted >= 400 && accepted <= 420 && model.revision() == beforeFailure
                && model.rowState("row" + accepted).isEmpty(),
                "Aggregate row-state budget partly published an invalid update");
        model.publish(Collections.emptyList());
        model.publish(records);
        require(model.rowState("row0").isEmpty()
                && model.publishRowState("row0", Collections.singletonMap("draft", draft)),
                "Removed state retained its byte reservation");
        records.removeIf(record -> record.key.equals("row100"));
        model.publish(records);
        window = window.reconcile(model.visible(), 20, 200, 2);
        require(window.anchor.equals("row101") && window.offset == 2743,
                "Removed scroll anchor did not fall back to its clamped visible index");
    }

    private static GuiElement box(int height) {
        return new GuiElement() {
            protected Size measureElement(GuiContext context, Constraints constraints) {
                return constraints.constrain(new Size(40, height));
            }
        };
    }

    private static void gridGeometry() {
        GuiGridLayout grid = new GuiGridLayout(0, 60, 5);
        GuiElement a = grid.add(box(10));
        GuiElement b = grid.add(box(20));
        GuiElement c = grid.add(box(30));
        GuiElement d = grid.add(box(5));
        GuiScene scene = new GuiScene();
        scene.updateEnvironment(null, null, 205, 120, 205, 120);
        scene.setContent(grid);
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
        require(a.getWidth() == 65 && c.getRight() == 205 && d.getTop() == 35,
                "Responsive grid measured rows or distributed columns incorrectly");
        scene.updateEnvironment(null, null, 130, 120, 130, 120);
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
        require(a.getWidth() == 63 && b.getLeft() == 68 && b.getRight() == 130 && c.getTop() == 25,
                "Responsive resize lost remainder pixels or reflow order");
        b.setVisible(false);
        c.setHidden(true);
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
        require(c.getLeft() == 68 && d.getTop() == 35, "Grid confused hidden and collapsed layout participation");
        expect(() -> grid.setMetrics(2, 60, 5));
        expect(() -> grid.setPadding(new Insets(Integer.MAX_VALUE)));
        require(grid.columnsFor(130) == 2, "Invalid layout metrics partly changed responsive behavior");
        GuiGridLayout conflict = new GuiGridLayout(2, 0, 100);
        conflict.add(box(10));
        expect(() -> conflict.measure(scene.getContext(), Constraints.upTo(20, 100)));
    }

    private static void nestedScroll() {
        GuiScrollView inner = new GuiScrollView(box(200), GuiScrollView.Policy.VERTICAL);
        inner.setContentSize(50, 200);
        GuiContainer content = new GuiContainer() {
            protected void arrangeChildren(GuiContext context) {
                inner.arrange(context, Rect.fromPositionAndSize(getLeft() + 10, getTop() + 10, 50, 50));
            }
        };
        content.add(inner);
        GuiScrollView outer = new GuiScrollView(content, GuiScrollView.Policy.VERTICAL);
        outer.setContentSize(100, 240);
        GuiScene scene = new GuiScene();
        scene.updateEnvironment(null, null, 100, 100, 100, 100);
        scene.setContent(outer);
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
        require(scene.mouseScrolled(20, 20, -120, false) && inner.getScrollY() == 15 && outer.getScrollY() == 0,
                "Nested viewport did not handle its own available scroll");
        inner.setScrollPosition(0, 150);
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
        require(scene.mouseScrolled(20, 20, -120, false) && outer.getScrollY() == 15,
                "Exhausted inner viewport swallowed its ancestor's wheel gesture");
        inner.setScrollPosition(0, 0);
        outer.setScrollPosition(0, 0);
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
        require(!scene.mouseScrolled(20, 20, 120, false), "Exhausted nested viewports consumed an unmoving gesture");
        require(scene.mouseScrolled(20, 20, Integer.MIN_VALUE, false) && inner.getScrollY() == 150,
                "Extreme wheel input overflowed or reversed its scroll direction");
    }

    private static void expect(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Invalid advanced UI candidate was accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
