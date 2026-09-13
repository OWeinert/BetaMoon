package betamoon.gui.widget;

import betamoon.gui.framework.GuiAction;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiInputEvent;
import java.util.function.Supplier;
import org.lwjgl.input.Keyboard;

/** Clickable text with optional wrapping and overlay tooltip. */
public class GuiLink extends GuiElement {
    private String text = "";
    private String tooltip;
    private Supplier<String> textSupplier;
    private Supplier<String> tooltipSupplier;
    private GuiAction action;
    private boolean wrap;

    public GuiLink(GuiAction action) {
        this.action = action;
        setFocusable(action != null);
    }

    public GuiLink() {
        this(null);
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
        textSupplier = null;
        requestLayout();
    }

    public void setTextSupplier(Supplier<String> supplier) {
        textSupplier = supplier;
        requestLayout();
    }

    public void setTooltip(String tooltip) {
        this.tooltip = tooltip;
        tooltipSupplier = null;
    }

    public void setTooltipSupplier(Supplier<String> supplier) {
        tooltipSupplier = supplier;
    }

    public void setAction(GuiAction action) {
        this.action = action;
        setFocusable(action != null);
    }

    public void setWrap(boolean wrap) {
        this.wrap = wrap;
        requestLayout();
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        String value = resolveText();
        int width = context.getRenderer().textWidth(value);
        int height = context.getRenderer().lineHeight();
        if (wrap && width > constraints.getMaxWidth()) {
            width = constraints.getMaxWidth();
            height = context.getRenderer().wrappedTextHeight(value, width);
        }
        return constraints.constrain(new Size(width, height));
    }

    @Override
    protected void renderElement(GuiContext context) {
        String value = resolveText();
        if (value.isEmpty()) {
            return;
        }
        boolean hovered = isHovered(context);
        int color = hovered ? context.getRenderer().getTheme().linkHover : context.getRenderer().getTheme().link;
        if (wrap) {
            context.getRenderer().drawWrappedText(value, getLeft(), getTop(), getWidth(), color);
        } else {
            context.getRenderer().drawText(value, getLeft(), getTop(), color);
            if (hovered) {
                int width = Math.min(getWidth(), context.getRenderer().textWidth(value));
                int underlineY = getTop() + context.getRenderer().lineHeight() + 1;
                context.getRenderer().drawHorizontalLine(getLeft(), getLeft() + width, underlineY,
                        context.getRenderer().getTheme().linkUnderline);
            }
        }
        if (hovered) {
            String resolvedTooltip = resolveTooltip();
            if (resolvedTooltip != null && !resolvedTooltip.isEmpty()) {
                context.showTooltip(resolvedTooltip);
            }
        }
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        boolean pointerActivation = event.getType() == GuiInputEvent.Type.POINTER_DOWN && event.getButton() == 0
                && contains(event.getMouseX(), event.getMouseY());
        boolean keyboardActivation = event.getType() == GuiInputEvent.Type.KEY_TYPED && isFocused()
                && (event.getKeyCode() == Keyboard.KEY_RETURN || event.getKeyCode() == Keyboard.KEY_NUMPADENTER
                        || event.getKeyCode() == Keyboard.KEY_SPACE);
        if (!pointerActivation && !keyboardActivation) {
            return false;
        }
        requestFocus(context);
        if (action != null) {
            action.run();
            return true;
        }
        return false;
    }

    private String resolveText() {
        String value = textSupplier == null ? text : textSupplier.get();
        return value == null ? "" : value;
    }

    private String resolveTooltip() {
        return tooltipSupplier == null ? tooltip : tooltipSupplier.get();
    }
}
