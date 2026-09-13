package betamoon.gui;

import betamoon.gui.api.component.EnumScrollMode;
import betamoon.gui.api.component.GuiComponentBase;
import betamoon.gui.api.component.GuiScrollPanel;
import betamoon.gui.api.component.GuiTextClickable;
import betamoon.gui.api.util.GuiColors;
import betamoon.gui.api.util.GuiUtils;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptErrors.ScriptIssue;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.FontRenderer;

/**
 * Scrollable list of script errors and warnings with clickable links.
 */
final class GuiPanelScriptErrorList extends GuiComponentBase {
    private static final int ENTRY_GAP = 10;
    private static final int ENTRY_SEPARATOR_OFFSET = 4;
    private static final int CONTENT_PADDING = 6;

    private final List<GuiTextClickable> linkTexts = new ArrayList<>();
    private final GuiTextClickable inlineHelper = new GuiTextClickable();
    private final ErrorListContent content = new ErrorListContent();
    private final GuiScrollPanel scrollPanel = new GuiScrollPanel(content, EnumScrollMode.VERTICAL);
    private GuiIssueLayout issueLayout;
    private int screenWidth;
    private int screenHeight;

    /**
     * Creates a vertical scroll list for script issues.
     */
    GuiPanelScriptErrorList() {
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        scrollPanel.setBounds(left, top, right, bottom);
    }

    @Override
    public void layout(int screenWidth, int screenHeight) {
        scrollPanel.layout(screenWidth, screenHeight);
    }

    /**
     * Updates display metrics required for scissor clipping.
     */
    void setDisplayMetrics(int screenWidth, int screenHeight, int displayWidth, int displayHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        scrollPanel.setDisplayMetrics(screenWidth, screenHeight, displayWidth, displayHeight);
    }

    /**
     * Draws the scrollable list of errors and warnings.
     */
    @Override
    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
        List<ScriptIssue> entries = LuaScriptErrors.getEntries();
        int contentWidth = Math.max(0, right - left - CONTENT_PADDING);
        issueLayout = GuiIssueLayout.prepare(font, entries, contentWidth, ENTRY_GAP);
        scrollPanel.setContentSize(Math.max(0, right - left), issueLayout.getHeight());
        scrollPanel.draw(font, mouseX, mouseY, partialTicks);
    }

    /**
     * Routes mouse clicks to any inline links before the scroll panel.
     */
    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return scrollPanel.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        return scrollPanel.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(int mouseX, int mouseY, boolean mouseDown) {
        return scrollPanel.mouseDragged(mouseX, mouseY, mouseDown);
    }

    @Override
    public boolean mouseScrolled(int mouseX, int mouseY, int wheelDelta, boolean shiftDown) {
        return scrollPanel.mouseScrolled(mouseX, mouseY, wheelDelta, shiftDown);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        return scrollPanel.keyTyped(typedChar, keyCode);
    }

    private final class ErrorListContent extends GuiComponentBase {
        @Override
        public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
            if (issueLayout == null) {
                return;
            }
            int contentWidth = Math.max(0, right - left - CONTENT_PADDING);
            int y = top;
            linkTexts.clear();
            List<GuiIssueLayout.Entry> entries = issueLayout.getEntries();
            for (int i = 0; i < entries.size(); i++) {
                GuiIssueLayout.Entry entry = entries.get(i);
                int color = entry.isWarning() ? GuiColors.TEXT_WARNING : GuiColors.TEXT_ERROR;
                int entryY = y;
                entry.draw(font, inlineHelper, linkTexts, left + CONTENT_PADDING, entryY, contentWidth, screenWidth,
                        screenHeight, color, mouseX, mouseY, partialTicks);
                int entryHeight = entry.getHeight();
                y = entryY + entryHeight + ENTRY_GAP;
                if (i < entries.size() - 1) {
                    int lineY = entryY + entryHeight + ENTRY_SEPARATOR_OFFSET;
                    GuiUtils.drawHorizontalLine(left + CONTENT_PADDING, right - CONTENT_PADDING, lineY,
                            GuiUtils.COLOR_LIST_SEPARATOR);
                }
            }
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            for (int i = 0; i < linkTexts.size(); i++) {
                GuiTextClickable link = linkTexts.get(i);
                if (link.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return false;
        }
    }

}
