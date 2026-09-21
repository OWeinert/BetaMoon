package betamoon.gui;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Insets;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiLayouts;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiDialog;
import betamoon.gui.widget.GuiScrollView;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptMod;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.lwjgl.input.Keyboard;

/**
 * Headless regression checks for retained GUI layout, routing, and ordering.
 */
public final class GuiCollectionsTest {
    private GuiCollectionsTest() {
    }

    public static void main(String[] arguments) throws Exception {
        verifyInputRouting();
        verifyStackLayout();
        verifyFocusAndPointerCapture();
        verifyScrollbarRouting();
        verifyDialogGeometry();
        verifyScriptOrdering();
        verifyUpdateNoticeLayout();
        System.out.println("GUI collections passed: routing, layout, focus, capture and script ordering.");
    }

    private static void verifyUpdateNoticeLayout() {
        for (int width : new int[]{320, 427, 640, 854, 1280}) {
            for (int height : new int[]{240, 360, 480, 720}) {
                Rect notice = GuiUpdateNotice.noticeBounds(width, height);
                Rect vanilla = Rect.fromPositionAndSize(width / 2 - 100, height / 4 + 48, 200, 104);
                require(notice.intersect(vanilla).isEmpty(), "Update notice must not intercept vanilla clicks");
                require(notice.getLeft() == 10 && notice.getBottom() == height - 46,
                        "Notice aligned six units above Scripts");
                require(notice.getTop() >= 0 && notice.getRight() <= width, "Notice inside viewport");
            }
        }
        GuiUpdateNotice hidden = new GuiUpdateNotice(null, "0.6.0");
        require(!hidden.isVisible(), "No update means no visible card");
    }

    private static void verifyInputRouting() {
        List<String> calls = new ArrayList<String>();
        RecordingContainer overlay = new RecordingContainer("parent", calls, true);
        RecordingElement first = overlay.add(new RecordingElement("first", calls, true));
        RecordingElement middle = overlay.add(new RecordingElement("middle", calls, true));
        RecordingElement last = overlay.add(new RecordingElement("last", calls, false));
        GuiScene scene = createScene(overlay, 100, 40);

        scene.render(0, 0, 0.0F);
        expectCalls(calls, "first", "middle", "last");
        require(scene.mousePressed(10, 10, 0), "An unhandled top child must bubble to its parent");
        expectCalls(calls, "last", "parent");

        last.setVisible(false);
        scene.render(0, 0, 0.0F);
        expectCalls(calls, "first", "middle");
        require(scene.mousePressed(10, 10, 0), "The next visible top child should receive the click");
        expectCalls(calls, "middle");

        overlay.remove(middle);
        require(scene.mousePressed(10, 10, 0), "Removing a child must reveal the element below it");
        expectCalls(calls, "first");
        overlay.clear();
        require(scene.mousePressed(10, 10, 0), "An empty interactive container should receive its own click");
        expectCalls(calls, "parent");
    }

    private static void verifyStackLayout() {
        GuiLayouts.Stack panel = new GuiLayouts.Stack(GuiLayouts.Axis.VERTICAL);
        panel.setPadding(new Insets(5));
        panel.setGap(4);
        RecordingElement fixed = new RecordingElement("fixed", new ArrayList<String>(), false);
        RecordingElement fill = new RecordingElement("fill", new ArrayList<String>(), false);
        panel.addItem(fixed, GuiLayouts.Length.fixed(20), GuiLayouts.Length.fixed(30));
        panel.addItem(fill, GuiLayouts.Length.fill(), GuiLayouts.Length.fill());
        GuiScene scene = createScene(panel, 100, 100);

        scene.render(0, 0, 0.0F);
        expectBounds(fixed, 5, 5, 25, 35);
        expectBounds(fill, 5, 39, 95, 95);

        panel.setAxis(GuiLayouts.Axis.HORIZONTAL);
        scene.render(0, 0, 0.0F);
        expectBounds(fixed, 5, 5, 25, 35);
        expectBounds(fill, 29, 5, 95, 95);

        panel.remove(fixed);
        scene.render(0, 0, 0.0F);
        expectBounds(fill, 5, 5, 95, 95);
    }

    private static void verifyFocusAndPointerCapture() {
        GuiLayouts.Stack row = new GuiLayouts.Stack(GuiLayouts.Axis.HORIZONTAL);
        RecordingElement fixed = new RecordingElement("fixed", new ArrayList<String>(), true);
        RecordingElement fill = new RecordingElement("fill", new ArrayList<String>(), true);
        fixed.captureOnPress = true;
        row.addItem(fixed, GuiLayouts.Length.fixed(20), GuiLayouts.Length.fill());
        row.addItem(fill, GuiLayouts.Length.fill(), GuiLayouts.Length.fill());
        GuiScene scene = createScene(row, 100, 30);
        scene.render(0, 0, 0.0F);

        expectBounds(fixed, 0, 0, 20, 30);
        expectBounds(fill, 20, 0, 100, 30);
        require(fixed.contains(19, 29) && !fixed.contains(20, 29), "Bounds must use half-open edges");
        require(scene.mousePressed(10, 10, 0), "Pointer press should be consumed");
        require(scene.getFocusedElement() == fixed, "Pointer press should focus its target");
        require(!scene.mouseReleased(11, 11, -1), "Mouse movement must not release pointer capture");
        require(!scene.mouseReleased(11, 11, 1), "Another button must not release pointer capture");
        require(scene.mouseDragged(150, 80, true), "Captured drag should route outside element bounds");
        require(scene.mouseReleased(150, 80, 0), "Captured release should route outside element bounds");
        require(fixed.dragCalls == 1 && fixed.releaseCalls == 1, "Pointer capture must own drag and release");
        require(scene.keyTyped('\t', Keyboard.KEY_TAB, false), "Tab should advance retained focus");
        require(scene.getFocusedElement() == fill, "Tab should focus the next element");
    }

    private static void verifyScrollbarRouting() throws Exception {
        RecordingElement content = new RecordingElement("content", new ArrayList<String>(), true);
        GuiScrollView scrollView = new GuiScrollView(content, GuiScrollView.Policy.VERTICAL);
        scrollView.setContentSize(100, 200);
        GuiScene scene = createScene(scrollView, 100, 100);
        layoutScene(scene);

        require(scene.mousePressed(98, 10, 0), "Scrollbar thumb should intercept content input");
        require(scene.mouseDragged(98, 45, true), "Scrollbar thumb should retain its drag");
        require(scrollView.getScrollY() > 0, "Dragging the scrollbar should update its position");
        require(scene.mouseReleased(98, 45, 0), "Scrollbar release should be consumed");
    }

    private static void verifyDialogGeometry() throws Exception {
        GuiDialog dialog = new GuiDialog("Geometry");
        RecordingElement body = new RecordingElement("body", new ArrayList<String>(), false);
        GuiButton first = new GuiButton("First", null);
        GuiButton second = new GuiButton("Second", null);
        dialog.setPanelSize(360, 200, 0);
        dialog.setBody(body);
        dialog.addFooterButton(first);
        dialog.addFooterButton(second);
        GuiScene scene = createScene(dialog, 400, 300);
        layoutScene(scene);

        require(dialog.getPanelBounds().equals(new Rect(20, 50, 380, 250)), "Dialog panel must remain centered");
        expectBounds(body, 30, 82, 370, 212);
        expectBounds(first, 30, 220, 195, 240);
        expectBounds(second, 205, 220, 370, 240);
    }

    private static void verifyScriptOrdering() throws Exception {
        LuaScriptRegistry.clear();
        try {
            ScriptMod alpha = LuaScriptRegistry.registerFile("alpha.lua");
            ScriptMod failed = LuaScriptRegistry.registerFile("z-failed.lua");
            ScriptMod beta = LuaScriptRegistry.registerFile("Beta.lua");
            LuaScriptRegistry.markFailedByFile(failed.getSourceFileName(), "test failure");
            List<ScriptMod> entries = Arrays.asList(beta, alpha, failed);
            Method sort = GuiScreenScripts.class.getDeclaredMethod("getSortedEntries", List.class);
            sort.setAccessible(true);
            Object sorted = sort.invoke(null, entries);
            require(Arrays.asList(failed, alpha, beta).equals(sorted),
                    "Failed scripts must appear first, followed by case-insensitive names");
            require(entries.equals(Arrays.asList(beta, alpha, failed)), "Display sorting must not mutate its input");
            require(sort.invoke(null, new Object[]{null}) == null, "Null list behavior must be preserved");
        } finally {
            LuaScriptRegistry.clear();
        }
    }

    private static GuiScene createScene(GuiElement content, int width, int height) {
        GuiScene scene = new GuiScene();
        scene.setContent(content);
        scene.updateEnvironment(null, null, width, height, width, height);
        return scene;
    }

    private static void layoutScene(GuiScene scene) throws Exception {
        Method layout = GuiScene.class.getDeclaredMethod("ensureLayout");
        layout.setAccessible(true);
        layout.invoke(scene);
    }

    private static void expectBounds(GuiElement element, int left, int top, int right, int bottom) {
        require(element.getBounds().equals(new Rect(left, top, right, bottom)),
                "Unexpected bounds: " + element.getBounds());
    }

    private static void expectCalls(List<String> calls, String... expected) {
        require(calls.equals(Arrays.asList(expected)), "Unexpected dispatch order: " + calls);
        calls.clear();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static class RecordingElement extends GuiElement {
        private final String name;
        private final List<String> calls;
        private final boolean handlesInput;
        private boolean captureOnPress;
        private int dragCalls;
        private int releaseCalls;

        private RecordingElement(String name, List<String> calls, boolean handlesInput) {
            this.name = name;
            this.calls = calls;
            this.handlesInput = handlesInput;
            setFocusable(true);
        }

        @Override
        protected void renderElement(GuiContext context) {
            calls.add(name);
        }

        @Override
        protected boolean onInput(GuiContext context, GuiInputEvent event) {
            if (event.getType() == GuiInputEvent.Type.POINTER_DOWN) {
                calls.add(name);
                if (captureOnPress) {
                    capturePointer(context);
                }
                return handlesInput;
            }
            if (event.getType() == GuiInputEvent.Type.POINTER_DRAG) {
                dragCalls++;
                return handlesInput;
            }
            if (event.getType() == GuiInputEvent.Type.POINTER_UP) {
                releaseCalls++;
                return handlesInput;
            }
            return handlesInput;
        }
    }

    private static final class RecordingContainer extends GuiContainer {
        private final String name;
        private final List<String> calls;
        private final boolean handlesInput;

        private RecordingContainer(String name, List<String> calls, boolean handlesInput) {
            this.name = name;
            this.calls = calls;
            this.handlesInput = handlesInput;
        }

        @Override
        protected boolean onInput(GuiContext context, GuiInputEvent event) {
            if (event.getType() == GuiInputEvent.Type.POINTER_DOWN) {
                calls.add(name);
            }
            return handlesInput;
        }
    }
}
