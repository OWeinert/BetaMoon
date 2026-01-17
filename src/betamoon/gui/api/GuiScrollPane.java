package betamoon.gui.api;

public final class GuiScrollPane {
    private final ScrollState state = new ScrollState();
    private int left;
    private int right;
    private int top;
    private int bottom;
    private int scrollbarX;
    private int scrollbarTop;
    private int scrollbarBottom;
    private int thumbHeight;
    private int thumbY;
    private boolean draggingScrollbar;
    private int dragOffsetY;
    private boolean allowHorizontal = true;
    private boolean allowVertical = true;

    public GuiScrollPane(boolean allowHorizontal, boolean allowVertical) {
        this.allowHorizontal = allowHorizontal;
        this.allowVertical = allowVertical;
    }

    public GuiScrollPane() {
    }

    /**
     * Resets scroll offsets and limits.
     */
    public void reset() {
        state.reset();
        draggingScrollbar = false;
    }

    /**
     * Updates the visible bounds for the scrollable pane.
     *
     * @param left left edge
     * @param top top edge
     * @param right right edge
     * @param bottom bottom edge
     */
    public void setBounds(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    /**
     * Controls whether horizontal and/or vertical scrolling is allowed.
     */
    public void setScrollEnabled(boolean allowHorizontal, boolean allowVertical) {
        this.allowHorizontal = allowHorizontal;
        this.allowVertical = allowVertical;
    }

    /**
     * Updates scroll limits from the current content size.
     *
     * @param contentWidth total content width in pixels
     * @param contentHeight total content height in pixels
     */
    public void updateContentSize(int contentWidth, int contentHeight) {
        int viewWidth = right - left;
        int viewHeight = bottom - top;
        state.updateContentWidthX(contentWidth, viewWidth);
        state.updateContentHeightY(contentHeight, viewHeight);
    }

    /**
     * Returns the current horizontal scroll offset.
     */
    public int getScrollOffsetX() {
        return state.getScrollOffsetX();
    }

    /**
     * Returns the current vertical scroll offset.
     */
    public int getScrollOffsetY() {
        return state.getScrollOffsetY();
    }

    /**
     * Handles mouse wheel input. Shift + wheel scrolls horizontally when enabled.
     *
     * @param mouseX mouse x in GUI space
     * @param mouseY mouse y in GUI space
     * @param wheelDelta mouse wheel delta
     * @param shiftDown true when shift is held
     */
    public void handleMouseWheel(int mouseX, int mouseY, int wheelDelta, boolean shiftDown) {
        if (wheelDelta == 0) {
            return;
        }
        if (!isMouseOver(mouseX, mouseY)) {
            return;
        }
        int delta = -wheelDelta / 8;
        if (shiftDown && allowHorizontal && state.getMaxScrollX() > 0) {
            state.scrollByX(delta);
        } else if (allowVertical && state.getMaxScrollY() > 0) {
            state.scrollByY(delta);
        }
    }

    /**
     * Updates the scroll offset while dragging the vertical scrollbar thumb.
     *
     * @param mouseY mouse y in GUI space
     * @param mouseDown true when the left mouse button is held
     */
    public void handleMouseDrag(int mouseY, boolean mouseDown) {
        if (!mouseDown) {
            draggingScrollbar = false;
            return;
        }
        if (!draggingScrollbar || !allowVertical || state.getMaxScrollY() <= 0) {
            return;
        }
        int trackHeight = scrollbarBottom - scrollbarTop - thumbHeight;
        if (trackHeight <= 0) {
            return;
        }
        int relative = mouseY - scrollbarTop - dragOffsetY;
        state.setScrollOffsetY((int) ((float) relative / (float) trackHeight * (float) state.getMaxScrollY()));
    }

    /**
     * Starts dragging when the vertical scrollbar thumb is clicked.
     *
     * @param mouseX mouse x in GUI space
     * @param mouseY mouse y in GUI space
     * @param button mouse button
     */
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !allowVertical || state.getMaxScrollY() <= 0) {
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
     * Draws the vertical scrollbar thumb and track.
     *
     * @param contentHeight total content height in pixels
     */
    public void drawScrollbar(int contentHeight) {
        if (!allowVertical || state.getMaxScrollY() <= 0) {
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

    private boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    private boolean isMouseOverScrollbar(int mouseX, int mouseY) {
        return mouseX >= scrollbarX && mouseX <= scrollbarX + 4 && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
    }
}
