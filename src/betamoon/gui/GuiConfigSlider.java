package betamoon.gui;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.control.GuiNumericRange;
import java.math.BigDecimal;
import java.util.function.DoubleConsumer;
import org.lwjgl.input.Keyboard;

/** Stepped numeric slider for the script configuration screen. */
final class GuiConfigSlider extends GuiElement {
    private static final int THUMB_WIDTH = 8;

    private final double minimum;
    private final double maximum;
    private final GuiNumericRange range;
    private final int steps;
    private final DoubleConsumer onChange;
    private double value;
    private boolean dragging;

    GuiConfigSlider(double minimum, double maximum, double step, double value, DoubleConsumer onChange) {
        this.minimum = minimum;
        this.maximum = maximum;
        this.range = new GuiNumericRange(minimum, maximum, step);
        this.steps = (int) Math.round((maximum - minimum) / step);
        this.value = value;
        this.onChange = onChange;
        setFocusable(true);
    }

    double value() {
        return value;
    }

    @Override
    protected void renderElement(GuiContext context) {
        boolean hovered = isEnabled() && contains(context.getMouseX(), context.getMouseY())
                && context.getRenderer().isPointVisible(context.getMouseX(), context.getMouseY());
        int thumbLeft = trackLeft() + (int) Math.round(fraction() * (trackRight() - trackLeft())) - THUMB_WIDTH / 2;

        context.getRenderer().drawRect(getBounds(), 0xFF000000);
        context.getRenderer().drawRect(getLeft() + 1, getTop() + 1, getRight() - 1, getBottom() - 1,
                isEnabled() ? hovered ? 0xFF303030 : 0xFF202020 : 0xFF171717);
        context.getRenderer().drawRect(thumbLeft, getTop() + 1, thumbLeft + THUMB_WIDTH, getBottom() - 1, 0xFF000000);
        context.getRenderer().drawRect(thumbLeft + 1, getTop() + 2, thumbLeft + THUMB_WIDTH - 1, getBottom() - 2,
                isEnabled() ? hovered ? 0xFF999999 : 0xFF777777 : 0xFF555555);
        context.getRenderer().drawRect(thumbLeft + 1, getTop() + 2, thumbLeft + THUMB_WIDTH - 2, getTop() + 3,
                isEnabled() ? 0xFFB0B0B0 : 0xFF777777);
        context.getRenderer().drawRect(thumbLeft + 1, getTop() + 2, thumbLeft + 2, getBottom() - 2,
                isEnabled() ? 0xFF999999 : 0xFF707070);
        context.getRenderer().drawRect(thumbLeft + THUMB_WIDTH - 2, getTop() + 2, thumbLeft + THUMB_WIDTH - 1,
                getBottom() - 2, isEnabled() ? 0xFF555555 : 0xFF393939);

        String label = context.getRenderer().trimToWidth(format(value), Math.max(0, getWidth() - 8));
        int labelLeft = getLeft() + (getWidth() - context.getRenderer().textWidth(label)) / 2;
        int labelTop = getTop() + (getHeight() - context.getRenderer().lineHeight() + 1) / 2;
        context.getRenderer().drawText(label, labelLeft, labelTop,
                isEnabled()
                        ? context.getRenderer().getTheme().textPrimary
                        : context.getRenderer().getTheme().textMuted);
        if (isFocused()) {
            context.getRenderer().drawRect(getLeft(), getTop(), getRight(), getTop() + 1, 0xFFFFFFA0);
        }
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        if (event.getType() == GuiInputEvent.Type.POINTER_DOWN && event.getButton() == 0) {
            requestFocus(context);
            capturePointer(context);
            dragging = true;
            setFromMouse(event.getMouseX());
            return true;
        }
        if (event.getType() == GuiInputEvent.Type.POINTER_DRAG && dragging && event.isMouseDown()) {
            setFromMouse(event.getMouseX());
            return true;
        }
        if (event.getType() == GuiInputEvent.Type.POINTER_UP || event.getType() == GuiInputEvent.Type.POINTER_CANCEL) {
            boolean wasDragging = dragging;
            dragging = false;
            releasePointer(context);
            return wasDragging;
        }
        if (event.getType() != GuiInputEvent.Type.KEY_TYPED || !isFocused()) {
            return false;
        }
        int key = event.getKeyCode();
        int index = nearestStep();
        if (key == Keyboard.KEY_LEFT) {
            setStep(index - 1);
        } else if (key == Keyboard.KEY_RIGHT) {
            setStep(index + 1);
        } else if (key == Keyboard.KEY_HOME) {
            setStep(0);
        } else if (key == Keyboard.KEY_END) {
            setStep(steps);
        } else {
            return false;
        }
        return true;
    }

    private int trackLeft() {
        return getLeft() + 1 + THUMB_WIDTH / 2;
    }

    private int trackRight() {
        return Math.max(trackLeft(), getRight() - 1 - THUMB_WIDTH / 2);
    }

    private double fraction() {
        return Math.max(0.0D, Math.min(1.0D, (value - minimum) / (maximum - minimum)));
    }

    private int nearestStep() {
        return (int) Math.max(0, Math.min(steps, Math.round(fraction() * steps)));
    }

    private void setFromMouse(int mouseX) {
        int width = Math.max(1, trackRight() - trackLeft());
        double fraction = Math.max(0.0D, Math.min(1.0D, (mouseX - trackLeft()) / (double) width));
        setStep((int) Math.round(fraction * steps));
    }

    private void setStep(int index) {
        int selected = Math.max(0, Math.min(steps, index));
        double next = range.valueAt(selected);
        if (Double.compare(value, next) == 0) {
            return;
        }
        value = next;
        onChange.accept(value);
    }

    private static String format(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
