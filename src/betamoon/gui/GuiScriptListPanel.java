package betamoon.gui;

import betamoon.gui.api.component.EnumScrollMode;
import betamoon.gui.api.util.GuiColors;
import betamoon.gui.api.component.GuiScrollPanel;
import betamoon.gui.api.util.GuiText;
import betamoon.gui.api.util.GuiUtils;
import betamoon.scriptloader.ScriptMod;
import java.util.List;
import net.minecraft.src.FontRenderer;

public final class GuiScriptListPanel extends GuiScrollPanel {
    private static final int ENTRY_PADDING = 10;
    private static final int PADDING = 10;

    private int listTop;
    private int listBottom;
    private int listContentWidth;
    private int listContentRight;
    private int separatorX;
    private int headerTextY;
    private int headerLineY;
    private int selectedIndex = -1;
    private int hoverIndex = -1;
    private List entries;
    private FontRenderer font;
    private float headerScale = 1.0F;
    private int screenWidth;
    private int screenHeight;
    private int displayWidth;
    private int displayHeight;

    /**
     * Resets scroll state and clears selection/hover state.
     */
    public GuiScriptListPanel() {
        super(EnumScrollMode.VERTICAL);
    }

    public void reset() {
        resetScroll();
        selectedIndex = -1;
        hoverIndex = -1;
    }

    public void setEntries(List entries) {
        this.entries = entries;
    }

    public void setHeaderScale(float headerScale) {
        this.headerScale = headerScale;
    }

    public void setDisplayMetrics(int screenWidth, int screenHeight, int displayWidth, int displayHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
    }

    public void layout(int screenWidth, int screenHeight) {
        headerTextY = top + 4;
        headerLineY = top + 20;
        listTop = headerLineY + 6;
        listBottom = bottom;
        if (listBottom - listTop < 80) {
            listBottom = screenHeight - PADDING;
        }
        listContentRight = right - 6;
        listContentWidth = listContentRight - left - 4;
        separatorX = right + 6;
    }

    /**
     * Draws the scripts list panel and its scrollbar.
     */
    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
        this.font = font;
        updateHoverIndex(mouseX, mouseY);

        // Section headers and separators.
        GuiUtils.drawScaledString(font, "Scripts", left, headerTextY, GuiColors.TEXT_PRIMARY, headerScale);
        GuiUtils.drawHorizontalLine(PADDING, screenWidth - PADDING, headerLineY, GuiColors.LINE_WHITE);
        GuiUtils.drawVerticalLine(listTop - 6, listBottom + 10, separatorX, GuiColors.LINE_WHITE);

        int contentHeight = 0;
        if (entries != null) {
            // Precompute total height for scrolling.
            for (int i = 0; i < entries.size(); i++) {
                ScriptMod entry = (ScriptMod) entries.get(i);
                String displayName = GuiText.trimToWidth(font, entry.getDisplayName(), listContentWidth);
                int entryHeight = font.func_27277_a(displayName, listContentWidth);
                contentHeight += entryHeight + ENTRY_PADDING;
            }
        }
        if (contentHeight > 0) {
            contentHeight -= ENTRY_PADDING;
        }
        // Update scroll bounds based on total content height.
        updateScrollContentSize(listContentWidth, contentHeight);

        int y = listTop - getScrollOffsetY();
        int scissorTop = listTop - 2;
        int scissorBottom = listBottom + 8;
        // Constrain list rendering to the visible panel.
        GuiUtils.beginScissor(left, scissorTop, listContentRight, scissorBottom, screenWidth, screenHeight, displayWidth, displayHeight);
        if (entries != null) {
            for (int i = 0; i < entries.size(); i++) {
                ScriptMod entry = (ScriptMod) entries.get(i);
                int color = entry.isFailed() ? GuiColors.TEXT_ERROR : GuiColors.TEXT_PRIMARY;
                String displayName = GuiText.trimToWidth(font, entry.getDisplayName(), listContentWidth);
                int entryHeight = font.func_27277_a(displayName, listContentWidth);
                int blockHeight = entryHeight + ENTRY_PADDING - 2;
                if (y > listBottom) {
                    break;
                }
                // Render highlight backgrounds for hover/selection.
                if (i == selectedIndex) {
                    GuiUtils.drawRect(left + 1, y - 1, listContentRight - 1, y + blockHeight - 1, GuiColors.LIST_SELECTED_BG);
                } else if (i == hoverIndex) {
                    GuiUtils.drawRect(left + 1, y - 1, listContentRight - 1, y + blockHeight - 1, GuiColors.LIST_HOVER_BG);
                }
                // Draw text only when within the visible list bounds.
                if (y + entryHeight >= listTop && y <= listBottom) {
                    int textY = y + (blockHeight - entryHeight) / 2;
                    font.func_27278_a(displayName, left + 4, textY, listContentWidth, color);
                }
                y += entryHeight + ENTRY_PADDING;
            }
        }
        GuiUtils.endScissor();

        drawScrollbar(contentHeight);
    }

    /**
     * Returns the currently selected script entry.
     *
     * @param entries list of ScriptMod entries
     * @return selected entry or null when none exist
     */
    public ScriptMod getSelectedEntry() {
        if (entries == null || entries.isEmpty()) {
            selectedIndex = -1;
            return null;
        }
        if (selectedIndex < 0 || selectedIndex >= entries.size()) {
            selectedIndex = 0;
        }
        return (ScriptMod) entries.get(selectedIndex);
    }

    /**
     * Returns the x coordinate of the vertical separator.
     *
     * @return separator x position
     */
    public int getSeparatorX() {
        return separatorX;
    }

    /**
     * Returns the outer panel padding.
     *
     * @return padding value
     */
    public int getPadding() {
        return PADDING;
    }

    /**
     * Returns the y coordinate of the header text.
     *
     * @return header text y position
     */
    public int getHeaderTextY() {
        return headerTextY;
    }

    /**
     * Returns the top y coordinate of the list content.
     *
     * @return list top y position
     */
    public int getListTop() {
        return listTop;
    }

    /**
     * Returns the bottom y coordinate of the list content.
     *
     * @return list bottom y position
     */
    public int getListBottom() {
        return listBottom;
    }

    /**
     * Updates the selected entry based on a click position.
     *
     * @param mouseX mouse x in GUI coordinates
     * @param mouseY mouse y in GUI coordinates
     */
    private void selectEntryAt(int mouseX, int mouseY) {
        if (entries == null || entries.isEmpty() || font == null) {
            selectedIndex = -1;
            return;
        }
        if (mouseX < left || mouseX > listContentRight || mouseY < listTop || mouseY > listBottom + 6) {
            return;
        }
        int y = listTop - getScrollOffsetY();
        for (int i = 0; i < entries.size(); i++) {
            ScriptMod entry = (ScriptMod) entries.get(i);
            String displayName = GuiText.trimToWidth(this.font, entry.getDisplayName(), listContentWidth);
            int entryHeight = font.func_27277_a(displayName, listContentWidth);
            int blockHeight = entryHeight + ENTRY_PADDING - 2;
            if (mouseY >= y && mouseY <= y + blockHeight) {
                selectedIndex = i;
                return;
            }
            y += entryHeight + ENTRY_PADDING;
        }
    }

    /**
     * Updates the hovered entry based on current mouse position.
     *
     * @param mouseX mouse x in GUI coordinates
     * @param mouseY mouse y in GUI coordinates
     */
    private void updateHoverIndex(int mouseX, int mouseY) {
        hoverIndex = -1;
        if (entries == null || entries.isEmpty() || font == null) {
            return;
        }
        if (mouseX < left || mouseX > listContentRight || mouseY < listTop || mouseY > listBottom + 6) {
            return;
        }
        int y = listTop - getScrollOffsetY();
        for (int i = 0; i < entries.size(); i++) {
            ScriptMod entry = (ScriptMod) entries.get(i);
            String displayName = GuiText.trimToWidth(this.font, entry.getDisplayName(), listContentWidth);
            int entryHeight = font.func_27277_a(displayName, listContentWidth);
            int blockHeight = entryHeight + ENTRY_PADDING - 2;
            if (mouseY >= y && mouseY <= y + blockHeight) {
                hoverIndex = i;
                return;
            }
            y += entryHeight + ENTRY_PADDING;
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        super.mouseClicked(mouseX, mouseY, button);
        if (button == 0) {
            selectEntryAt(mouseX, mouseY);
            return true;
        }
        return false;
    }
}
