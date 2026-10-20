package betamoon.gui.widget;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiInputEvent;

/** Popup host for dropdown buttons; add it after scrolling content so menus are not clipped. */
public final class GuiDropdownMenu extends GuiContainer {
    private static final int OPTION_HEIGHT = 20;
    private static final int MAX_VISIBLE_OPTIONS = 6;

    private final GuiContainer options = new GuiContainer() {
        @Override
        protected void arrangeChildren(GuiContext context) {
            int y = getTop() + 2;
            for (GuiElement child : getChildren()) {
                child.arrange(context, Rect.fromPositionAndSize(getLeft() + 2, y,
                        Math.max(0, getWidth() - 4), OPTION_HEIGHT));
                y += OPTION_HEIGHT;
            }
        }
    };
    private final GuiScrollView scroll = add(new GuiScrollView(options, GuiScrollView.Policy.VERTICAL));
    private GuiDropdownButton<?> owner;
    private Rect panel = Rect.EMPTY;

    public GuiDropdownMenu() {
        setVisible(false);
    }

    public boolean isOpenFor(GuiDropdownButton<?> button) {
        return isVisible() && owner == button;
    }

    public void open(GuiDropdownButton<?> button) {
        if (button == null || !button.isEnabled() || button.options().isEmpty()) {
            return;
        }
        owner = button;
        options.clear();
        for (int index = 0; index < button.options().size(); index++) {
            final int selected = index;
            options.add(new GuiButton("", () -> select(selected)));
        }
        scroll.resetScroll();
        setVisible(true);
        requestLayout();
    }

    public void close() {
        owner = null;
        setVisible(false);
    }

    private void select(int index) {
        if (owner == null) {
            return;
        }
        GuiDropdownButton<?> button = owner;
        close();
        button.select(index);
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        if (owner == null) {
            return;
        }
        Rect anchor = owner.getBounds();
        int visibleCount = Math.min(MAX_VISIBLE_OPTIONS, owner.options().size());
        int height = Math.min(visibleCount * OPTION_HEIGHT + 4, Math.max(0, getHeight() - 8));
        int preferredWidth = anchor.getWidth();
        for (GuiDropdownButton.Option<?> option : owner.options()) {
            preferredWidth = Math.max(preferredWidth, context.getRenderer().textWidth(option.label()) + 24);
        }
        int width = Math.min(preferredWidth, Math.max(0, getWidth() - 8));
        int left = Math.max(getLeft() + 4, Math.min(anchor.getLeft(), getRight() - width - 4));
        int below = anchor.getBottom();
        int above = anchor.getTop() - height;
        int top = below + height <= getBottom() - 4 ? below : Math.max(getTop() + 4, above);
        panel = Rect.fromPositionAndSize(left, top, width, height);
        scroll.setContentSize(width, owner.options().size() * OPTION_HEIGHT + 4);
        scroll.arrange(context, panel);
        for (int index = 0; index < options.getChildren().size(); index++) {
            GuiButton button = (GuiButton) options.getChildren().get(index);
            String caption = (owner.selectedIndex() == index ? "> " : "  ")
                    + owner.options().get(index).label();
            String visible = context.getRenderer().trimToWidth(caption, Math.max(0, width - 12));
            if (!visible.equals(button.getLabel())) {
                button.setLabel(visible);
            }
        }
    }

    @Override
    protected void renderBeforeChildren(GuiContext context) {
        context.getRenderer().drawRect(panel, 0xFF222222);
        context.getRenderer().drawRect(panel.getLeft() + 1, panel.getTop() + 1,
                panel.getRight() - 1, panel.getBottom() - 1, 0xFF777777);
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        if (event.getType() == GuiInputEvent.Type.POINTER_DOWN && !panel.contains(event.getMouseX(),
                event.getMouseY())) {
            close();
            return true;
        }
        return false;
    }
}
