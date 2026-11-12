package betamoon.gui.widget;

import betamoon.gui.control.GuiSelectionOptions;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiFrameContent;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiLayer;
import betamoon.gui.framework.GuiMeasurementPreview;
import betamoon.gui.graphics.GraphicsTransform;
import betamoon.gui.presentation.GuiPresentationNode;
import betamoon.gui.presentation.GuiViewPublication;
import betamoon.gui.style.GuiControlStyle;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiVisualTemplate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import org.lwjgl.input.Keyboard;

/**
 * Keyed select/context menu using the shared radio selection and scene-owned
 * popover service.
 */
public final class GuiSelectView extends GuiRadioView {
    private GuiLayer popup;
    private boolean pressed;
    private Object pressFrame;
    private GraphicsTransform pressTransform;
    private String placeholder = "";
    private Object sourceOptions;
    private Object sourceSelection;
    private Object sourceStyle;

    public GuiSelectView(String kind, GuiStyleSheet theme, GuiVisualTemplate template, Events events) {
        super(kind, theme, template, events);
    }

    @Override
    public GuiContainer childContainer() {
        return null;
    }

    @Override
    public void publish(GuiPresentationNode node) {
        if (popup != null && (!Objects.equals(sourceOptions, node.values().get("options"))
                || !Objects.equals(sourceSelection, node.values().get("selectedKey")) || !Objects.equals(sourceStyle,
                        Arrays.asList(node.values().get("variant"), node.values().get("styleRef"))))) {
            closePopup();
        }
        sourceOptions = node.values().get("options");
        sourceSelection = node.values().get("selectedKey");
        sourceStyle = Arrays.asList(node.values().get("variant"), node.values().get("styleRef"));
        super.publish(node);
        placeholder = (String) node.values().get("text");
    }

    @Override
    public GuiSelectView configure(Map<String, Object> values) {
        closePopup();
        super.configure(values);
        placeholder = (String) values.getOrDefault("text", "");
        return this;
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        GuiViewPublication pending = GuiMeasurementPreview.values(this);
        Map<String, Object> values = pending == null ? templateValues(false) : pending.values;
        double width = ((Number) values.get("width")).doubleValue();
        double height = ((Number) values.get("height")).doubleValue();
        return constraints.constrain(new Size(
                Math.max((int) Math.round(((Number) values.get("minWidth")).doubleValue()),
                        width > 0 ? (int) Math.round(width) : 120),
                Math.max((int) Math.round(((Number) values.get("minHeight")).doubleValue()),
                        height > 0 ? (int) Math.round(height) : 20)));
    }

    @Override
    protected Rect focusBounds() {
        return getBounds();
    }

    @Override
    public String text() {
        for (GuiSelectionOptions.Option option : options().options) {
            if (option.key.equals(selectedKey())) {
                return option.label;
            }
        }
        return placeholder;
    }

    @Override
    protected GuiFrameContent sampleFrameContent() {
        Object stamp = Arrays.asList(getBounds(), options(), selectedKey());
        return new TriggerFrame(stamp);
    }

    private static final class TriggerFrame implements GuiFrameContent {
        final Object stamp;
        TriggerFrame(Object stamp) {
            this.stamp = stamp;
        }

        public boolean contains(double x, double y) {
            return true;
        }
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        if (event.getType() == GuiInputEvent.Type.POINTER_CANCEL) {
            pressed = false;
            releasePointer(context);
            return true;
        }
        boolean contextMenu = kind().equals("contextmenu");
        int button = contextMenu ? 1 : 0;
        if (event.getType() == GuiInputEvent.Type.POINTER_DOWN && event.getButton() == button) {
            TriggerFrame frame = (TriggerFrame) context.getFrameContent();
            if (frame == null || !frame.stamp.equals(Arrays.asList(getBounds(), options(), selectedKey()))) {
                return true;
            }
            pressed = true;
            pressFrame = frame.stamp;
            pressTransform = context.getElementTransform();
            requestFocus(context);
            capturePointer(context);
            return true;
        }
        if (event.getType() == GuiInputEvent.Type.POINTER_UP && event.getButton() == button && pressed) {
            pressed = false;
            releasePointer(context);
            GraphicsTransform transform = context.getElementTransform();
            if (pressFrame.equals(Arrays.asList(getBounds(), options(), selectedKey()))
                    && transform.x == pressTransform.x && transform.y == pressTransform.y
                    && transform.scaleX == pressTransform.scaleX && transform.scaleY == pressTransform.scaleY
                    && contains(event.getMouseX(), event.getMouseY())) {
                open(context, contextMenu, (int) transform.mapX(event.getMouseX()),
                        (int) transform.mapY(event.getMouseY()));
            }
            return true;
        }
        if (event.getType() == GuiInputEvent.Type.KEY_TYPED && isFocused()
                && (event.getKeyCode() == Keyboard.KEY_RETURN || event.getKeyCode() == Keyboard.KEY_NUMPADENTER
                        || event.getKeyCode() == Keyboard.KEY_SPACE || event.getKeyCode() == Keyboard.KEY_DOWN
                        || event.getKeyCode() == Keyboard.KEY_UP || event.getKeyCode() == Keyboard.KEY_APPS
                        || event.isShiftDown() && event.getKeyCode() == Keyboard.KEY_F10)) {
            open(context, false, 0, 0);
            return true;
        }
        return pressed && event.getType() == GuiInputEvent.Type.POINTER_DRAG;
    }

    private void open(GuiContext context, boolean atPointer, int x, int y) {
        if (popup != null && popup.isActive() || options().navigate("", 1, true).isEmpty()) {
            return;
        }
        MenuOptions menu = new MenuOptions(GuiStyleSheet.MINIMAL.withInline(style(0)));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("options", templateValues(true).get("options"));
        values.put("selectedKey", selectedKey());
        values.put("optionHeight", 20);
        menu.configure(values);
        GuiScrollView scroll = new GuiScrollView(menu, GuiScrollView.Policy.VERTICAL);
        GuiContainer body = new GuiContainer() {
            @Override
            protected Size measureElement(GuiContext context, Constraints constraints) {
                int width = Math.max(120, GuiSelectView.this.getWidth());
                for (GuiSelectionOptions.Option option : menu.options().options) {
                    width = Math.max(width, menu.style(0).font().width(context, option.label) + 16);
                }
                return constraints.constrain(new Size(width, Math.min(120, menu.options().options.size() * 20)));
            }

            @Override
            protected void arrangeChildren(GuiContext context) {
                scroll.setContentSize(getWidth(), menu.options().options.size() * 20);
                scroll.arrange(context, getBounds());
            }
        };
        body.add(scroll);
        GuiLayer opened = context.openPopover(this, body, atPointer, x, y);
        popup = opened;
        opened.onClosed(() -> {
            menu.close();
            body.clear();
            if (popup == opened) {
                popup = null;
            }
        });
    }

    private final class MenuOptions extends GuiRadioView {
        private boolean activating;

        MenuOptions(GuiStyleSheet theme) {
            super(theme, MENU_TEMPLATE, (event, value) -> true);
        }

        @Override
        protected boolean select(String key) {
            if (activating) {
                if (GuiSelectView.this.select(key)) {
                    closePopup();
                }
                return true;
            }
            return super.select(key);
        }

        @Override
        protected boolean onInput(GuiContext context, GuiInputEvent event) {
            if (event.getType() == GuiInputEvent.Type.KEY_TYPED && (event.getKeyCode() == Keyboard.KEY_RETURN
                    || event.getKeyCode() == Keyboard.KEY_NUMPADENTER || event.getKeyCode() == Keyboard.KEY_SPACE)) {
                if (GuiSelectView.this.select(focusKey())) {
                    closePopup();
                }
                return true;
            }
            activating = event.getType() == GuiInputEvent.Type.POINTER_UP;
            try {
                return super.onInput(context, event);
            } finally {
                activating = false;
            }
        }
    }

    private static final GuiVisualTemplate MENU_TEMPLATE = new GuiVisualTemplate() {
        public Set<String> parts() {
            return new LinkedHashSet<>(Arrays.asList("options", "focus"));
        }

        public void paint(GuiContext context, GuiStyledView view, GuiControlStyle style) {
            GuiRadioView menu = (GuiRadioView) view;
            context.getRenderer().drawRect(menu.getBounds(), style.number("background", 0xFF222222));
            int top = (int) context.getElementTransform().localY(context.getElementClip().top);
            int bottom = (int) context.getElementTransform().localY(context.getElementClip().bottom);
            int first = Math.max(0, (top - menu.getTop()) / 20);
            int last = Math.min(menu.options().options.size(), (bottom - menu.getTop() + 19) / 20);
            String hovered = menu.hoveredKey(context);
            for (int i = first; i < last; i++) {
                GuiSelectionOptions.Option option = menu.options().options.get(i);
                Rect bounds = menu.optionBounds(i);
                if (option.key.equals(menu.focusKey())) {
                    context.getRenderer().drawRect(bounds, style.number("selection", 0x8855AAFF));
                }
                if (option.key.equals(hovered)) {
                    context.getRenderer().drawRect(bounds, style.number("accent", 0xFF4477AA));
                }
                style.font().draw(context, option.label, bounds.getLeft() + 4, bounds.getTop() + 5,
                        option.enabled ? style.number("foreground", 0xFFE0E0E0) : 0xFF777777, false);
            }
        }
    };

    private void closePopup() {
        if (popup != null) {
            popup.close();
        }
    }

    @Override
    public void close() {
        closePopup();
        pressed = false;
        pressFrame = null;
        pressTransform = null;
        sourceOptions = null;
        sourceSelection = null;
        sourceStyle = null;
        placeholder = "";
        super.close();
    }
}
