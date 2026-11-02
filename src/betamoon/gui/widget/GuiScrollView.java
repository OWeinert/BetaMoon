package betamoon.gui.widget;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiFocusViewport;
import betamoon.gui.framework.GuiFrameContent;
import betamoon.gui.graphics.GraphicsClip;
import betamoon.gui.graphics.GraphicsTransform;
import org.lwjgl.input.Keyboard;
import betamoon.luaapi.utils.LuaCallbackScope;

/** Clips and scrolls one retained element inside a fixed viewport. */
public final class GuiScrollView extends GuiContainer implements GuiFocusViewport {
    public enum Policy {
        VERTICAL, HORIZONTAL, BOTH
    }

    private static final int SCROLLBAR_SIZE = 4;
    private static final int MINIMUM_THUMB_SIZE = 12;

    @FunctionalInterface
    public interface PositionHandler {
        void propose(int x, int y);
    }

    private final GuiElement content;
    private PositionHandler positionHandler;
    private final ScrollbarHandle verticalHandle = new ScrollbarHandle(true);
    private final ScrollbarHandle horizontalHandle = new ScrollbarHandle(false);
    private Policy policy;
    private boolean scrollbarsVisible = true;
    private int contentWidth;
    private int contentHeight;
    private int scrollX;
    private int scrollY;
    private int maxScrollX;
    private int maxScrollY;
    private boolean draggingVertical;
    private boolean draggingHorizontal;
    private int dragOffset;
    private Rect arrangedBounds;
    private int arrangedScrollX;
    private int arrangedScrollY;
    private int arrangedMaxX;
    private int arrangedMaxY;

    public GuiScrollView(GuiElement content, Policy policy) {
        this(content, policy, false);
    }

    public GuiScrollView(GuiElement content, Policy policy, boolean focusable) {
        setFocusable(focusable);
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

    public void setScrollbarsVisible(boolean visible) {
        LuaCallbackScope.requireUiStateWrite();
        if (scrollbarsVisible != visible) {
            scrollbarsVisible = visible;
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

    public Runnable handlePositionWith(PositionHandler handler) {
        LuaCallbackScope.requireUiStateWrite();
        if (handler == null || positionHandler != null) {
            throw new IllegalStateException("Install a scroll position handler once");
        }
        positionHandler = handler;
        return () -> {
            if (positionHandler == handler) {
                positionHandler = null;
            }
        };
    }

    public void setScrollPosition(int x, int y) {
        LuaCallbackScope.requireUiStateWrite();
        int resolvedX = clamp(x, 0, maxScrollX);
        int resolvedY = clamp(y, 0, maxScrollY);
        if (scrollX == resolvedX && scrollY == resolvedY) {
            return;
        }
        if (positionHandler != null) {
            positionHandler.propose(resolvedX, resolvedY);
        } else {
            publishScrollPosition(resolvedX, resolvedY);
        }
    }

    /**
     * Owner publication/restoration; limits are applied by the next arrangement.
     */
    public void publishScrollPosition(int x, int y) {
        LuaCallbackScope.requireUiStateWrite();
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("Scroll positions cannot be negative");
        }
        if (scrollX != x || scrollY != y) {
            scrollX = x;
            scrollY = y;
            requestLayout();
        }
    }

    public void resetScroll() {
        setScrollPosition(0, 0);
        draggingVertical = false;
        draggingHorizontal = false;
        requestLayout();
    }

    /**
     * Reveals an element using its current arranged screen bounds, preserving
     * horizontal scroll.
     */
    public void reveal(Rect bounds) {
        if (bounds.getTop() < getTop()) {
            setScrollPosition(scrollX, scrollY + bounds.getTop() - getTop());
        } else if (bounds.getBottom() > getBottom()) {
            int delta = bounds.getHeight() > getHeight()
                    ? bounds.getTop() - getTop()
                    : bounds.getBottom() - getBottom();
            setScrollPosition(scrollX, scrollY + delta);
        }
    }

    @Override
    public GraphicsClip revealFocus(GraphicsClip bounds) {
        int oldX = scrollX;
        int oldY = scrollY;
        double x = bounds.left < getLeft()
                ? bounds.left - getLeft()
                : bounds.right > getRight() ? Math.min(bounds.left - getLeft(), bounds.right - getRight()) : 0;
        double y = bounds.top < getTop()
                ? bounds.top - getTop()
                : bounds.bottom > getBottom() ? Math.min(bounds.top - getTop(), bounds.bottom - getBottom()) : 0;
        setScrollPosition((int) Math.max(0, Math.min(maxScrollX, scrollX + x)),
                (int) Math.max(0, Math.min(maxScrollY, scrollY + y)));
        return new GraphicsClip(Math.max(getLeft(), Math.min(getRight(), bounds.left + oldX - scrollX)),
                Math.max(getTop(), Math.min(getBottom(), bounds.top + oldY - scrollY)),
                Math.max(getLeft(), Math.min(getRight(), bounds.right + oldX - scrollX)),
                Math.max(getTop(), Math.min(getBottom(), bounds.bottom + oldY - scrollY)));
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        updateLimits();
        if (arrangedBounds != null) {
            if (!arrangedBounds.equals(getBounds()) || arrangedMaxX != maxScrollX || arrangedMaxY != maxScrollY
                    || verticalHandle.dragging != null && !draggingVertical
                    || horizontalHandle.dragging != null && !draggingHorizontal) {
                context.cancelGesturesIn(verticalHandle);
                context.cancelGesturesIn(horizontalHandle);
            }
            if (arrangedScrollX != scrollX || arrangedScrollY != scrollY) {
                context.cancelGesturesIn(content);
            }
        }
        arrangedBounds = getBounds();
        arrangedScrollX = scrollX;
        arrangedScrollY = scrollY;
        arrangedMaxX = maxScrollX;
        arrangedMaxY = maxScrollY;
        content.arrange(context, Rect.fromPositionAndSize(getLeft() - scrollX, getTop() - scrollY,
                Math.max(contentWidth, getWidth()), Math.max(contentHeight, getHeight())));
        boolean showVertical = scrollbarsVisible && maxScrollY > 0 && policy != Policy.HORIZONTAL;
        if (!showVertical) {
            draggingVertical = false;
        }
        verticalHandle.setVisible(showVertical);
        verticalHandle.arrange(context, verticalTrack());
        boolean showHorizontal = scrollbarsVisible && maxScrollX > 0 && policy != Policy.VERTICAL;
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
            drawScrollbars(context);
        } finally {
            context.getRenderer().popClip();
        }
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        if (event.getType() == GuiInputEvent.Type.SCROLL && contains(event.getMouseX(), event.getMouseY())) {
            int delta = (int) (-(long) event.getWheelDelta() / 8);
            int previousX = scrollX;
            int previousY = scrollY;
            boolean horizontalAllowed = policy == Policy.HORIZONTAL || policy == Policy.BOTH;
            boolean verticalAllowed = policy == Policy.VERTICAL || policy == Policy.BOTH;
            if (event.isShiftDown() && horizontalAllowed && maxScrollX > 0) {
                setScrollPosition(wheelOffset(scrollX, delta, maxScrollX), scrollY);
                return previousX != scrollX;
            }
            if (verticalAllowed && maxScrollY > 0) {
                setScrollPosition(scrollX, wheelOffset(scrollY, delta, maxScrollY));
                return previousY != scrollY;
            }
            if (horizontalAllowed && maxScrollX > 0) {
                setScrollPosition(wheelOffset(scrollX, delta, maxScrollX), scrollY);
                return previousX != scrollX;
            }
        }
        if (event.getType() == GuiInputEvent.Type.KEY_TYPED) {
            int x = scrollX;
            int y = scrollY;
            switch (event.getKeyCode()) {
                case Keyboard.KEY_LEFT:
                    x = wheelOffset(x, -16, maxScrollX);
                    break;
                case Keyboard.KEY_RIGHT:
                    x = wheelOffset(x, 16, maxScrollX);
                    break;
                case Keyboard.KEY_UP:
                    y = wheelOffset(y, -16, maxScrollY);
                    break;
                case Keyboard.KEY_DOWN:
                    y = wheelOffset(y, 16, maxScrollY);
                    break;
                case Keyboard.KEY_PRIOR:
                case Keyboard.KEY_NEXT:
                    int direction = event.getKeyCode() == Keyboard.KEY_PRIOR ? -1 : 1;
                    if (policy == Policy.HORIZONTAL
                            || policy == Policy.BOTH && (event.isShiftDown() || maxScrollY == 0)) {
                        x = wheelOffset(x, direction * getWidth(), maxScrollX);
                    } else {
                        y = wheelOffset(y, direction * getHeight(), maxScrollY);
                    }
                    break;
                case Keyboard.KEY_HOME:
                case Keyboard.KEY_END:
                    boolean end = event.getKeyCode() == Keyboard.KEY_END;
                    if (policy == Policy.HORIZONTAL
                            || policy == Policy.BOTH && (event.isShiftDown() || maxScrollY == 0)) {
                        x = end ? maxScrollX : 0;
                    } else {
                        y = end ? maxScrollY : 0;
                    }
                    break;
                default:
                    return false;
            }
            int previousX = scrollX;
            int previousY = scrollY;
            setScrollPosition(x, y);
            return previousX != scrollX || previousY != scrollY;
        }
        return false;
    }

    private static int wheelOffset(int current, int delta, int maximum) {
        return (int) Math.max(0, Math.min((long) current + delta, maximum));
    }

    private void updateLimits() {
        maxScrollX = policy == Policy.VERTICAL ? 0 : Math.max(0, contentWidth - getWidth());
        maxScrollY = policy == Policy.HORIZONTAL ? 0 : Math.max(0, contentHeight - getHeight());
        scrollX = clamp(scrollX, 0, maxScrollX);
        scrollY = clamp(scrollY, 0, maxScrollY);
    }

    private void drawScrollbars(GuiContext context) {
        if (!scrollbarsVisible) {
            return;
        }
        if (maxScrollY > 0 && policy != Policy.HORIZONTAL) {
            Rect track = verticalTrack();
            Rect thumb = verticalThumb();
            context.getRenderer().drawRect(track, context.getRenderer().getTheme().scrollbarTrack);
            context.getRenderer().drawRect(thumb, 0xFF555555);
            context.getRenderer().drawRect(thumb.getLeft(), thumb.getTop(), thumb.getRight() - 1, thumb.getBottom() - 1,
                    context.getRenderer().getTheme().scrollbarThumb);
        }
        if (maxScrollX > 0 && policy != Policy.VERTICAL) {
            Rect track = horizontalTrack();
            Rect thumb = horizontalThumb();
            context.getRenderer().drawRect(track, context.getRenderer().getTheme().scrollbarTrack);
            context.getRenderer().drawRect(thumb, 0xFF555555);
            context.getRenderer().drawRect(thumb.getLeft(), thumb.getTop(), thumb.getRight() - 1, thumb.getBottom() - 1,
                    context.getRenderer().getTheme().scrollbarThumb);
        }
    }

    private Rect verticalTrack() {
        return new Rect(Math.max(getLeft(), getRight() - SCROLLBAR_SIZE), getTop(), getRight(),
                getBottom() - (maxScrollX > 0 ? SCROLLBAR_SIZE : 0));
    }

    private Rect verticalThumb() {
        Rect track = verticalTrack();
        int thumbHeight = thumbSize(track.getHeight(), getHeight(), contentHeight);
        int range = track.getHeight() - thumbHeight;
        int offset = maxScrollY == 0 ? 0 : (int) ((long) range * scrollY / maxScrollY);
        return Rect.fromPositionAndSize(track.getLeft(), track.getTop() + offset, track.getWidth(), thumbHeight);
    }

    private Rect horizontalTrack() {
        return new Rect(getLeft(), Math.max(getTop(), getBottom() - SCROLLBAR_SIZE),
                getRight() - (maxScrollY > 0 ? SCROLLBAR_SIZE : 0), getBottom());
    }

    private Rect horizontalThumb() {
        Rect track = horizontalTrack();
        int thumbWidth = thumbSize(track.getWidth(), getWidth(), contentWidth);
        int range = track.getWidth() - thumbWidth;
        int offset = maxScrollX == 0 ? 0 : (int) ((long) range * scrollX / maxScrollX);
        return Rect.fromPositionAndSize(track.getLeft() + offset, track.getTop(), thumbWidth, track.getHeight());
    }

    private static int thumbSize(int trackSize, int viewportSize, int totalSize) {
        if (trackSize <= 0 || totalSize <= 0) {
            return Math.max(0, trackSize);
        }
        int size = Math.max(MINIMUM_THUMB_SIZE, (int) ((long) trackSize * viewportSize / totalSize));
        return Math.min(trackSize, size);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * Input layer kept above scroll content so content cannot consume scrollbar
     * drags.
     */
    private static final class ScrollbarFrame implements GuiFrameContent {
        final Rect track;
        final Rect thumb;
        final int maximum;
        final int position;

        ScrollbarFrame(Rect track, Rect thumb, int maximum, int position) {
            this.track = track;
            this.thumb = thumb;
            this.maximum = maximum;
            this.position = position;
        }

        public boolean contains(double x, double y) {
            return true;
        }
    }

    private final class ScrollbarHandle extends GuiElement {
        private final boolean vertical;
        private ScrollbarFrame dragging;
        private GraphicsTransform dragTransform;

        private ScrollbarHandle(boolean vertical) {
            this.vertical = vertical;
        }

        @Override
        protected GuiFrameContent sampleFrameContent() {
            return new ScrollbarFrame(vertical ? verticalTrack() : horizontalTrack(),
                    vertical ? verticalThumb() : horizontalThumb(), vertical ? maxScrollY : maxScrollX,
                    vertical ? scrollY : scrollX);
        }

        @Override
        protected boolean onInput(GuiContext context, GuiInputEvent event) {
            if (event.getType() == GuiInputEvent.Type.POINTER_DOWN && event.getButton() == 0) {
                ScrollbarFrame frame = (ScrollbarFrame) context.getFrameContent();
                if (frame == null || frame.maximum != (vertical ? maxScrollY : maxScrollX)
                        || frame.position != (vertical ? scrollY : scrollX)
                        || !frame.track.equals(vertical ? verticalTrack() : horizontalTrack())) {
                    return true;
                }
                if (frame.thumb.contains(event.getMouseX(), event.getMouseY())) {
                    dragging = frame;
                    dragTransform = context.getElementTransform();
                    draggingVertical = vertical;
                    draggingHorizontal = !vertical;
                    dragOffset = vertical
                            ? event.getMouseY() - frame.thumb.getTop()
                            : event.getMouseX() - frame.thumb.getLeft();
                    capturePointer(context);
                } else {
                    boolean before = vertical
                            ? event.getMouseY() < frame.thumb.getTop()
                            : event.getMouseX() < frame.thumb.getLeft();
                    int page = vertical ? GuiScrollView.this.getHeight() : GuiScrollView.this.getWidth();
                    int next = wheelOffset(frame.position, before ? -page : page, frame.maximum);
                    setScrollPosition(vertical ? scrollX : next, vertical ? next : scrollY);
                }
                if (GuiScrollView.this.isFocusable()) {
                    context.requestFocus(GuiScrollView.this);
                }
                return true;
            }
            if (event.getType() == GuiInputEvent.Type.POINTER_DRAG && dragging != null) {
                GraphicsTransform current = context.getElementTransform();
                if (!event.isMouseDown() || current.x != dragTransform.x || current.y != dragTransform.y
                        || current.scaleX != dragTransform.scaleX || current.scaleY != dragTransform.scaleY) {
                    return endDrag(context);
                }
                int range = vertical
                        ? dragging.track.getHeight() - dragging.thumb.getHeight()
                        : dragging.track.getWidth() - dragging.thumb.getWidth();
                int position = vertical
                        ? event.getMouseY() - dragging.track.getTop() - dragOffset
                        : event.getMouseX() - dragging.track.getLeft() - dragOffset;
                if (range > 0) {
                    int next = (int) ((long) clamp(position, 0, range) * dragging.maximum / range);
                    setScrollPosition(vertical ? scrollX : next, vertical ? next : scrollY);
                }
                return true;
            }
            if (event.getType() == GuiInputEvent.Type.POINTER_UP
                    || event.getType() == GuiInputEvent.Type.POINTER_CANCEL) {
                return endDrag(context);
            }
            return false;
        }

        private boolean endDrag(GuiContext context) {
            boolean active = dragging != null;
            dragging = null;
            dragTransform = null;
            if (vertical) {
                draggingVertical = false;
            } else {
                draggingHorizontal = false;
            }
            releasePointer(context);
            return active;
        }
    }
}
