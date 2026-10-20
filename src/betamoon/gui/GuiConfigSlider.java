package betamoon.gui;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiInputEvent;
import java.math.BigDecimal;
import java.util.function.DoubleConsumer;
import org.lwjgl.input.Keyboard;

/** Stepped numeric slider for the script configuration screen. */
final class GuiConfigSlider extends GuiElement {
    private static final int VALUE_WIDTH = 40;
    private static final int TRACK_INSET = 5;

    private final double minimum;
    private final double maximum;
    private final BigDecimal decimalMinimum;
    private final BigDecimal decimalStep;
    private final int steps;
    private final DoubleConsumer onChange;
    private double value;
    private boolean dragging;

    GuiConfigSlider(double minimum, double maximum, double step, double value, DoubleConsumer onChange) {
        this.minimum = minimum;
        this.maximum = maximum;
        this.decimalMinimum = BigDecimal.valueOf(minimum);
        this.decimalStep = BigDecimal.valueOf(step);
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
        int trackLeft = trackLeft();
        int trackRight = trackRight();
        int trackY = getTop() + getHeight() / 2;
        int thumb = trackLeft + (int) Math.round(fraction() * (trackRight - trackLeft));
        context.getRenderer().drawRect(getBounds(), 0xFF333333);
        context.getRenderer().drawRect(getLeft() + 1, getTop() + 1, getRight() - 1, getBottom() - 1,
                isEnabled() ? 0xFF777777 : 0xFF555555);
        context.getRenderer().drawRect(trackLeft, trackY - 2, trackRight + 1, trackY + 2, 0xFF222222);
        context.getRenderer().drawRect(trackLeft, trackY - 1, thumb + 1, trackY + 1,
                isEnabled() ? 0xFFDDDDDD : 0xFF999999);
        context.getRenderer().drawRect(thumb - 2, getTop() + 3, thumb + 3, getBottom() - 3,
                isEnabled() ? 0xFFE0E0E0 : 0xFF999999);

        String label = context.getRenderer().trimToWidth(format(value), VALUE_WIDTH - 4);
        int labelLeft = getRight() - VALUE_WIDTH + (VALUE_WIDTH - context.getRenderer().textWidth(label)) / 2;
        int labelTop = getTop() + (getHeight() - context.getRenderer().lineHeight() + 1) / 2;
        context.getRenderer().drawText(label, labelLeft, labelTop,
                isEnabled() ? context.getRenderer().getTheme().textPrimary
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
        if (event.getType() == GuiInputEvent.Type.POINTER_UP) {
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
        return getLeft() + TRACK_INSET;
    }

    private int trackRight() {
        return Math.max(trackLeft(), getRight() - VALUE_WIDTH - TRACK_INSET);
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
        double next = decimalMinimum.add(decimalStep.multiply(BigDecimal.valueOf(selected))).doubleValue();
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
