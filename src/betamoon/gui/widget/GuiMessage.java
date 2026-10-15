package betamoon.gui.widget;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import java.util.function.Supplier;

/**
 * Wrapped, vertically scrollable dialog text; actions remain outside its
 * viewport.
 */
public final class GuiMessage extends GuiContainer {
    private final Supplier<String> text;
    private final GuiScrollView scrollView;
    private String measuredText;
    private int measuredWidth = -1;

    public GuiMessage(Supplier<String> text) {
        if (text == null) {
            throw new IllegalArgumentException("Message supplier cannot be null");
        }
        this.text = text;
        scrollView = add(new GuiScrollView(new GuiElement() {
            @Override
            protected void renderElement(GuiContext context) {
                context.getRenderer().drawWrappedText(value(), getLeft(), getTop(), Math.max(1, getWidth() - 8),
                        context.getRenderer().getTheme().textPrimary);
            }
        }, GuiScrollView.Policy.VERTICAL));
    }

    private String value() {
        String value = text.get();
        return value == null ? "" : value;
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        measuredText = value();
        measuredWidth = getWidth();
        int height = context.getRenderer().wrappedTextHeight(measuredText, Math.max(1, getWidth() - 8));
        scrollView.setContentSize(getWidth(), height);
        scrollView.arrange(context, getBounds());
    }

    @Override
    protected void renderBeforeChildren(GuiContext context) {
        if (measuredWidth != getWidth() || !value().equals(measuredText)) {
            arrangeChildren(context);
        }
    }
}
