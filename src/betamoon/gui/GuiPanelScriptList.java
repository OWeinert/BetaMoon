package betamoon.gui;

import betamoon.gui.api.component.EnumScrollMode;
import betamoon.gui.api.component.GuiComponentBase;
import betamoon.gui.api.component.GuiNonReloadableIndicator;
import betamoon.gui.api.component.GuiScrollPanel;
import betamoon.gui.api.util.GuiColors;
import betamoon.gui.api.util.GuiText;
import betamoon.gui.api.util.GuiUtils;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.ScriptMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.FontRenderer;
import net.minecraft.src.ModLoader;

public final class GuiPanelScriptList extends GuiComponentBase {
    private static final int ROW_HEIGHT = 16;
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
    private List<ScriptMod> entries;
    private FontRenderer font;
    private float headerScale = 1.0F;
    private int screenWidth;
    private int screenHeight;
    private final List<GuiNonReloadableIndicator> warningIndicators = new ArrayList<GuiNonReloadableIndicator>();
    private GuiNonReloadableIndicator hoveredWarning;
    private static final int WARNING_SIZE = 12;
    private static final int WARNING_GAP = 3;
    private final ScriptListContent content = new ScriptListContent();
    private final GuiScrollPanel scrollPanel = new GuiScrollPanel(content, EnumScrollMode.VERTICAL);

    /**
     * Resets scroll state and clears selection/hover state.
     */
    public GuiPanelScriptList() {
    }

    public void reset() {
        scrollPanel.resetScroll();
        selectedIndex = -1;
        hoverIndex = -1;
    }

    public void setEntries(List<ScriptMod> entries) {
        this.entries = entries;
        int count = entries == null ? 0 : entries.size();
        while (warningIndicators.size() > count) {
            warningIndicators.remove(warningIndicators.size() - 1);
        }
        for (int i = 0; i < count; i++) {
            ScriptMod entry = entries.get(i);
            if (i == warningIndicators.size()) {
                GuiNonReloadableIndicator indicator = new GuiNonReloadableIndicator(entry.getSourceFileName());
                indicator.setTooltipDeferred(true);
                warningIndicators.add(indicator);
            } else {
                warningIndicators.get(i).setSourceFileName(entry.getSourceFileName());
            }
        }
    }

    public void setHeaderScale(float headerScale) {
        this.headerScale = headerScale;
    }

    public void setDisplayMetrics(int screenWidth, int screenHeight, int displayWidth, int displayHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        scrollPanel.setDisplayMetrics(screenWidth, screenHeight, displayWidth, displayHeight);
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        scrollPanel.setBounds(left, top, right, bottom);
    }

    @Override
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
        scrollPanel.setBounds(left, listTop, right, listBottom);
        scrollPanel.layout(screenWidth, screenHeight);
    }

    /**
     * Draws the scripts list panel and its scrollbar.
     */
    @Override
    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
        this.font = font;

        // Section headers and separators.
        GuiUtils.drawScaledString(font, "Scripts", left, headerTextY, GuiColors.TEXT_PRIMARY, headerScale);
        GuiUtils.drawHorizontalLine(PADDING, screenWidth - PADDING, headerLineY, GuiColors.LINE_WHITE);
        GuiUtils.drawVerticalLine(listTop - 6, listBottom + 2, separatorX, GuiColors.LINE_WHITE);

        int contentHeight = entries == null ? 0 : entries.size() * ROW_HEIGHT;
        if (contentHeight > 0) {
            // The last row still draws its background and vertically centered text into
            // all but two pixels of the normal inter-row spacing. Include those pixels
            // so even a one-pixel overflow creates a matching scroll range.
            contentHeight -= 2;
        }
        scrollPanel.setContentSize(Math.max(0, right - left), contentHeight);
        hoveredWarning = null;
        scrollPanel.draw(font, mouseX, mouseY, partialTicks);
        if (hoveredWarning != null) {
            hoveredWarning.drawTooltip(font, mouseX, mouseY);
        }
    }

    private final class ScriptListContent extends GuiComponentBase {
        @Override
        public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
            updateHoverIndex(mouseX, mouseY);
            int y = top;
            if (entries == null) {
                return;
            }
            for (int i = 0; i < entries.size(); i++) {
                ScriptMod entry = entries.get(i);
                int color = entry.isFailed() ? GuiColors.TEXT_ERROR : GuiColors.TEXT_PRIMARY;
                if (!entry.isFailed()
                        && LuaScriptErrors.hasWarningFor(entry.getDisplayName(), entry.getSourceFileName())) {
                    color = GuiColors.TEXT_WARNING;
                }
                GuiNonReloadableIndicator indicator = warningIndicators.get(i);
                boolean nonReloadable = indicator.isVisible();
                int nameLeft = left + 4 + (nonReloadable ? WARNING_SIZE + WARNING_GAP : 0);
                int nameWidth = listContentRight - nameLeft;
                String displayName = GuiText.trimToWidth(font, entry.getDisplayName(), nameWidth);
                int entryHeight = font.func_27277_a(displayName, listContentWidth);
                int blockHeight = ROW_HEIGHT - 2;
                if (i == selectedIndex) {
                    GuiUtils.drawRect(left + 1, y - 1, listContentRight - 1, y + blockHeight - 1,
                            GuiColors.LIST_SELECTED_BG);
                } else if (i == hoverIndex) {
                    GuiUtils.drawRect(left + 1, y - 1, listContentRight - 1, y + blockHeight - 1,
                            GuiColors.LIST_HOVER_BG);
                }
                int textY = y + (blockHeight - entryHeight) / 2;
                if (nonReloadable) {
                    int iconY = y + (blockHeight - WARNING_SIZE - 2) / 2;
                    indicator.setMinecraft(ModLoader.getMinecraftInstance());
                    indicator.layout(screenWidth, screenHeight);
                    indicator.setBounds(left + 4, iconY, left + 4 + WARNING_SIZE, iconY + WARNING_SIZE);
                    indicator.draw(font, mouseX, mouseY, partialTicks);
                    if (indicator.isHovered(mouseX, mouseY) && mouseX >= left && mouseX < right && mouseY >= listTop
                            && mouseY < listBottom) {
                        hoveredWarning = indicator;
                    }
                }
                font.func_27278_a(displayName, nameLeft, textY, nameWidth, color);
                y += ROW_HEIGHT;
            }
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (button == 0) {
                selectEntryAt(mouseX, mouseY);
                return true;
            }
            return false;
        }

        private void selectEntryAt(int mouseX, int mouseY) {
            if (entries == null || entries.isEmpty() || font == null) {
                selectedIndex = -1;
                return;
            }
            if (mouseX < left || mouseX > listContentRight) {
                return;
            }
            int y = top;
            for (int i = 0; i < entries.size(); i++) {
                int blockHeight = ROW_HEIGHT - 2;
                if (mouseY >= y && mouseY <= y + blockHeight) {
                    selectedIndex = i;
                    return;
                }
                y += ROW_HEIGHT;
            }
        }

        private void updateHoverIndex(int mouseX, int mouseY) {
            hoverIndex = -1;
            if (entries == null || entries.isEmpty() || font == null || mouseX < left || mouseX > listContentRight
                    || mouseY < listTop || mouseY > listBottom) {
                return;
            }
            int y = top;
            for (int i = 0; i < entries.size(); i++) {
                int blockHeight = ROW_HEIGHT - 2;
                if (mouseY >= y && mouseY <= y + blockHeight) {
                    hoverIndex = i;
                    return;
                }
                y += ROW_HEIGHT;
            }
        }
    }

    /**
     * Returns the currently selected script entry.
     *
     * @param entries
     *            list of ScriptMod entries
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
        return entries.get(selectedIndex);
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

}
