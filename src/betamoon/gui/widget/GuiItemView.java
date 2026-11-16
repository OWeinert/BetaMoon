package betamoon.gui.widget;

import betamoon.gui.control.GuiItemModel;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiFrameContent;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiMeasurementPreview;
import betamoon.gui.interop.GuiPreparedItem;
import betamoon.gui.presentation.GuiPresentationNode;
import betamoon.gui.presentation.GuiViewPublication;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiVisualTemplate;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import org.lwjgl.opengl.GL11;

/**
 * Passive item illustration. Rendering cannot transfer, select or modify a
 * stack.
 */
public final class GuiItemView extends GuiStyledView {
    private GuiPreparedItem.Service service;
    private boolean hudAvailable;
    private Map<String, Object> values = Collections.emptyMap();
    private GuiItemModel model;
    private GuiItemModel preparedModel;
    private Object revision;
    private GuiPreparedItem prepared;

    public GuiItemView(GuiStyleSheet theme, GuiVisualTemplate template, Events events) {
        super("item", theme, template, events);
        setFocusable(false);
    }

    public void items(GuiPreparedItem.Service service, boolean hudAvailable) {
        this.service = service;
        this.hudAvailable = hudAvailable;
    }

    @Override
    public void validateValues(Map<String, Object> values) {
        super.validateValues(values);
        new GuiItemModel(values, hudAvailable);
        if (service == null) {
            throw new IllegalArgumentException("Item view requires a native item host service");
        }
    }

    @Override
    public GuiItemView configure(Map<String, Object> values) {
        super.configure(values);
        this.values = GuiBuiltInViews.properties("item").withDefaults(values).baseline();
        return this;
    }

    @Override
    public void publish(GuiPresentationNode node) {
        super.publish(node);
        values = node.values();
        model = new GuiItemModel(values, hudAvailable);
    }

    @Override
    protected void updateElement(GuiContext context) {
        super.updateElement(context);
        model = new GuiItemModel(values, hudAvailable);
        Object next = model.hotbarSlot == 0 ? null : service.revision(model.hotbarSlot);
        if (prepared == null || !model.sameAppearance(preparedModel) || !Objects.equals(revision, next)) {
            prepared = service.prepare(model);
            preparedModel = model;
            revision = next;
        }
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        Size base = super.measureElement(context, constraints);
        GuiViewPublication pending = GuiMeasurementPreview.values(this);
        Map<String, Object> values = pending == null ? this.values : pending.values;
        return constraints
                .constrain(new Size(((Number) values.getOrDefault("width", 0)).doubleValue() > 0 ? base.getWidth() : 16,
                        ((Number) values.getOrDefault("height", 0)).doubleValue() > 0 ? base.getHeight() : 16));
    }

    @Override
    protected GuiFrameContent sampleFrameContent() {
        double pickup = model != null && model.pickupAnimation && model.hotbarSlot != 0
                ? service.pickup(model.hotbarSlot)
                : 0;
        return new Frame(prepared, pickup);
    }

    public void paintItem(GuiContext context) {
        Frame frame = (Frame) context.getFrameContent();
        if (frame.item == null) {
            throw new IllegalStateException("Item must prepare before painting");
        }
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(getLeft(), getTop(), 0);
            GL11.glScaled(getWidth() / 16.0, getHeight() / 16.0, 1);
            frame.item.draw(context, frame.pickup);
        } finally {
            GL11.glPopMatrix();
        }
    }

    @Override
    public void close() {
        prepared = null;
        preparedModel = null;
        service = null;
        super.close();
    }

    private static final class Frame implements GuiFrameContent {
        final GuiPreparedItem item;
        final double pickup;
        Frame(GuiPreparedItem item, double pickup) {
            this.item = item;
            this.pickup = pickup;
        }

        public boolean contains(double localX, double localY) {
            return true;
        }
    }
}
