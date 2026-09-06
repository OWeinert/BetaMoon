package betamoon.gui.api.component;

import betamoon.gui.api.util.GuiColors;
import betamoon.gui.api.util.GuiUtils;
import net.minecraft.src.FontRenderer;

/**
 * Clips and scrolls a single GUI component inside this panel's bounds.
 */
public class GuiScrollPanel extends GuiComponentBase {
    private final IGuiComponent content;
    private final ScrollState state = new ScrollState();
    private int contentWidth;
    private int contentHeight;
    private int screenWidth;
    private int screenHeight;
    private int displayWidth;
    private int displayHeight;
    private int scrollbarX;
    private int scrollbarTop;
    private int scrollbarBottom;
    private int thumbHeight;
    private int thumbY;
    private boolean draggingScrollbar;
    private int dragOffsetY;
    private EnumScrollMode scrollMode = EnumScrollMode.BOTH;

    public GuiScrollPanel(IGuiComponent content, EnumScrollMode scrollMode) {
        if (content == null) {
            throw new IllegalArgumentException("Scroll panel content cannot be null");
        }
        this.content = content;
        setScrollMode(scrollMode);
    }

    public GuiScrollPanel(IGuiComponent content) {
        this(content, EnumScrollMode.BOTH);
    }

    public IGuiComponent getContent() {
        return content;
    }

    public void resetScroll() {
        state.reset();
        draggingScrollbar = false;
        updateContentBounds();
    }

    public void setScrollMode(EnumScrollMode scrollMode) {
        if (scrollMode != null) {
            this.scrollMode = scrollMode;
        }
    }

    /**
     * Sets the full, unclipped size of the owned content component.
     */
    public void setContentSize(int contentWidth, int contentHeight) {
        this.contentWidth = Math.max(0, contentWidth);
        this.contentHeight = Math.max(0, contentHeight);
        updateScrollLimits();
        updateContentBounds();
    }

    /**
     * Updates the GUI and display sizes required to convert the viewport to a GL scissor rectangle.
     */
    public void setDisplayMetrics(int screenWidth, int screenHeight, int displayWidth, int displayHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
    }

    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        updateScrollLimits();
        updateContentBounds();
    }

    public void layout(int screenWidth, int screenHeight) {
        updateScrollLimits();
        updateContentBounds();
        content.layout(screenWidth, screenHeight);
    }

    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
        updateScrollLimits();
        updateContentBounds();

        boolean canClip = screenWidth > 0 && screenHeight > 0 && displayWidth > 0 && displayHeight > 0;
        if (canClip) {
            GuiUtils.beginScissor(left, top, right, bottom, screenWidth, screenHeight, displayWidth, displayHeight);
        }
        content.draw(font, mouseX, mouseY, partialTicks);
        if (canClip) {
            GuiUtils.endScissor();
        }

        drawScrollbar();
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        updateScrollbarGeometry();
        if (button == 0 && isMouseOverScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            dragOffsetY = mouseY - thumbY;
            return true;
        }
        return content.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        boolean wasDragging = draggingScrollbar;
        if (button == 0) {
            draggingScrollbar = false;
        }
        return content.mouseReleased(mouseX, mouseY, button) || wasDragging;
    }

    public boolean mouseDragged(int mouseX, int mouseY, boolean mouseDown) {
        if (!mouseDown) {
            boolean wasDragging = draggingScrollbar;
            draggingScrollbar = false;
            return content.mouseDragged(mouseX, mouseY, false) || wasDragging;
        }
        if (draggingScrollbar && scrollMode != EnumScrollMode.HORIZONTAL && state.getMaxScrollY() > 0) {
            int trackHeight = scrollbarBottom - scrollbarTop - thumbHeight;
            if (trackHeight <= 0) {
                return false;
            }
            int relative = mouseY - scrollbarTop - dragOffsetY;
            state.setScrollOffsetY((int) ((float) relative / (float) trackHeight * (float) state.getMaxScrollY()));
            updateContentBounds();
            return true;
        }
        return content.mouseDragged(mouseX, mouseY, true);
    }

    public boolean mouseScrolled(int mouseX, int mouseY, int wheelDelta, boolean shiftDown) {
        if (wheelDelta == 0 || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (content.mouseScrolled(mouseX, mouseY, wheelDelta, shiftDown)) {
            return true;
        }
        int delta = -wheelDelta / 8;
        boolean allowHorizontal = scrollMode == EnumScrollMode.HORIZONTAL || scrollMode == EnumScrollMode.BOTH;
        boolean allowVertical = scrollMode == EnumScrollMode.VERTICAL || scrollMode == EnumScrollMode.BOTH;
        if (shiftDown && allowHorizontal && state.getMaxScrollX() > 0) {
            state.scrollByX(delta);
            updateContentBounds();
            return true;
        }
        if (allowVertical && state.getMaxScrollY() > 0) {
            state.scrollByY(delta);
            updateContentBounds();
            return true;
        }
        return false;
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        return content.keyTyped(typedChar, keyCode);
    }

    private void updateScrollLimits() {
        int viewWidth = Math.max(0, right - left);
        int viewHeight = Math.max(0, bottom - top);
        state.updateContentWidthX(contentWidth, viewWidth);
        state.updateContentHeightY(contentHeight, viewHeight);
        updateScrollbarGeometry();
    }

    private void updateContentBounds() {
        int contentLeft = left - state.getScrollOffsetX();
        int contentTop = top - state.getScrollOffsetY();
        content.setBounds(contentLeft, contentTop, contentLeft + contentWidth, contentTop + contentHeight);
    }

    private void drawScrollbar() {
        updateScrollbarGeometry();
        if (scrollMode == EnumScrollMode.HORIZONTAL || state.getMaxScrollY() <= 0) {
            return;
        }
        GuiUtils.drawRect(scrollbarX, scrollbarTop, scrollbarX + 4, scrollbarBottom, GuiColors.SCROLLBAR_TRACK);
        GuiUtils.drawRect(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, GuiColors.SCROLLBAR_THUMB);
    }

    private void updateScrollbarGeometry() {
        scrollbarX = right - 4;
        scrollbarTop = top;
        scrollbarBottom = bottom;
        int trackHeight = Math.max(0, scrollbarBottom - scrollbarTop);
        if (trackHeight == 0 || contentHeight <= 0) {
            thumbHeight = trackHeight;
            thumbY = scrollbarTop;
            return;
        }
        thumbHeight = Math.max(12, (int) ((float) trackHeight * (float) trackHeight / (float) contentHeight));
        if (thumbHeight > trackHeight) {
            thumbHeight = trackHeight;
        }
        int thumbRange = trackHeight - thumbHeight;
        thumbY = scrollbarTop + (thumbRange > 0 && state.getMaxScrollY() > 0
            ? (int) ((float) thumbRange * (float) state.getScrollOffsetY() / (float) state.getMaxScrollY())
            : 0);
    }

    private boolean isMouseOverScrollbar(int mouseX, int mouseY) {
        return scrollMode != EnumScrollMode.HORIZONTAL && state.getMaxScrollY() > 0
            && mouseX >= scrollbarX && mouseX <= scrollbarX + 4
            && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
    }
}
