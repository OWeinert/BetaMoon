package betamoon.gui.api;

import net.minecraft.src.GuiScreen;

/**
 * Base class for BetaMoon popup dialogs.
 */
public abstract class GuiPopupScreen extends GuiScreen {
    protected static final int DEFAULT_MAX_PANEL_WIDTH = 360;
    protected static final int DEFAULT_MAX_PANEL_HEIGHT = 200;
    protected static final int DEFAULT_MIN_PANEL_HEIGHT = 0;
    protected static final int DEFAULT_PANEL_HORIZONTAL_MARGIN = 40;
    protected static final int DEFAULT_PANEL_VERTICAL_MARGIN = 80;
    protected static final int DEFAULT_FRAME_PADDING = 4;
    protected static final int DEFAULT_HEADER_TEXT_OFFSET = 8;
    protected static final int DEFAULT_HEADER_LINE_OFFSET = 24;
    protected static final int DEFAULT_HEADER_LINE_COLOR = 0xFFFFFFFF;
    protected static final int DEFAULT_TITLE_COLOR = 0xFFFFFF;
    protected static final float DEFAULT_TITLE_SCALE = 1.2F;
    protected static final int DEFAULT_HEADER_LINE_INSET = 10;
    protected final GuiScreen parent;
    protected int panelLeft;
    protected int panelTop;
    protected int panelWidth;
    protected int panelHeight;

    /**
     * Creates a popup layered on top of the provided parent screen.
     *
     * @param parent screen to render behind the popup (may be null)
     */
    protected GuiPopupScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        updatePanelGeometry();
        this.controlList.clear();
        initPopupGui();
    }

    /**
     * Draws the popup frame and delegates to subclasses for contents.
     */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updatePanelGeometry();
        drawPopupBackground(mouseX, mouseY, partialTicks);
        drawPopupFrame();
        drawPopupContents(mouseX, mouseY, partialTicks);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /**
     * Builds the popup's buttons/controls; called after geometry is updated.
     */
    protected abstract void initPopupGui();

    /**
     * Returns the title shown in the popup header.
     */
    protected abstract String getPopupTitle();

    /**
     * Draws custom popup contents (text, lists, etc.).
     */
    protected void drawPopupContents(int mouseX, int mouseY, float partialTicks) {
    }

    /**
     * Maximum popup width in pixels.
     */
    protected int getMaxPanelWidth() {
        return DEFAULT_MAX_PANEL_WIDTH;
    }

    /**
     * Maximum popup height in pixels.
     */
    protected int getMaxPanelHeight() {
        return DEFAULT_MAX_PANEL_HEIGHT;
    }

    /**
     * Minimum popup height in pixels (0 disables the minimum constraint).
     */
    protected int getMinPanelHeight() {
        return DEFAULT_MIN_PANEL_HEIGHT;
    }

    /**
     * Horizontal margin to keep from screen edges.
     */
    protected int getPanelHorizontalMargin() {
        return DEFAULT_PANEL_HORIZONTAL_MARGIN;
    }

    /**
     * Vertical margin to keep from screen edges.
     */
    protected int getPanelVerticalMargin() {
        return DEFAULT_PANEL_VERTICAL_MARGIN;
    }

    /**
     * Outer border padding around the popup panel.
     */
    protected int getFramePadding() {
        return DEFAULT_FRAME_PADDING;
    }

    /**
     * Header text offset from the top of the panel.
     */
    protected int getHeaderTextOffset() {
        return DEFAULT_HEADER_TEXT_OFFSET;
    }

    /**
     * Header underline offset from the top of the panel.
     */
    protected int getHeaderLineOffset() {
        return DEFAULT_HEADER_LINE_OFFSET;
    }

    /**
     * Header underline color (ARGB).
     */
    protected int getHeaderLineColor() {
        return DEFAULT_HEADER_LINE_COLOR;
    }

    /**
     * Header title color (ARGB).
     */
    protected int getTitleColor() {
        return DEFAULT_TITLE_COLOR;
    }

    /**
     * Header title scale factor.
     */
    protected float getTitleScale() {
        return DEFAULT_TITLE_SCALE;
    }

    /**
     * Updates panel geometry based on current screen size.
     */
    protected void updatePanelGeometry() {
        panelWidth = Math.min(getMaxPanelWidth(), this.width - getPanelHorizontalMargin());
        panelWidth = Math.max(0, panelWidth);
        panelHeight = Math.min(getMaxPanelHeight(), this.height - getPanelVerticalMargin());
        int minHeight = getMinPanelHeight();
        if (minHeight > 0 && panelHeight < minHeight) {
            panelHeight = minHeight;
        }
        panelHeight = Math.max(0, panelHeight);
        panelLeft = GuiLayout.centerX(this.width, panelWidth);
        panelTop = GuiLayout.centerY(this.height, panelHeight);
    }

    /**
     * Draws the background behind the popup (parent screen or default background).
     */
    protected void drawPopupBackground(int mouseX, int mouseY, float partialTicks) {
        if (this.parent != null) {
            this.parent.drawScreen(mouseX, mouseY, partialTicks);
        } else {
            this.drawDefaultBackground();
        }
    }

    /**
     * Draws the popup frame, title, and header line.
     */
    protected void drawPopupFrame() {
        int left = panelLeft;
        int top = panelTop;
        int right = panelLeft + panelWidth;
        int bottom = panelTop + panelHeight;
        int padding = getFramePadding();
        // Render the popup panel and its shadowed border.
        this.drawRect(left - padding, top - padding, right + padding, bottom + padding, 0xDD000000);
        this.drawRect(left, top, right, bottom, 0xFA1A1A1A);
        String title = getPopupTitle();
        if (title != null && !title.isEmpty()) {
            // Render the header title and underline for consistent popup styling.
            GuiUtils.drawScaledCenteredString(this.fontRenderer, title, this.width / 2,
                top + getHeaderTextOffset(), getTitleColor(), getTitleScale());
            GuiUtils.drawHorizontalLine(left + DEFAULT_HEADER_LINE_INSET, right - DEFAULT_HEADER_LINE_INSET,
                top + getHeaderLineOffset(), getHeaderLineColor());
        }
    }
}
