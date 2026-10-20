package betamoon.gui.widget;

import betamoon.gui.framework.GuiContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A button that opens a shared dropdown menu and stores a typed selected value. */
public final class GuiDropdownButton<T> extends GuiButton {
    public static final class Option<T> {
        private final T value;
        private final String label;

        public Option(T value, String label) {
            if (label == null || label.isEmpty()) {
                throw new IllegalArgumentException("Dropdown option labels must not be empty");
            }
            this.value = value;
            this.label = label;
        }

        public T value() {
            return value;
        }

        public String label() {
            return label;
        }
    }

    private final GuiDropdownMenu menu;
    private final List<Option<T>> options;
    private final Consumer<T> onSelect;
    private T selected;
    private BooleanSupplier beforeOpen = () -> true;

    public GuiDropdownButton(GuiDropdownMenu menu, List<Option<T>> options, T selected, Consumer<T> onSelect) {
        super("", null);
        if (menu == null || options == null || options.isEmpty() || onSelect == null) {
            throw new IllegalArgumentException("A dropdown needs a menu, options and a selection action");
        }
        this.menu = menu;
        this.options = Collections.unmodifiableList(new ArrayList<Option<T>>(options));
        this.selected = selected;
        this.onSelect = onSelect;
        setAction(this::open);
        updateLabel(null);
    }

    public void setBeforeOpen(BooleanSupplier beforeOpen) {
        this.beforeOpen = beforeOpen == null ? () -> true : beforeOpen;
    }

    public void setSelected(T selected) {
        this.selected = selected;
        updateLabel(null);
    }

    public T getSelected() {
        return selected;
    }

    List<Option<T>> options() {
        return options;
    }

    int selectedIndex() {
        for (int index = 0; index < options.size(); index++) {
            if (Objects.equals(options.get(index).value(), selected)) {
                return index;
            }
        }
        return -1;
    }

    void select(int index) {
        if (index < 0 || index >= options.size()) {
            return;
        }
        selected = options.get(index).value();
        updateLabel(null);
        onSelect.accept(selected);
    }

    private void open() {
        if (menu.isOpenFor(this)) {
            menu.close();
        } else if (beforeOpen.getAsBoolean()) {
            menu.open(this);
        }
    }

    @Override
    protected void arrangeElement(GuiContext context) {
        updateLabel(context);
    }

    @Override
    protected void renderElement(GuiContext context) {
        super.renderElement(context);
        int middle = getTop() + getHeight() / 2;
        int right = getRight() - 8;
        int color = isEnabled() ? 0xFFE0E0E0 : 0xFF999999;
        context.getRenderer().drawHorizontalLine(right - 5, right, middle - 2, color);
        context.getRenderer().drawHorizontalLine(right - 4, right - 1, middle - 1, color);
        context.getRenderer().drawHorizontalLine(right - 3, right - 2, middle, color);
    }

    private void updateLabel(GuiContext context) {
        int index = selectedIndex();
        String label = index >= 0 ? options.get(index).label() : "Select";
        if (context != null) {
            label = context.getRenderer().trimToWidth(label, Math.max(0, getWidth() - 24));
        }
        if (!label.equals(getLabel())) {
            setLabel(label);
        }
    }
}
