package betamoon.gui;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.widget.GuiScrollView;
import betamoon.luamodloader.LuaScriptErrors;
import java.util.List;

/** Scrollable error and warning list with retained source-file link hitboxes. */
final class GuiPanelScriptErrorList extends GuiContainer {
    private static final int ENTRY_GAP = 10;
    private static final int ENTRY_SEPARATOR_OFFSET = 4;
    private static final int CONTENT_PADDING = 6;

    private final ErrorListContent content = new ErrorListContent();
    private final GuiScrollView scrollView = new GuiScrollView(content, GuiScrollView.Policy.VERTICAL);
    private GuiIssueLayout issueLayout;

    GuiPanelScriptErrorList() {
        add(scrollView);
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        int contentWidth = Math.max(0, getWidth() - CONTENT_PADDING);
        issueLayout = GuiIssueLayout.prepare(context.getFont(), LuaScriptErrors.getEntries(), contentWidth, ENTRY_GAP);
        scrollView.setContentSize(getWidth(), issueLayout.getHeight());
        scrollView.arrange(context, getBounds());
    }

    private final class ErrorListContent extends GuiElement {
        @Override
        protected void renderElement(GuiContext context) {
            if (issueLayout == null) {
                return;
            }
            int contentWidth = Math.max(0, getWidth() - CONTENT_PADDING);
            int y = getTop();
            List<GuiIssueLayout.Entry> entries = issueLayout.getEntries();
            for (int i = 0; i < entries.size(); i++) {
                GuiIssueLayout.Entry entry = entries.get(i);
                int color = entry.isWarning() ? context.getRenderer().getTheme().textWarning
                        : context.getRenderer().getTheme().textError;
                int entryY = y;
                entry.draw(context, getLeft() + CONTENT_PADDING, entryY, contentWidth, color);
                y = entryY + entry.getHeight() + ENTRY_GAP;
                if (i < entries.size() - 1) {
                    context.getRenderer().drawHorizontalLine(getLeft() + CONTENT_PADDING,
                            getRight() - CONTENT_PADDING, entryY + entry.getHeight() + ENTRY_SEPARATOR_OFFSET,
                            context.getRenderer().getTheme().listSeparator);
                }
            }
        }

        @Override
        protected boolean onInput(GuiContext context, GuiInputEvent event) {
            if (event.getType() != GuiInputEvent.Type.POINTER_DOWN || issueLayout == null) {
                return false;
            }
            List<GuiIssueLayout.Entry> entries = issueLayout.getEntries();
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
                    return true;
                }
            }
            return false;
        }
    }
}
