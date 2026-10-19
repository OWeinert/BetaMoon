package betamoon.gui.widget;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiTheme;
import java.util.ArrayList;
import java.util.List;

/**
 * Centered modal panel with a title, body, and consistently arranged actions.
 */
public final class GuiDialog extends GuiContainer {
    public enum FooterAlignment {
        FILL, CENTER
    }

    private final String title;
    private final GuiContainer bodySlot = add(new GuiContainer());
    private final List<GuiButton> footerButtons = new ArrayList<GuiButton>();
    private GuiElement body;
    private GuiElement customFooter;
    private int maximumWidth = 360;
    private int maximumHeight = 200;
    private int minimumHeight;
    private int horizontalScreenMargin = 32;
    private int verticalScreenMargin = 48;
    private int titleOffset = 8;
    private int titleLineOffset = 24;
    private int titleLineInset = 10;
    private int titleColor;
    private float titleScale = 1.0F;
    private int bodyHorizontalInset = 10;
    private int bodyTopOffset = 32;
    private int bodyBottomGap = 8;
    private int footerBottomInset = 10;
    private int footerHorizontalInset = 10;
    private int footerGap = 10;
    private int footerButtonHeight = 20;
    private int centeredButtonWidth = 90;
    private FooterAlignment footerAlignment = FooterAlignment.FILL;
    private Rect panelBounds = Rect.EMPTY;

    public GuiDialog(String title) {
        this.title = title == null ? "" : title;
        this.titleColor = GuiTheme.DEFAULT.textPrimary;
        bodySlot.setVisible(false);
    }

    public void setBody(GuiElement body) {
        bodySlot.clear();
        this.body = body;
        if (body != null) {
            bodySlot.add(body);
        }
        bodySlot.setVisible(body != null);
    }

    public GuiButton addFooterButton(GuiButton button) {
        if (customFooter != null) {
            throw new IllegalStateException("A dialog cannot combine a custom footer and automatic footer buttons");
        }
        if (button == null) {
            throw new IllegalArgumentException("Dialog footer button cannot be null");
        }
        footerButtons.add(button);
        add(button);
        return button;
    }

    public void setFooter(GuiElement footer) {
        if (!footerButtons.isEmpty()) {
            throw new IllegalStateException("A dialog cannot combine a custom footer and automatic footer buttons");
        }
        if (customFooter != null) {
            remove(customFooter);
        }
        customFooter = footer;
        if (footer != null) {
            add(footer);
        }
        requestLayout();
    }

    public Rect getPanelBounds() {
        return panelBounds;
    }

    public void setPanelSize(int maximumWidth, int maximumHeight, int minimumHeight) {
        this.maximumWidth = Math.max(0, maximumWidth);
        this.maximumHeight = Math.max(0, maximumHeight);
        this.minimumHeight = Math.max(0, minimumHeight);
        requestLayout();
    }

    public void setScreenMargins(int horizontal, int vertical) {
        horizontalScreenMargin = Math.max(0, horizontal);
        verticalScreenMargin = Math.max(0, vertical);
        requestLayout();
    }

    public void setBodyInsets(int horizontal, int topOffset, int bottomGap) {
        bodyHorizontalInset = Math.max(0, horizontal);
        bodyTopOffset = Math.max(0, topOffset);
        bodyBottomGap = Math.max(0, bottomGap);
        requestLayout();
    }

    public void setFooterLayout(FooterAlignment alignment, int horizontalInset, int gap, int buttonWidth) {
        footerAlignment = alignment == null ? FooterAlignment.FILL : alignment;
        footerHorizontalInset = Math.max(0, horizontalInset);
        footerGap = Math.max(0, gap);
        centeredButtonWidth = Math.max(0, buttonWidth);
        requestLayout();
    }

    public void setTitleColor(int titleColor) {
        this.titleColor = titleColor;
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        int availableWidth = Math.max(0, getWidth() - horizontalScreenMargin);
        int availableHeight = Math.max(0, getHeight() - verticalScreenMargin);
        int panelWidth = Math.min(maximumWidth, availableWidth);
        int footerRows = footerRowCount(context, panelWidth);
        int footerHeight = footerRows * footerButtonHeight + Math.max(0, footerRows - 1) * footerGap;
        if (customFooter != null) {
            footerHeight = customFooter.measure(context,
                    Constraints.upTo(Math.max(0, panelWidth - footerHorizontalInset * 2), availableHeight)).getHeight();
        }
        int bodyWidth = Math.max(0, panelWidth - bodyHorizontalInset * 2);
        int bodyHeight = body == null
                ? 0
                : body.measure(context, Constraints.upTo(bodyWidth, Integer.MAX_VALUE)).getHeight();
        boolean hasFooter = customFooter != null || !footerButtons.isEmpty();
        int bottomHeight = hasFooter ? bodyBottomGap + footerHeight + footerBottomInset : bodyBottomGap;
        int preferredHeight = Math.max(minimumHeight, bodyTopOffset + bodyHeight + bottomHeight);
        int panelHeight = Math.min(Math.min(maximumHeight, preferredHeight), availableHeight);
        int panelLeft = getLeft() + (getWidth() - panelWidth) / 2;
        int panelTop = getTop() + (getHeight() - panelHeight) / 2;
        panelBounds = Rect.fromPositionAndSize(panelLeft, panelTop, panelWidth, panelHeight);

        int footerTop = panelBounds.getBottom() - footerBottomInset - footerHeight;
        if (customFooter == null) {
            arrangeFooter(context, footerTop);
        } else {
            customFooter.arrange(context, new Rect(panelBounds.getLeft() + footerHorizontalInset, footerTop,
                    panelBounds.getRight() - footerHorizontalInset, footerTop + footerHeight));
        }
        if (body != null) {
            int bodyBottom = hasFooter ? footerTop - bodyBottomGap : panelBounds.getBottom() - bodyBottomGap;
            bodySlot.arrange(context, new Rect(panelBounds.getLeft() + bodyHorizontalInset,
                    panelBounds.getTop() + bodyTopOffset, panelBounds.getRight() - bodyHorizontalInset, bodyBottom));
        }
    }

    private int footerRowCount(GuiContext context, int panelWidth) {
        if (footerButtons.isEmpty()) {
            return 0;
        }
        return preferredFooterWidth(context) <= Math.max(0, panelWidth - footerHorizontalInset * 2)
                ? 1
                : footerButtons.size();
    }

    private int preferredFooterWidth(GuiContext context) {
        int width = Math.max(0, footerButtons.size() - 1) * footerGap;
        for (GuiButton button : footerButtons) {
            width += Math.max(centeredButtonWidth, context.getRenderer().textWidth(button.getLabel()) + 20);
        }
        return width;
    }

    private void arrangeFooter(GuiContext context, int footerTop) {
        int count = footerButtons.size();
        if (count == 0) {
            return;
        }
        int available = Math.max(0, panelBounds.getWidth() - footerHorizontalInset * 2);
        boolean stacked = footerRowCount(context, panelBounds.getWidth()) > 1;
        int totalWidth = footerAlignment == FooterAlignment.CENTER && !stacked
                ? Math.min(available, preferredFooterWidth(context))
                : available;
        int x = panelBounds.getLeft() + (panelBounds.getWidth() - totalWidth) / 2;
        int remaining = totalWidth - (stacked ? 0 : (count - 1) * footerGap);
        int preferred = Math.max(1, preferredFooterWidth(context) - (count - 1) * footerGap);
        int distributable = remaining;
        for (int i = 0; i < count; i++) {
            GuiButton button = footerButtons.get(i);
            int minimum = Math.max(centeredButtonWidth, context.getRenderer().textWidth(button.getLabel()) + 20);
            int width = stacked
                    ? totalWidth
                    : i == count - 1 ? remaining : Math.min(remaining, distributable * minimum / preferred);
            button.arrange(context, Rect.fromPositionAndSize(x, footerTop, width, footerButtonHeight));
            if (stacked) {
                footerTop += footerButtonHeight + footerGap;
            } else {
                x += width + footerGap;
                remaining -= width;
            }
        }
    }

    @Override
    protected void renderBeforeChildren(GuiContext context) {
        if (panelBounds.isEmpty()) {
            return;
        }
        context.getRenderer().drawPopupPanel(panelBounds);
        if (!title.isEmpty()) {
            context.getRenderer().drawScaledCenteredText(
                    context.getRenderer().trimToWidth(title, panelBounds.getWidth() - 24),
                    panelBounds.getLeft() + panelBounds.getWidth() / 2, panelBounds.getTop() + titleOffset, titleColor,
                    titleScale);
            context.getRenderer().drawHorizontalLine(panelBounds.getLeft() + titleLineInset,
                    panelBounds.getRight() - titleLineInset, panelBounds.getTop() + titleLineOffset,
                    context.getRenderer().getTheme().line);
        }
    }
}
