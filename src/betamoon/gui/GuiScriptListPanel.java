package betamoon.gui;

import betamoon.gui.api.GuiText;
import betamoon.gui.api.GuiUtils;
import betamoon.gui.api.ScrollState;
import betamoon.scriptloader.ScriptMod;
import java.util.List;
import net.minecraft.src.FontRenderer;

public final class GuiScriptListPanel {
    private static final int ENTRY_PADDING = 10;
    private static final int PADDING = 10;

    private final ScrollState scrollState = new ScrollState();
    private int listLeft;
    private int listRight;
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

    /**
     * Resets scroll state and clears selection/hover state.
     */
    public void reset() {
        scrollState.reset();
        selectedIndex = -1;
        hoverIndex = -1;
    }

    /**
     * Feeds mouse wheel and drag events into the scroll state.
     *
     * @param mouseX mouse x in GUI coordinates
     * @param mouseY mouse y in GUI coordinates
     * @param wheelDelta mouse wheel delta
     * @param mouseDown true when left mouse button is down
     */
    public void handleMouseInput(int mouseX, int mouseY, int wheelDelta, boolean mouseDown) {
        scrollState.handleMouseWheel(mouseX, mouseY, wheelDelta);
        scrollState.handleMouseDrag(mouseY, mouseDown);
        updateHoverIndex(mouseX, mouseY);
    }

    /**
     * Handles mouse clicks for list selection and scrollbar dragging.
     *
     * @param mouseX mouse x in GUI coordinates
     * @param mouseY mouse y in GUI coordinates
     * @param button mouse button id
     */
    public void mouseClicked(int mouseX, int mouseY, int button) {
        scrollState.mouseClicked(mouseX, mouseY, button);
        if (button == 0) {
            selectEntryAt(mouseX, mouseY);
        }
    }

    /**
     * Releases any active scrollbar drag on mouse release.
     *
     * @param button mouse button id
     */
    public void mouseReleased(int button) {
        scrollState.mouseReleased(button);
    }

    /**
     * Draws the scripts list panel and its scrollbar.
     *
     * @param font font renderer
     * @param screenWidth screen width
     * @param screenHeight screen height
     * @param backButtonY y position of the back button
     * @param headerScale scale factor for the header text
     * @param entries list of ScriptMod entries
     */
    public void draw(FontRenderer font, int screenWidth, int screenHeight, int displayWidth, int displayHeight, int backButtonY, float headerScale, List entries) {
        this.font = font;
        this.entries = entries;
        headerTextY = PADDING + 4;
        headerLineY = PADDING + 20;
        int listWidth = Math.min(200, Math.max(120, screenWidth / 4));
        listLeft = PADDING;
        listRight = listLeft + listWidth;
        listTop = headerLineY + 6;
        listBottom = backButtonY - PADDING;
        if (listBottom - listTop < 80) {
            listBottom = screenHeight - PADDING;
        }
        separatorX = listRight + 6;
        listContentRight = listRight - 6;
        listContentWidth = listContentRight - listLeft - 4;

        // Section headers and separators.
        GuiUtils.drawScaledString(font, "Scripts", listLeft, headerTextY, 0xFFFFFF, headerScale);
        GuiUtils.drawHorizontalLine(PADDING, screenWidth - PADDING, headerLineY, 0xFFFFFFFF);
        GuiUtils.drawVerticalLine(listTop - 6, listBottom + 10, separatorX, 0xFFFFFFFF);

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
        scrollState.setBounds(listLeft, listTop, listRight, listBottom);
        scrollState.updateContentHeight(contentHeight);

        int y = listTop - scrollState.getScrollOffset();
        int scissorTop = listTop - 2;
        int scissorBottom = listBottom + 8;
        // Constrain list rendering to the visible panel.
        GuiUtils.beginScissor(listLeft, scissorTop, listContentRight, scissorBottom, screenWidth, screenHeight, displayWidth, displayHeight);
        if (entries != null) {
            for (int i = 0; i < entries.size(); i++) {
                ScriptMod entry = (ScriptMod) entries.get(i);
                int color = entry.isFailed() ? 0xFFCC6666 : 0xFFFFFF;
                String displayName = GuiText.trimToWidth(font, entry.getDisplayName(), listContentWidth);
                int entryHeight = font.func_27277_a(displayName, listContentWidth);
                int blockHeight = entryHeight + ENTRY_PADDING - 2;
                if (y > listBottom) {
                    break;
                }
                // Render highlight backgrounds for hover/selection.
                if (i == selectedIndex) {
                    GuiUtils.drawRect(listLeft + 1, y - 1, listContentRight - 1, y + blockHeight - 1, 0xCC3B6DD1);
                } else if (i == hoverIndex) {
                    GuiUtils.drawRect(listLeft + 1, y - 1, listContentRight - 1, y + blockHeight - 1, 0x88444444);
                }
                // Draw text only when within the visible list bounds.
                if (y + entryHeight >= listTop && y <= listBottom) {
                    int textY = y + (blockHeight - entryHeight) / 2;
                    font.func_27278_a(displayName, listLeft + 4, textY, listContentWidth, color);
                }
                y += entryHeight + ENTRY_PADDING;
            }
        }
        GuiUtils.endScissor();

        scrollState.drawScrollbar(contentHeight);
    }

    /**
     * Returns the currently selected script entry.
     *
     * @param entries list of ScriptMod entries
     * @return selected entry or null when none exist
     */
    public ScriptMod getSelectedEntry(List entries) {
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
        if (mouseX < listLeft || mouseX > listContentRight || mouseY < listTop || mouseY > listBottom + 6) {
            return;
        }
        int y = listTop - scrollState.getScrollOffset();
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
        if (mouseX < listLeft || mouseX > listContentRight || mouseY < listTop || mouseY > listBottom + 6) {
            return;
        }
        int y = listTop - scrollState.getScrollOffset();
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

}
