package betamoon.gui;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.widget.GuiScrollView;
import betamoon.luamodloader.LuaScriptErrors.ScriptIssue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.lwjgl.input.Keyboard;

/** Retained entries share clipboard behavior, focus and hit testing in both issue views. */
final class GuiScriptIssueEntries extends GuiContainer {
    static final int ENTRY_GAP = 6;
    private final GuiScriptIssueActions actions;
    private List<ScriptIssue> issues = Collections.emptyList();

    GuiScriptIssueEntries(GuiScriptIssueActions actions) {
        this.actions = actions;
    }

    void setIssues(List<ScriptIssue> value) {
        List<ScriptIssue> ordered = GuiScriptIssueText.ordered(value);
        if (issues.equals(ordered)) {
            return;
        }
        issues = new ArrayList<>(ordered);
        clear();
        for (ScriptIssue issue : issues) {
            add(new IssueEntry(issue));
        }
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        return new Size(constraints.getMaxWidth(),
                GuiIssueLayout.prepare(context.getFont(), issues, Math.max(1, constraints.getMaxWidth() - 4),
                        ENTRY_GAP).getHeight());
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        int y = getTop();
        for (GuiElement child : getChildren()) {
            int width = Math.max(1, getWidth() - 4);
            int height = child.measure(context, Constraints.upTo(width, Integer.MAX_VALUE)).getHeight();
            child.arrange(context, Rect.fromPositionAndSize(getLeft(), y, width, height));
            y += height + ENTRY_GAP;
        }
    }

    private final class IssueEntry extends GuiElement {
        private final ScriptIssue issue;
        private GuiIssueLayout.Entry layout;
        private boolean pressed;
        private int startX;
        private int startY;

        private IssueEntry(ScriptIssue issue) {
            this.issue = issue;
            setFocusable(true);
        }

        @Override
        protected Size measureElement(GuiContext context, Constraints constraints) {
            layout = GuiIssueLayout.Entry.prepare(context.getFont(), issue, constraints.getMaxWidth());
            return new Size(constraints.getMaxWidth(), layout.getHeight());
        }

        @Override
        protected void onFocusChanged(GuiContext context, boolean focused) {
            if (focused) {
                GuiContainer parent = getParent();
                while (parent != null && !(parent instanceof GuiScrollView)) {
                    parent = parent.getParent();
                }
                if (parent != null) {
                    ((GuiScrollView) parent).reveal(getBounds());
                }
            }
        }

        @Override
        protected void renderElement(GuiContext context) {
            if (isHovered(context) || isFocused()) {
                context.getRenderer().drawRect(getBounds(), context.getRenderer().getTheme().listHoverBackground);
            }
            if (isFocused()) {
                context.getRenderer().drawHorizontalLine(getLeft(), getRight(), getTop(), 0xFFFFFFA0);
                context.getRenderer().drawHorizontalLine(getLeft(), getRight(), getBottom() - 1, 0xFFFFFFA0);
                context.getRenderer().drawVerticalLine(getTop(), getBottom(), getLeft(), 0xFFFFFFA0);
                context.getRenderer().drawVerticalLine(getTop(), getBottom(), getRight() - 1, 0xFFFFFFA0);
            }
            if (layout != null) {
                layout.draw(context, getLeft(), getTop(), getWidth(), issue.isWarning()
                        ? context.getRenderer().getTheme().textWarning : context.getRenderer().getTheme().textError);
            }
            if (isHovered(context) || isFocused() && !actions.feedback().isEmpty()) {
                String feedback = actions.feedback();
                if (actions.failed()) {
                    List<String> lines = new ArrayList<>();
                    lines.add(feedback);
                    lines.addAll(Arrays.asList(actions.detail().split("\\n")));
                    context.showTooltip(lines);
                } else {
                    context.showTooltip(feedback.isEmpty() ? "Click or press Enter to copy" : feedback);
                }
            }
        }

        @Override
        protected boolean onInput(GuiContext context, GuiInputEvent event) {
            switch (event.getType()) {
                case POINTER_DOWN:
                    if (event.getButton() != 0) {
                        return false;
                    }
                    startX = event.getMouseX();
                    startY = event.getMouseY();
                    pressed = true;
                    requestFocus(context);
                    capturePointer(context);
                    return true;
                case SCROLL:
                    pressed = false;
                    return false;
                case POINTER_DRAG:
                    if (Math.abs(event.getMouseX() - startX) > 4 || Math.abs(event.getMouseY() - startY) > 4) {
                        pressed = false;
                    }
                    return pressed;
                case POINTER_UP:
                    if (event.getButton() != 0) {
                        return false;
                    }
                    boolean copy = pressed && contains(event.getMouseX(), event.getMouseY()) && insideViewport(event);
                    pressed = false;
                    releasePointer(context);
                    if (copy) {
                        actions.copy(issue);
                    }
                    return true;
                case KEY_TYPED:
                    if (isFocused() && (event.getKeyCode() == Keyboard.KEY_RETURN
                            || event.getKeyCode() == Keyboard.KEY_NUMPADENTER || event.getKeyCode() == Keyboard.KEY_SPACE)) {
                        actions.copy(issue);
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        }

        private boolean insideViewport(GuiInputEvent event) {
            GuiContainer parent = getParent();
            while (parent != null) {
                if (parent instanceof GuiScrollView && !parent.contains(event.getMouseX(), event.getMouseY())) {
                    return false;
                }
                parent = parent.getParent();
            }
            return true;
        }
    }
}
