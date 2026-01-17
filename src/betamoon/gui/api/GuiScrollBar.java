package betamoon.gui.api;

public final class GuiScrollBar {
    private final ScrollState state = new ScrollState();
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    private int scrollbarX;
    private int scrollbarTop;
    private int scrollbarBottom;
    private int thumbHeight;
    private int thumbY;
    private boolean draggingScrollbar;
    private int dragOffsetY;

    /**
     * Resets scroll position and clears dragging state.
     */
    public void reset() {
        state.reset();
        draggingScrollbar = false;
    }

    /**
     * Updates the visible bounds for the scrollable list.
     *
     * @param left left edge
     * @param top top edge
     * @param right right edge
     * @param bottom bottom edge
     */
    public void setBounds(int left, int top, int right, int bottom) {
        listLeft = left;
        listTop = top;
        listRight = right;
        listBottom = bottom;
    }

    /**
     * Updates scroll limits based on total content height.
     *
     * @param contentHeight total content height in pixels
     */
    public void updateContentHeight(int contentHeight) {
        int viewHeight = listBottom - listTop;
        state.updateContentHeight(contentHeight, viewHeight);
    }

    /**
     * Returns the current scroll offset.
     *
     * @return scroll offset in pixels
     */
    public int getScrollOffset() {
        return state.getScrollOffset();
    }

    /**
     * Scrolls the list using mouse wheel input when hovering the list bounds.
     *
     * @param mouseX mouse x in GUI space
     * @param mouseY mouse y in GUI space
     * @param wheelDelta wheel delta
     */
    public void handleMouseWheel(int mouseX, int mouseY, int wheelDelta) {
        if (wheelDelta == 0 || state.getMaxScroll() <= 0) {
            return;
        }
        if (!isMouseOverList(mouseX, mouseY)) {
            return;
        }
        state.scrollBy(-wheelDelta / 8);
    }

    /**
     * Updates the scroll offset while dragging the scrollbar thumb.
     *
     * @param mouseY mouse y in GUI space
     * @param mouseDown true when the left mouse button is held
     */
    public void handleMouseDrag(int mouseY, boolean mouseDown) {
        if (!mouseDown) {
            draggingScrollbar = false;
            return;
        }
        if (!draggingScrollbar) {
            return;
        }
        // Convert thumb drag distance into scroll offset.
        int trackHeight = scrollbarBottom - scrollbarTop - thumbHeight;
        if (trackHeight <= 0) {
            return;
        }
        int relative = mouseY - scrollbarTop - dragOffsetY;
        state.setScrollOffset((int) ((float) relative / (float) trackHeight * (float) state.getMaxScroll()));
    }

    /**
     * Starts dragging when the scrollbar thumb is clicked.
     *
     * @param mouseX mouse x in GUI space
     * @param mouseY mouse y in GUI space
     * @param button mouse button
     */
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || state.getMaxScroll() <= 0) {
            return;
        }
        if (isMouseOverScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            dragOffsetY = mouseY - thumbY;
        }
    }

    /**
     * Ends dragging on mouse release.
     *
     * @param button mouse button
     */
    public void mouseReleased(int button) {
        if (button == 0) {
            draggingScrollbar = false;
        }
    }

    /**
     * Draws the scrollbar thumb and track.
     *
     * @param contentHeight total content height in pixels
     */
    public void drawScrollbar(int contentHeight) {
        if (state.getMaxScroll() <= 0) {
            return;
        }
        // Compute thumb size and position from scroll state.
        scrollbarX = listRight - 4;
        scrollbarTop = listTop;
        scrollbarBottom = listBottom;
        int trackHeight = scrollbarBottom - scrollbarTop;
        thumbHeight = Math.max(12, (int) ((float) trackHeight * (float) trackHeight / (float) contentHeight));
        if (thumbHeight > trackHeight) {
            thumbHeight = trackHeight;
        }
        int thumbRange = trackHeight - thumbHeight;
        thumbY = scrollbarTop + (thumbRange > 0
            ? (int) ((float) thumbRange * (float) state.getScrollOffset() / (float) state.getMaxScroll())
            : 0);

        GuiUtils.drawRect(scrollbarX, scrollbarTop, scrollbarX + 4, scrollbarBottom, GuiColors.SCROLLBAR_TRACK);
        GuiUtils.drawRect(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, GuiColors.SCROLLBAR_THUMB);
    }

    private boolean isMouseOverList(int mouseX, int mouseY) {
        return mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom;
    }

    private boolean isMouseOverScrollbar(int mouseX, int mouseY) {
        return mouseX >= scrollbarX && mouseX <= scrollbarX + 4 && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
    }
}
