package betamoon.gui;

import betamoon.gui.framework.GuiAction;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.widget.GuiButton;
import betamoon.luamodloader.LuaScriptErrors.ScriptIssue;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.lwjgl.input.Keyboard;

/**
 * The utility-button pair exactly spans Dismiss; feedback aligns above Close
 * Game.
 */
final class GuiScriptIssueFooter extends GuiContainer {
    private static final int GAP = 6;
    private final GuiScriptIssueActions actions;
    private final GuiButton copy;
    private final GuiButton export;
    private final Feedback feedback = new Feedback();
    private final GuiButton dismiss;
    private final GuiButton close;
    private final Supplier<List<ScriptIssue>> issues;

    GuiScriptIssueFooter(GuiScriptIssueActions actions, Supplier<List<ScriptIssue>> issues, GuiAction exportAction,
            GuiAction dismissAction, GuiAction closeAction) {
        this.actions = actions;
        this.issues = issues;
        copy = add(new GuiButton("Copy all", () -> actions.copyAll(issues.get())));
        export = add(new GuiButton("Save report", exportAction));
        add(feedback);
        dismiss = add(new GuiButton("Dismiss", dismissAction));
        close = add(new GuiButton("Close Game", closeAction));
    }

    void focusDismiss(GuiContext context) {
        context.requestFocus(dismiss);
    }

    private boolean compact(GuiContext context, int width) {
        int utilityWidth = Math.max(context.getRenderer().textWidth(copy.getLabel()),
                context.getRenderer().textWidth(export.getLabel())) + 16;
        return (width - GAP) / 2 >= utilityWidth * 2 + GAP;
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        return new Size(constraints.getMaxWidth(), compact(context, constraints.getMaxWidth()) ? 44 : 62);
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        boolean haveIssues = !issues.get().isEmpty();
        copy.setEnabled(haveIssues);
        export.setEnabled(haveIssues);
        int column = Math.max(0, (getWidth() - GAP) / 2);
        int right = getLeft() + column + GAP;
        int mainY = getBottom() - 20;
        dismiss.arrange(context, Rect.fromPositionAndSize(getLeft(), mainY, column, 20));
        close.arrange(context, new Rect(right, mainY, getRight(), getBottom()));
        if (compact(context, getWidth())) {
            int small = Math.max(0, (column - GAP) / 2);
            copy.arrange(context, Rect.fromPositionAndSize(getLeft(), getTop(), small, 18));
            export.arrange(context, new Rect(getLeft() + small + GAP, getTop(), getLeft() + column, getTop() + 18));
            feedback.arrange(context, new Rect(right, getTop(), getRight(), getTop() + 18));
        } else {
            copy.arrange(context, Rect.fromPositionAndSize(getLeft(), getTop(), column, 18));
            export.arrange(context, new Rect(right, getTop(), getRight(), getTop() + 18));
            feedback.arrange(context, new Rect(getLeft(), getTop() + 22, getRight(), mainY - 4));
        }
    }

    private final class Feedback extends GuiElement {
        private String text = "";
        private String detail = "";
        private boolean link;
        private boolean failed;

        @Override
        protected void updateElement(GuiContext context) {
            String feedback = actions.feedback();
            link = actions.reportLinkVisible();
            text = link ? "Open report" : feedback.isEmpty() ? "Click an entry to copy" : feedback;
            detail = link ? actions.report().toString() : feedback.isEmpty() ? "" : actions.detail();
            failed = actions.failed();
            setFocusable(link);
        }

        @Override
        protected void renderElement(GuiContext context) {
            int color = failed
                    ? context.getRenderer().getTheme().textError
                    : link ? context.getRenderer().getTheme().link : context.getRenderer().getTheme().textMuted;
            String visible = context.getRenderer().trimToWidth(text, getWidth());
            int x = getRight() - context.getRenderer().textWidth(visible);
            int y = getTop() + (getHeight() - context.getRenderer().lineHeight()) / 2;
            context.getRenderer().drawText(visible, x, y, color);
            if (link && (isHovered(context) || isFocused())) {
                context.getRenderer().drawHorizontalLine(x, getRight(), y + context.getRenderer().lineHeight(), color);
            }
            if (isHovered(context)) {
                if (!detail.isEmpty() || !visible.equals(text)) {
                    context.showTooltip(Arrays.asList((detail.isEmpty() ? text : detail).split("\\n")));
                }
            }
        }

        @Override
        protected boolean onInput(GuiContext context, GuiInputEvent event) {
            int width = Math.min(getWidth(), context.getRenderer().textWidth("Open report"));
            int y = getTop() + (getHeight() - context.getRenderer().lineHeight()) / 2;
            boolean click = event.getType() == GuiInputEvent.Type.POINTER_DOWN && event.getButton() == 0
                    && Rect.fromPositionAndSize(getRight() - width, y, width, context.getRenderer().lineHeight())
                            .contains(event.getMouseX(), event.getMouseY());
            boolean key = event.getType() == GuiInputEvent.Type.KEY_TYPED && isFocused()
                    && (event.getKeyCode() == Keyboard.KEY_RETURN || event.getKeyCode() == Keyboard.KEY_NUMPADENTER
                            || event.getKeyCode() == Keyboard.KEY_SPACE);
            if ((click || key) && actions.reportLinkVisible()) {
                actions.openReport();
                return true;
            }
            return false;
        }
    }
}
