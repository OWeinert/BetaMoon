package betamoon.gui.widget;

import betamoon.gui.control.GuiIconMeterModel;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiFrameContent;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiMeasurementPreview;
import betamoon.gui.presentation.GuiPresentationNode;
import betamoon.gui.presentation.GuiViewPublication;
import betamoon.gui.style.GuiIconMeterSkin;
import betamoon.gui.style.GuiNativeIconSkins;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiVisualTemplate;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

/**
 * One passive retained view paints a bounded icon program; no per-icon scene
 * children.
 */
public final class GuiIconMeterView extends GuiStyledView {
    private Map<String, Object> values = Collections.emptyMap();
    private Function<String, GuiIconMeterSkin> resolver = name -> {
        throw new IllegalArgumentException("Unknown prepared icon skin: " + name);
    };
    private GuiIconMeterSkin skin;
    private GuiIconMeterModel model;

    public GuiIconMeterView(GuiStyleSheet theme, GuiVisualTemplate template, Events events) {
        super("iconmeter", theme, template, events);
        setFocusable(false);
    }

    public void skinResolver(Function<String, GuiIconMeterSkin> resolver) {
        this.resolver = resolver;
    }

    @Override
    public void validateValues(Map<String, Object> values) {
        super.validateValues(values);
        String name = (String) values.getOrDefault("skin", "");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Icon meter requires a prepared skin name");
        }
        GuiIconMeterSkin candidate = GuiNativeIconSkins.contains(name) ? null : resolver.apply(name);
        model(values, candidate);
    }

    @Override
    public GuiIconMeterView configure(Map<String, Object> values) {
        super.configure(values);
        this.values = GuiBuiltInViews.properties("iconmeter").withDefaults(values).baseline();
        return this;
    }

    @Override
    public void publish(GuiPresentationNode node) {
        super.publish(node);
        values = node.values();
        if (skin != null) {
            model = model(values, skin);
        }
    }

    @Override
    protected void updateElement(GuiContext context) {
        super.updateElement(context);
        String name = (String) values.getOrDefault("skin", "");
        skin = GuiNativeIconSkins.contains(name) ? GuiNativeIconSkins.prepare(context, name) : resolver.apply(name);
        model = model(values, skin);
    }

    private static GuiIconMeterModel model(Map<String, Object> values, GuiIconMeterSkin skin) {
        return new GuiIconMeterModel(values, skin == null ? 9 : skin.width, skin == null ? 9 : skin.height,
                skin == null ? 8 : skin.pitchX, skin == null ? 9 : skin.pitchY);
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        Size base = super.measureElement(context, constraints);
        GuiViewPublication pending = GuiMeasurementPreview.values(this);
        Map<String, Object> candidate = pending == null ? values : pending.values;
        GuiIconMeterModel measure = model(candidate, skin);
        return constraints.constrain(new Size(
                ((Number) candidate.getOrDefault("width", 0)).doubleValue() > 0
                        ? base.getWidth()
                        : measure.measuredWidth(),
                ((Number) candidate.getOrDefault("height", 0)).doubleValue() > 0
                        ? base.getHeight()
                        : measure.measuredHeight()));
    }

    @Override
    protected GuiFrameContent sampleFrameContent() {
        return new Frame(model, skin);
    }

    public void paintIcons(GuiContext context) {
        Frame sampled = (Frame) context.getFrameContent();
        GuiIconMeterModel model = sampled.model;
        GuiIconMeterSkin skin = sampled.skin;
        if (model == null || skin == null) {
            throw new IllegalStateException("Icon meter must prepare before painting");
        }
        for (int index = 0; index < model.capacity; index++) {
            Rect bounds = Rect.fromPositionAndSize(getLeft() + model.column(index) * model.pitchX,
                    getTop() + model.row(index) * model.pitchY, model.width, model.height);
            skin.empty(context, bounds, false);
            if (model.reference != null) {
                skin.fill(context, bounds, model.fraction(index, model.reference), model.steps, model.order, true);
            }
            skin.fill(context, bounds, model.fraction(index, model.value), model.steps, model.order, false);
        }
    }

    private static final class Frame implements GuiFrameContent {
        final GuiIconMeterModel model;
        final GuiIconMeterSkin skin;

        Frame(GuiIconMeterModel model, GuiIconMeterSkin skin) {
            this.model = model;
            this.skin = skin;
        }

        public boolean contains(double localX, double localY) {
            return true;
        }
    }
}
