package betamoon.gui.widget;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.control.GuiActivation;
import java.util.function.Consumer;
import org.lwjgl.opengl.GL11;

/**
 * Retained, labeled checkbox using the existing native checkbox appearance and
 * framework focus routing.
 */
public final class GuiCheckboxControl extends GuiElement {
    private final String label;
    private final Consumer<Boolean> changed;
    private boolean selected;
    private String tooltip;
    private final GuiActivation activation = new GuiActivation(GuiActivation.Timing.LEGACY_PRESS);

    public GuiCheckboxControl(String label, boolean selected, Consumer<Boolean> changed) {
        this.label = label;
        this.selected = selected;
        this.changed = changed;
        setFocusable(true);
    }

    public boolean isSelected() {
        return selected;
    }

    public void setTooltip(String tooltip) {
        this.tooltip = tooltip;
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        int textWidth = Math.max(8, constraints.getMaxWidth() - 26);
        int height = Math.max(20, context.getRenderer().wrappedTextHeight(label, textWidth));
        return constraints.constrain(new Size(constraints.getMaxWidth(), height));
    }

    @Override
    protected void renderElement(GuiContext context) {
        if (context.getMinecraft() != null) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT
                    | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
            try {
                new GuiCheckbox(0, getLeft(), getTop() + (getHeight() - 20) / 2, selected, isEnabled())
                        .drawButton(context.getMinecraft(), context.getMouseX(), context.getMouseY());
            } finally {
                GL11.glPopAttrib();
            }
        }
        context.getRenderer().drawWrappedText(label, getLeft() + 26,
                getTop() + (getHeight() - context.getRenderer().wrappedTextHeight(label, Math.max(8, getWidth() - 26)))
                        / 2,
                Math.max(8, getWidth() - 26), isFocused() ? 0xffffffaa : context.getRenderer().getTheme().textPrimary);
        if (isHovered(context) && tooltip != null) {
            context.showTooltip(tooltip);
        }
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        int result = activation.input(event, contains(event.getMouseX(), event.getMouseY()), isFocused());
        if ((result & GuiActivation.ACTIVATE) != 0) {
            requestFocus(context);
            selected = !selected;
            changed.accept(selected);
        }
        return (result & GuiActivation.CONSUMED) != 0;
    }
}
