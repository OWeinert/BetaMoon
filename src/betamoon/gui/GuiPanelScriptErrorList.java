package betamoon.gui;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.widget.GuiScrollView;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptErrors.ScriptIssue;
import java.util.List;

/** Scrollable diagnostics; the summary and footer remain stationary. */
final class GuiPanelScriptErrorList extends GuiContainer {
    private final GuiScriptIssueEntries content;
    private final GuiScrollView scrollView;
    private final boolean showWarnings;
    private String summary = "";

    GuiPanelScriptErrorList(boolean showWarnings) {
        this(showWarnings, new GuiScriptIssueActions());
    }

    GuiPanelScriptErrorList(boolean showWarnings, GuiScriptIssueActions actions) {
        this.showWarnings = showWarnings;
        content = new GuiScriptIssueEntries(actions);
        scrollView = add(new GuiScrollView(content, GuiScrollView.Policy.VERTICAL));
    }

    List<ScriptIssue> issues() {
        return GuiScriptIssueText.ordered(LuaScriptErrors.getNotificationEntries(showWarnings));
    }

    @Override
    protected void updateElement(GuiContext context) {
        List<ScriptIssue> issues = issues();
        content.setIssues(issues);
        int warnings = (int) issues.stream().filter(ScriptIssue::isWarning).count();
        int errors = issues.size() - warnings;
        summary = errors + (errors == 1 ? " error, " : " errors, ") + warnings
                + (warnings == 1 ? " warning" : " warnings");
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        int height = content
                .measure(context, Constraints.upTo(Math.max(1, constraints.getMaxWidth()), Integer.MAX_VALUE))
                .getHeight();
        return new Size(constraints.getMaxWidth(), 18 + height);
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        int contentWidth = Math.max(1, getWidth());
        int contentHeight = content.measure(context, Constraints.upTo(contentWidth, Integer.MAX_VALUE)).getHeight();
        scrollView.setContentSize(contentWidth, contentHeight);
        scrollView.arrange(context, new Rect(getLeft(), getTop() + 18, getRight(), getBottom()));
    }

    @Override
    protected void renderBeforeChildren(GuiContext context) {
        context.getRenderer().drawText(summary, getLeft() + GuiIssueLayout.PADDING, getTop(),
                context.getRenderer().getTheme().textMuted);
    }
}
