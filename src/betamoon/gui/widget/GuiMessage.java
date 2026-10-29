package betamoon.gui.widget;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Size;
import java.util.function.Supplier;

/**
 * Wrapped, vertically scrollable dialog text; actions remain outside its
 * viewport.
 */
public final class GuiMessage extends GuiContainer {
    private final Supplier<String> text;
    private final GuiScrollView scrollView;
    private String publishedText = "";

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
        return publishedText;
    }

    @Override
    protected void updateElement(GuiContext context) {
        String candidate = text.get();
        candidate = candidate == null ? "" : candidate;
        if (!candidate.equals(publishedText)) {
            publishedText = candidate;
            requestLayout();
        }
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        int width = constraints.getMaxWidth();
        int height = context.getRenderer().wrappedTextHeight(value(), Math.max(1, width - 8));
        return new Size(width, height);
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        int height = context.getRenderer().wrappedTextHeight(publishedText, Math.max(1, getWidth() - 8));
        scrollView.setContentSize(getWidth(), height);
        scrollView.arrange(context, getBounds());
    }

}
