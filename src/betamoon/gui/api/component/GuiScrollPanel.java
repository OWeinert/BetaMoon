package betamoon.gui.api.component;

import betamoon.gui.api.util.GuiColors;
import betamoon.gui.api.util.GuiUtils;

public abstract class GuiScrollPanel extends GuiComponentBase {
    private final ScrollState state = new ScrollState();
    private int scrollbarX;
    private int scrollbarTop;
    private int scrollbarBottom;
    private int thumbHeight;
    private int thumbY;
    private boolean draggingScrollbar;
    private int dragOffsetY;
    private EnumScrollMode scrollMode = EnumScrollMode.BOTH;

    protected GuiScrollPanel(EnumScrollMode scrollMode) {
        if (scrollMode != null) {
            this.scrollMode = scrollMode;
        }
    }

    protected GuiScrollPanel() {
    }

    protected void resetScroll() {
        state.reset();
        draggingScrollbar = false;
    }

    protected void setScrollMode(EnumScrollMode scrollMode) {
        if (scrollMode != null) {
            this.scrollMode = scrollMode;
        }
    }

    protected void updateScrollContentSize(int contentWidth, int contentHeight) {
        int viewWidth = right - left;
        int viewHeight = bottom - top;
        state.updateContentWidthX(contentWidth, viewWidth);
        state.updateContentHeightY(contentHeight, viewHeight);
    }

    protected int getScrollOffsetX() {
        return state.getScrollOffsetX();
    }

    protected int getScrollOffsetY() {
        return state.getScrollOffsetY();
    }

    protected void drawScrollbar(int contentHeight) {
        if (scrollMode == EnumScrollMode.HORIZONTAL || state.getMaxScrollY() <= 0) {
            return;
        }
        scrollbarX = right - 4;
        scrollbarTop = top;
        scrollbarBottom = bottom;
        int trackHeight = scrollbarBottom - scrollbarTop;
        thumbHeight = Math.max(12, (int) ((float) trackHeight * (float) trackHeight / (float) contentHeight));
        if (thumbHeight > trackHeight) {
            thumbHeight = trackHeight;
        }
        int thumbRange = trackHeight - thumbHeight;
        thumbY = scrollbarTop + (thumbRange > 0
            ? (int) ((float) thumbRange * (float) state.getScrollOffsetY() / (float) state.getMaxScrollY())
            : 0);

        GuiUtils.drawRect(scrollbarX, scrollbarTop, scrollbarX + 4, scrollbarBottom, GuiColors.SCROLLBAR_TRACK);
        GuiUtils.drawRect(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, GuiColors.SCROLLBAR_THUMB);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || scrollMode == EnumScrollMode.HORIZONTAL || state.getMaxScrollY() <= 0) {
            return false;
        }
        if (isMouseOverScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            dragOffsetY = mouseY - thumbY;
            return true;
        }
        return false;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0) {
            draggingScrollbar = false;
        }
        return false;
    }

    public boolean mouseDragged(int mouseX, int mouseY, boolean mouseDown) {
        if (!mouseDown) {
            draggingScrollbar = false;
            return false;
        }
        if (scrollMode == EnumScrollMode.HORIZONTAL || !draggingScrollbar || state.getMaxScrollY() <= 0) {
            return false;
        }
        int trackHeight = scrollbarBottom - scrollbarTop - thumbHeight;
        if (trackHeight <= 0) {
            return false;
        }
        int relative = mouseY - scrollbarTop - dragOffsetY;
        state.setScrollOffsetY((int) ((float) relative / (float) trackHeight * (float) state.getMaxScrollY()));
        return true;
    }

    public boolean mouseScrolled(int mouseX, int mouseY, int wheelDelta, boolean shiftDown) {
        if (wheelDelta == 0 || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int delta = -wheelDelta / 8;
        boolean allowHorizontal = scrollMode == EnumScrollMode.HORIZONTAL || scrollMode == EnumScrollMode.BOTH;
        boolean allowVertical = scrollMode == EnumScrollMode.VERTICAL || scrollMode == EnumScrollMode.BOTH;
        if (shiftDown && allowHorizontal && state.getMaxScrollX() > 0) {
            state.scrollByX(delta);
            return true;
        }
        if (allowVertical && state.getMaxScrollY() > 0) {
            state.scrollByY(delta);
            return true;
        }
        return false;
    }

    private boolean isMouseOverScrollbar(int mouseX, int mouseY) {
        return mouseX >= scrollbarX && mouseX <= scrollbarX + 4 && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
    }
}
