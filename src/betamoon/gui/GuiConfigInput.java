package betamoon.gui;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.widget.GuiTextBox;
import net.minecraft.src.GuiScreen;

/**
 * Native-looking adapter using the same plain editor model and keys as authored
 * textboxes.
 */
final class GuiConfigInput extends GuiElement {
    private final GuiScreen screen;
    private GuiTextBox field;
    private String value;
    private Rect previousBounds = Rect.EMPTY;

    GuiConfigInput(GuiScreen screen, String value) {
        this.screen = screen;
        this.value = value;
        setFocusable(true);
    }

    String value() {
        return field == null ? value : field.getText();
    }

    void tick() {
        if (field != null) {
            field.updateCursorCounter();
        }
    }

    @Override
    protected void arrangeElement(GuiContext context) {
        if (field != null && previousBounds.equals(getBounds())) {
            return;
        }
        value = value();
        previousBounds = getBounds();
        GuiTextBox previous = field;
        field = new GuiTextBox(screen, context.getRenderer().getFont(), getLeft(), getTop(), getWidth(), getHeight(),
                previous == null ? Math.max(8192, value.length() + 1024) : previous.editorModel().maximumLength());
        field.setText(value);
        if (previous != null) {
            field.editorModel().restoreSnapshot(previous.editorModel().snapshot());
        }
        field.isEnabled = isEnabled();
        field.setFocused(isFocused());
    }

    @Override
    protected void renderElement(GuiContext context) {
        if (field != null) {
            field.isEnabled = isEnabled();
            field.drawTextBox();
        }
    }

    @Override
    protected void onFocusChanged(GuiContext context, boolean focused) {
        if (field != null) {
            field.setFocused(focused);
        }
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        if (field == null) {
            return false;
        }
        if (event.getType() == GuiInputEvent.Type.POINTER_DOWN && event.getButton() == 0) {
            requestFocus(context);
            field.setFocused(true);
            field.mouseClicked(event.getMouseX() - getLeft() + previousBounds.getLeft(),
                    event.getMouseY() - getTop() + previousBounds.getTop(), event.getButton());
            return true;
        }
        if (event.getType() == GuiInputEvent.Type.KEY_TYPED && isFocused()) {
            field.textboxKeyTyped(event.getTypedChar(), event.getKeyCode());
            return true;
        }
        return false;
    }
}
