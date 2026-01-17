package betamoon.gui.api;

public final class ScrollState {
    private int scrollOffsetX;
    private int maxScrollX;
    private int scrollOffset;
    private int maxScroll;

    /**
     * Resets scroll position and clears limits.
     */
    public void reset() {
        scrollOffsetX = 0;
        maxScrollX = 0;
        scrollOffset = 0;
        maxScroll = 0;
    }

    /**
     * Updates horizontal scroll limits based on content width and view width.
     *
     * @param contentWidth total content width in pixels
     * @param viewWidth visible width in pixels
     */
    public void updateContentWidthX(int contentWidth, int viewWidth) {
        maxScrollX = Math.max(0, contentWidth - viewWidth);
        scrollOffsetX = GuiUtils.clamp(scrollOffsetX, 0, maxScrollX);
    }

    /**
     * Updates vertical scroll limits based on total content height and view height.
     *
     * @param contentHeight total content height in pixels
     * @param viewHeight visible height in pixels
     */
    public void updateContentHeightY(int contentHeight, int viewHeight) {
        maxScroll = Math.max(0, contentHeight - viewHeight);
        scrollOffset = GuiUtils.clamp(scrollOffset, 0, maxScroll);
    }

    /**
     * Returns the current horizontal scroll offset.
     */
    public int getScrollOffsetX() {
        return scrollOffsetX;
    }

    /**
     * Returns the current vertical scroll offset.
     *
     * @return scroll offset in pixels
     */
    public int getScrollOffsetY() {
        return scrollOffset;
    }

    /**
     * Returns the maximum horizontal scroll range.
     */
    public int getMaxScrollX() {
        return maxScrollX;
    }

    /**
     * Returns the maximum vertical scroll range.
     */
    public int getMaxScrollY() {
        return maxScroll;
    }

    /**
     * Sets the horizontal scroll offset, clamped to the current range.
     */
    public void setScrollOffsetX(int offset) {
        scrollOffsetX = GuiUtils.clamp(offset, 0, maxScrollX);
    }

    /**
     * Sets the vertical scroll offset, clamped to the current range.
     */
    public void setScrollOffsetY(int offset) {
        scrollOffset = GuiUtils.clamp(offset, 0, maxScroll);
    }

    /**
     * Sets both scroll offsets, clamped to the current ranges.
     */
    public void setScrollOffsets(int offsetX, int offsetY) {
        setScrollOffsetX(offsetX);
        setScrollOffsetY(offsetY);
    }

    /**
     * Adjusts the horizontal scroll offset by the provided delta.
     */
    public void scrollByX(int delta) {
        setScrollOffsetX(scrollOffsetX + delta);
    }

    /**
     * Adjusts the vertical scroll offset by the provided delta.
     */
    public void scrollByY(int delta) {
        setScrollOffsetY(scrollOffset + delta);
    }

    /**
     * Adjusts both scroll offsets by the provided deltas.
     */
    public void scrollBy(int deltaX, int deltaY) {
        setScrollOffsetX(scrollOffsetX + deltaX);
        setScrollOffsetY(scrollOffset + deltaY);
    }
}
