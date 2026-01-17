package betamoon.gui.api;

public abstract class GuiScrollablePanel extends GuiComponentBase {
    protected final GuiScrollPanel scrollPanel;

    protected GuiScrollablePanel(boolean allowHorizontal, boolean allowVertical) {
        this.scrollPanel = new GuiScrollPanel(allowHorizontal, allowVertical);
    }

    protected GuiScrollablePanel() {
        this.scrollPanel = new GuiScrollPanel();
    }

    protected void resetScroll() {
        scrollPanel.reset();
    }

    protected void setScrollBounds(int left, int top, int right, int bottom) {
        scrollPanel.setBounds(left, top, right, bottom);
    }

    protected void updateScrollContentSize(int contentWidth, int contentHeight) {
        scrollPanel.updateContentSize(contentWidth, contentHeight);
    }

    protected int getScrollOffsetX() {
        return scrollPanel.getScrollOffsetX();
    }

    protected int getScrollOffsetY() {
        return scrollPanel.getScrollOffsetY();
    }

    protected void drawScrollbar(int contentHeight) {
        scrollPanel.drawScrollbar(contentHeight);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (isMouseOver(mouseX, mouseY)) {
            return scrollPanel.mouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        scrollPanel.mouseReleased(button);
        return false;
    }

    public boolean mouseDragged(int mouseX, int mouseY, boolean mouseDown) {
        scrollPanel.handleMouseDrag(mouseY, mouseDown);
        return false;
    }

    public boolean mouseScrolled(int mouseX, int mouseY, int wheelDelta, boolean shiftDown) {
        if (isMouseOver(mouseX, mouseY)) {
            scrollPanel.handleMouseWheel(mouseX, mouseY, wheelDelta, shiftDown);
            return true;
        }
        return false;
    }
}
