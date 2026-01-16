package betamoon.gui.api;

import betamoon.gui.api.GuiUtils;
import net.minecraft.src.GuiScreen;

/**
 * Base class for BetaMoon popup dialogs.
 */
public abstract class GuiPopupScreen extends GuiScreen {
    protected final GuiScreen parent;
    protected int panelLeft;
    protected int panelTop;
    protected int panelWidth;
    protected int panelHeight;

    protected GuiPopupScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        updatePanelGeometry();
        this.controlList.clear();
        initPopupGui();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updatePanelGeometry();
        drawPopupBackground(mouseX, mouseY, partialTicks);
        drawPopupFrame();
        drawPopupContents(mouseX, mouseY, partialTicks);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    protected abstract void initPopupGui();

    protected abstract String getPopupTitle();

    protected void drawPopupContents(int mouseX, int mouseY, float partialTicks) {
    }

    protected int getMaxPanelWidth() {
        return 360;
    }

    protected int getMaxPanelHeight() {
        return 200;
    }

    protected int getMinPanelHeight() {
        return 0;
    }

    protected int getPanelHorizontalMargin() {
        return 40;
    }

    protected int getPanelVerticalMargin() {
        return 80;
    }

    protected int getFramePadding() {
        return 4;
    }

    protected int getHeaderTextOffset() {
        return 8;
    }

    protected int getHeaderLineOffset() {
        return 24;
    }

    protected int getHeaderLineColor() {
        return 0xFFFFFFFF;
    }

    protected int getTitleColor() {
        return 0xFFFFFF;
    }

    protected float getTitleScale() {
        return 1.2F;
    }

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
        this.drawRect(left - padding, top - padding, right + padding, bottom + padding, 0xDD000000);
        this.drawRect(left, top, right, bottom, 0xFA1A1A1A);
        String title = getPopupTitle();
        if (title != null && !title.isEmpty()) {
            GuiUtils.drawScaledCenteredString(this.fontRenderer, title, this.width / 2,
                top + getHeaderTextOffset(), getTitleColor(), getTitleScale());
            GuiUtils.drawHorizontalLine(left + 10, right - 10, top + getHeaderLineOffset(), getHeaderLineColor());
        }
    }
}
