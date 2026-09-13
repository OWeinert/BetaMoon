package betamoon.gui;

import betamoon.gui.api.component.GuiComponentBase;
import betamoon.gui.api.component.GuiContainer;
import betamoon.gui.api.layout.EnumAxis;
import betamoon.gui.api.layout.GuiStackPanel;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptMod;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Headless regression checks for GUI collection ordering and layout behavior.
 */
public final class GuiCollectionsTest {
    private GuiCollectionsTest() {
    }

    public static void main(String[] arguments) throws Exception {
        verifyInputDispatch();
        verifyStackLayout();
        verifyScriptOrdering();
        System.out.println("GUI collections passed: input dispatch, stack layout and script ordering.");
    }

    private static void verifyInputDispatch() {
        List<String> calls = new ArrayList<>();
        GuiContainer container = new GuiContainer();
        RecordingComponent first = new RecordingComponent("first", calls, true);
        RecordingComponent middle = new RecordingComponent("middle", calls, true);
        RecordingComponent last = new RecordingComponent("last", calls, false);
        container.addChildren(new RecordingComponent[]{first, middle, last});

        container.layout(320, 240);
        expectCalls(calls, "first", "middle", "last");
        require(container.mouseClicked(0, 0, 0), "Click should be consumed");
        expectCalls(calls, "last", "middle");
        require(container.mouseReleased(0, 0, 0), "Release should be consumed");
        expectCalls(calls, "last", "middle");
        require(container.mouseScrolled(0, 0, 1, false), "Scroll should be consumed");
        expectCalls(calls, "last", "middle");
        require(container.keyTyped('a', 0), "Key should be consumed");
        expectCalls(calls, "last", "middle");
        require(container.mouseDragged(0, 0, true), "Drag should report handled");
        expectCalls(calls, "last", "middle", "first");

        container.removeChild(middle);
        require(container.mouseClicked(0, 0, 0), "Click should reach remaining child");
        expectCalls(calls, "last", "first");
        container.clear();
        require(!container.mouseDragged(0, 0, false), "Cleared container should not handle input");
        expectCalls(calls);
    }

    private static void verifyStackLayout() {
        List<String> calls = new ArrayList<>();
        RecordingComponent fixed = new RecordingComponent("fixed", calls, false);
        RecordingComponent fill = new RecordingComponent("fill", calls, false);
        GuiStackPanel panel = new GuiStackPanel(EnumAxis.VERTICAL);
        panel.setBounds(10, 20, 110, 120);
        panel.setPadding(5);
        panel.setSpacing(4);
        panel.addItem(fixed, 20, 30);
        panel.addItem(fill, GuiStackPanel.FILL, GuiStackPanel.FILL);

        panel.layout(320, 240);
        fixed.expectBounds(15, 25, 35, 55);
        fill.expectBounds(15, 59, 105, 115);
        expectCalls(calls, "fixed", "fill");

        panel.setAxis(EnumAxis.HORIZONTAL);
        panel.layout(320, 240);
        fixed.expectBounds(15, 25, 35, 55);
        fill.expectBounds(39, 25, 105, 115);
        expectCalls(calls, "fixed", "fill");

        panel.removeChild(fixed);
        panel.layout(320, 240);
        fill.expectBounds(15, 25, 105, 115);
        expectCalls(calls, "fill");
        panel.clear();
        panel.layout(320, 240);
        expectCalls(calls);
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

    private static void expectCalls(List<String> calls, String... expected) {
        require(calls.equals(Arrays.asList(expected)), "Unexpected dispatch order: " + calls);
        calls.clear();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingComponent extends GuiComponentBase {
        private final String name;
        private final List<String> calls;
        private final boolean handlesInput;

        private RecordingComponent(String name, List<String> calls, boolean handlesInput) {
            this.name = name;
            this.calls = calls;
            this.handlesInput = handlesInput;
        }

        @Override
        public void layout(int screenWidth, int screenHeight) {
            calls.add(name);
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            return recordInput();
        }

        @Override
        public boolean mouseReleased(int mouseX, int mouseY, int button) {
            return recordInput();
        }

        @Override
        public boolean mouseDragged(int mouseX, int mouseY, boolean mouseDown) {
            return recordInput();
        }

        @Override
        public boolean mouseScrolled(int mouseX, int mouseY, int wheelDelta, boolean shiftDown) {
            return recordInput();
        }

        @Override
        public boolean keyTyped(char typedChar, int keyCode) {
            return recordInput();
        }

        private boolean recordInput() {
            calls.add(name);
            return handlesInput;
        }

        private void expectBounds(int expectedLeft, int expectedTop, int expectedRight, int expectedBottom) {
            require(left == expectedLeft && top == expectedTop && right == expectedRight && bottom == expectedBottom,
                    "Unexpected bounds for " + name + ": " + left + ", " + top + ", " + right + ", " + bottom);
        }
    }
}
