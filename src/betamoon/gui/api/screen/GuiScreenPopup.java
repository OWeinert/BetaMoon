package betamoon.gui.api.screen;

import betamoon.gui.api.component.GuiContainer;
import betamoon.gui.api.layout.GuiLayout;
import betamoon.gui.api.util.GuiColors;
import betamoon.gui.api.util.GuiUtils;
import net.minecraft.src.GuiScreen;
import org.lwjgl.input.Keyboard;

/**
 * Base class for BetaMoon popup dialogs.
 */
public abstract class GuiScreenPopup extends GuiScreenBase {
    protected static final int DEFAULT_MAX_PANEL_WIDTH = 360;
    protected static final int DEFAULT_MAX_PANEL_HEIGHT = 200;
    protected static final int DEFAULT_MIN_PANEL_HEIGHT = 0;
    protected static final int DEFAULT_PANEL_HORIZONTAL_MARGIN = 40;
    protected static final int DEFAULT_PANEL_VERTICAL_MARGIN = 80;
    protected static final int DEFAULT_FRAME_PADDING = 4;
    protected static final int DEFAULT_HEADER_TEXT_OFFSET = 8;
    protected static final int DEFAULT_HEADER_LINE_OFFSET = 24;
    protected static final int DEFAULT_HEADER_LINE_COLOR = GuiColors.LINE_WHITE;
    protected static final int DEFAULT_TITLE_COLOR = GuiColors.TEXT_PRIMARY;
    protected static final float DEFAULT_TITLE_SCALE = 1.2F;
    protected static final int DEFAULT_HEADER_LINE_INSET = 10;
    protected final GuiScreen parent;
    protected int panelLeft;
    protected int panelTop;
    protected int panelWidth;
    protected int panelHeight;
    protected final GuiContainer popupRoot = new GuiContainer();
    private int framePadding = DEFAULT_FRAME_PADDING;
    private int headerTextOffset = DEFAULT_HEADER_TEXT_OFFSET;
    private int headerLineOffset = DEFAULT_HEADER_LINE_OFFSET;
    private int headerLineInset = DEFAULT_HEADER_LINE_INSET;
    private int headerLineColor = DEFAULT_HEADER_LINE_COLOR;
    private int titleColor = DEFAULT_TITLE_COLOR;
    private float titleScale = DEFAULT_TITLE_SCALE;

    /**
     * Creates a popup layered on top of the provided parent screen.
     *
     * @param parent screen to render behind the popup (may be null)
     */
    protected GuiScreenPopup(GuiScreen parent) {
        this.parent = parent;
    }

    protected void buildGui() {
        root.addChild(popupRoot);
        initPopupGui();
    }

    /**
     * Draws the popup frame and delegates to subclasses for contents.
     */
    protected void layoutComponents() {
        updatePanelGeometry();
        popupRoot.setBounds(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight);
        layoutPopupComponents();
        super.layoutComponents();
    }

    protected void drawBackground(int mouseX, int mouseY, float partialTicks) {
        drawPopupBackground(mouseX, mouseY, partialTicks);
        drawPopupFrame();
    }

    /**
     * Builds the popup's buttons/controls; called after geometry is updated.
     */
    protected abstract void initPopupGui();

    protected abstract void layoutPopupComponents();

    /**
     * Returns the title shown in the popup header.
     */
    protected abstract String getPopupTitle();

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
        return framePadding;
    }

    /**
     * Header text offset from the top of the panel.
     */
    protected int getHeaderTextOffset() {
        return headerTextOffset;
    }

    /**
     * Header underline offset from the top of the panel.
     */
    protected int getHeaderLineOffset() {
        return headerLineOffset;
    }

    /**
     * Header underline inset from the left and right edges.
     */
    protected int getHeaderLineInset() {
        return headerLineInset;
    }

    /**
     * Header underline color (ARGB).
     */
    protected int getHeaderLineColor() {
        return headerLineColor;
    }

    /**
     * Header title color (ARGB).
     */
    protected int getTitleColor() {
        return titleColor;
    }

    /**
     * Header title scale factor.
     */
    protected float getTitleScale() {
        return titleScale;
    }

    /**
     * Sets the padding between the frame edge and the panel.
     */
    protected void setFramePadding(int framePadding) {
        this.framePadding = framePadding;
    }

    /**
     * Sets the header text offset from the top of the panel.
     */
    protected void setHeaderTextOffset(int headerTextOffset) {
        this.headerTextOffset = headerTextOffset;
    }

    /**
     * Sets the header underline offset from the top of the panel.
     */
    protected void setHeaderLineOffset(int headerLineOffset) {
        this.headerLineOffset = headerLineOffset;
    }

    /**
     * Sets the left/right inset for the header underline.
     */
    protected void setHeaderLineInset(int headerLineInset) {
        this.headerLineInset = headerLineInset;
    }

    /**
     * Sets the header underline color (ARGB).
     */
    protected void setHeaderLineColor(int headerLineColor) {
        this.headerLineColor = headerLineColor;
    }

    /**
     * Sets the header title color (ARGB).
     */
    protected void setTitleColor(int titleColor) {
        this.titleColor = titleColor;
    }

    /**
     * Sets the header title scale factor.
     */
    protected void setTitleScale(float titleScale) {
        this.titleScale = titleScale;
    }

    /**
     * Updates panel geometry based on current screen size.
     */
    protected void updatePanelGeometry() {
        // Constrain popup size within margins, then center it.
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

    protected void drawPopupFrame() {
        int left = panelLeft;
        int top = panelTop;
        int right = panelLeft + panelWidth;
        int bottom = panelTop + panelHeight;
        int padding = getFramePadding();
        // Render the popup panel and its shadowed border.
        this.drawRect(left - padding, top - padding, right + padding, bottom + padding, GuiColors.POPUP_SHADOW);
        this.drawRect(left, top, right, bottom, GuiColors.POPUP_PANEL);
        String title = getPopupTitle();
        if (title != null && !title.isEmpty()) {
            // Render the header title and underline for consistent popup styling.
            GuiUtils.drawScaledCenteredString(this.fontRenderer, title, this.width / 2,
                top + getHeaderTextOffset(), getTitleColor(), getTitleScale());
            GuiUtils.drawHorizontalLine(left + getHeaderLineInset(), right - getHeaderLineInset(),
                top + getHeaderLineOffset(), getHeaderLineColor());
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE && this.parent != null) {
            // ESC returns to the parent screen when available.
            showScreen(this.parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}
