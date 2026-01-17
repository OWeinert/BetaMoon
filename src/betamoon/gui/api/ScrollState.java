package betamoon.gui.api;

public final class ScrollState {
    private int scrollOffset;
    private int maxScroll;

    /**
     * Resets scroll position and clears limits.
     */
    public void reset() {
        scrollOffset = 0;
        maxScroll = 0;
    }

    /**
     * Updates scroll limits based on total content height and view height.
     *
     * @param contentHeight total content height in pixels
     * @param viewHeight visible height in pixels
     */
    public void updateContentHeight(int contentHeight, int viewHeight) {
        maxScroll = Math.max(0, contentHeight - viewHeight);
        scrollOffset = GuiUtils.clamp(scrollOffset, 0, maxScroll);
    }

    /**
     * Returns the current scroll offset.
     *
     * @return scroll offset in pixels
     */
    public int getScrollOffset() {
        return scrollOffset;
    }

    /**
     * Returns the maximum scroll range.
     */
    public int getMaxScroll() {
        return maxScroll;
    }

    /**
     * Adjusts the scroll offset by the provided delta.
     */
    public void scrollBy(int delta) {
        setScrollOffset(scrollOffset + delta);
    }

    /**
     * Sets the scroll offset, clamped to the current range.
     */
    public void setScrollOffset(int offset) {
        scrollOffset = GuiUtils.clamp(offset, 0, maxScroll);
    }
}
