package betamoon.gui.widget;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiInputEvent;

/** Clips and scrolls one retained element inside a fixed viewport. */
public final class GuiScrollView extends GuiContainer {
    public enum Policy {
        VERTICAL,
        HORIZONTAL,
        BOTH
    }

    private static final int SCROLLBAR_SIZE = 4;
    private static final int MINIMUM_THUMB_SIZE = 12;

    private final GuiElement content;
    private final ScrollbarHandle verticalHandle = new ScrollbarHandle(true);
    private final ScrollbarHandle horizontalHandle = new ScrollbarHandle(false);
    private Policy policy;
    private int contentWidth;
    private int contentHeight;
    private int scrollX;
    private int scrollY;
    private int maxScrollX;
    private int maxScrollY;
    private boolean draggingVertical;
    private boolean draggingHorizontal;
    private int dragOffset;

    public GuiScrollView(GuiElement content, Policy policy) {
        if (content == null) {
            throw new IllegalArgumentException("Scroll content cannot be null");
        }
        this.content = add(content);
        add(verticalHandle);
        add(horizontalHandle);
        this.policy = policy == null ? Policy.BOTH : policy;
    }

    public GuiElement getContent() {
        return content;
    }

    public void setPolicy(Policy policy) {
        if (policy != null && this.policy != policy) {
            this.policy = policy;
            requestLayout();
        }
    }

    public void setContentSize(int width, int height) {
        int resolvedWidth = Math.max(0, width);
        int resolvedHeight = Math.max(0, height);
        if (resolvedWidth == contentWidth && resolvedHeight == contentHeight) {
            return;
        }
        contentWidth = resolvedWidth;
        contentHeight = resolvedHeight;
        requestLayout();
    }

    public int getScrollX() {
        return scrollX;
    }

    public int getScrollY() {
        return scrollY;
    }

    public void setScrollPosition(int x, int y) {
        int resolvedX = clamp(x, 0, maxScrollX);
        int resolvedY = clamp(y, 0, maxScrollY);
        if (scrollX == resolvedX && scrollY == resolvedY) {
            return;
        }
        scrollX = resolvedX;
        scrollY = resolvedY;
        requestLayout();
    }

    public void resetScroll() {
        setScrollPosition(0, 0);
        draggingVertical = false;
        draggingHorizontal = false;
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        updateLimits();
        content.arrange(context, Rect.fromPositionAndSize(getLeft() - scrollX, getTop() - scrollY,
                Math.max(contentWidth, getWidth()), Math.max(contentHeight, getHeight())));
        boolean showVertical = maxScrollY > 0 && policy != Policy.HORIZONTAL;
        if (!showVertical) {
            draggingVertical = false;
        }
        verticalHandle.setVisible(showVertical);
        verticalHandle.arrange(context, verticalTrack());
        boolean showHorizontal = maxScrollX > 0 && policy != Policy.VERTICAL;
        if (!showHorizontal) {
            draggingHorizontal = false;
        }
        horizontalHandle.setVisible(showHorizontal);
        horizontalHandle.arrange(context, horizontalTrack());
    }

    @Override
    protected void renderElement(GuiContext context) {
        context.getRenderer().pushClip(getBounds());
        try {
            content.render(context);
        } finally {
            context.getRenderer().popClip();
        }
        drawScrollbars(context);
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        if (event.getType() == GuiInputEvent.Type.SCROLL && contains(event.getMouseX(), event.getMouseY())) {
            int delta = -event.getWheelDelta() / 8;
            boolean horizontalAllowed = policy == Policy.HORIZONTAL || policy == Policy.BOTH;
            boolean verticalAllowed = policy == Policy.VERTICAL || policy == Policy.BOTH;
            if (event.isShiftDown() && horizontalAllowed && maxScrollX > 0) {
                setScrollPosition(scrollX + delta, scrollY);
                return true;
            }
            if (verticalAllowed && maxScrollY > 0) {
                setScrollPosition(scrollX, scrollY + delta);
                return true;
            }
            if (horizontalAllowed && maxScrollX > 0) {
                setScrollPosition(scrollX + delta, scrollY);
                return true;
            }
        }
        return false;
    }

    private void updateLimits() {
        maxScrollX = policy == Policy.VERTICAL ? 0 : Math.max(0, contentWidth - getWidth());
        maxScrollY = policy == Policy.HORIZONTAL ? 0 : Math.max(0, contentHeight - getHeight());
        scrollX = clamp(scrollX, 0, maxScrollX);
        scrollY = clamp(scrollY, 0, maxScrollY);
    }

    private void drawScrollbars(GuiContext context) {
        if (maxScrollY > 0 && policy != Policy.HORIZONTAL) {
            Rect track = verticalTrack();
            Rect thumb = verticalThumb();
            context.getRenderer().drawRect(track, context.getRenderer().getTheme().scrollbarTrack);
            context.getRenderer().drawRect(thumb, context.getRenderer().getTheme().scrollbarThumb);
        }
        if (maxScrollX > 0 && policy != Policy.VERTICAL) {
            Rect track = horizontalTrack();
            Rect thumb = horizontalThumb();
            context.getRenderer().drawRect(track, context.getRenderer().getTheme().scrollbarTrack);
            context.getRenderer().drawRect(thumb, context.getRenderer().getTheme().scrollbarThumb);
        }
    }

    private boolean startVerticalDrag(int mouseX, int mouseY) {
        if (maxScrollY <= 0 || policy == Policy.HORIZONTAL || !verticalThumb().contains(mouseX, mouseY)) {
            return false;
        }
        draggingVertical = true;
        draggingHorizontal = false;
        dragOffset = mouseY - verticalThumb().getTop();
        return true;
    }

    private boolean startHorizontalDrag(int mouseX, int mouseY) {
        if (maxScrollX <= 0 || policy == Policy.VERTICAL || !horizontalThumb().contains(mouseX, mouseY)) {
            return false;
        }
        draggingHorizontal = true;
        draggingVertical = false;
        dragOffset = mouseX - horizontalThumb().getLeft();
        return true;
    }

    private void dragVertical(int mouseY) {
        Rect track = verticalTrack();
        Rect thumb = verticalThumb();
        int range = track.getHeight() - thumb.getHeight();
        if (range <= 0) {
            return;
        }
        int relative = clamp(mouseY - track.getTop() - dragOffset, 0, range);
        scrollY = relative * maxScrollY / range;
    }

    private void dragHorizontal(int mouseX) {
        Rect track = horizontalTrack();
        Rect thumb = horizontalThumb();
        int range = track.getWidth() - thumb.getWidth();
        if (range <= 0) {
            return;
        }
        int relative = clamp(mouseX - track.getLeft() - dragOffset, 0, range);
        scrollX = relative * maxScrollX / range;
    }

    private Rect verticalTrack() {
        return new Rect(getRight() - SCROLLBAR_SIZE, getTop(), getRight(),
                getBottom() - (maxScrollX > 0 ? SCROLLBAR_SIZE : 0));
    }

    private Rect verticalThumb() {
        Rect track = verticalTrack();
        int thumbHeight = thumbSize(track.getHeight(), getHeight(), contentHeight);
        int range = track.getHeight() - thumbHeight;
        int offset = maxScrollY == 0 ? 0 : range * scrollY / maxScrollY;
        return Rect.fromPositionAndSize(track.getLeft(), track.getTop() + offset, track.getWidth(), thumbHeight);
    }

    private Rect horizontalTrack() {
        return new Rect(getLeft(), getBottom() - SCROLLBAR_SIZE,
                getRight() - (maxScrollY > 0 ? SCROLLBAR_SIZE : 0), getBottom());
    }

    private Rect horizontalThumb() {
        Rect track = horizontalTrack();
        int thumbWidth = thumbSize(track.getWidth(), getWidth(), contentWidth);
        int range = track.getWidth() - thumbWidth;
        int offset = maxScrollX == 0 ? 0 : range * scrollX / maxScrollX;
        return Rect.fromPositionAndSize(track.getLeft() + offset, track.getTop(), thumbWidth, track.getHeight());
    }

    private static int thumbSize(int trackSize, int viewportSize, int totalSize) {
        if (trackSize <= 0 || totalSize <= 0) {
            return Math.max(0, trackSize);
        }
        int size = Math.max(MINIMUM_THUMB_SIZE, trackSize * viewportSize / totalSize);
        return Math.min(trackSize, size);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Input layer kept above scroll content so content cannot consume scrollbar drags. */
    private final class ScrollbarHandle extends GuiElement {
        private final boolean vertical;

        private ScrollbarHandle(boolean vertical) {
            this.vertical = vertical;
        }

        @Override
        protected boolean onInput(GuiContext context, GuiInputEvent event) {
            if (event.getType() == GuiInputEvent.Type.POINTER_DOWN && event.getButton() == 0) {
                boolean started = vertical ? startVerticalDrag(event.getMouseX(), event.getMouseY())
                        : startHorizontalDrag(event.getMouseX(), event.getMouseY());
                if (started) {
                    capturePointer(context);
                }
                return started;
            }
            if (event.getType() == GuiInputEvent.Type.POINTER_DRAG) {
                if (!event.isMouseDown()) {
                    return endDrag(context);
                }
                if (vertical && draggingVertical) {
                    dragVertical(event.getMouseY());
                    requestLayout();
                    return true;
                }
                if (!vertical && draggingHorizontal) {
                    dragHorizontal(event.getMouseX());
                    requestLayout();
                    return true;
                }
            }
            if (event.getType() == GuiInputEvent.Type.POINTER_UP) {
                return endDrag(context);
            }
            return false;
        }

        private boolean endDrag(GuiContext context) {
            boolean wasDragging = vertical ? draggingVertical : draggingHorizontal;
            if (vertical) {
                draggingVertical = false;
            } else {
                draggingHorizontal = false;
            }
            releasePointer(context);
            return wasDragging;
        }
    }
}
