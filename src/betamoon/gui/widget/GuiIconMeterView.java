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
import betamoon.gui.hud.GuiHudFrameState;
import betamoon.gui.hud.GuiHudRuntime;
import java.util.Arrays;
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
    private boolean hudAvailable;
    private int admittedIcons = 256;

    public void admitIcons(int maximum) {
        admittedIcons = maximum;
    }

    public void hudAvailable(boolean available) {
        hudAvailable = available;
    }

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
        GuiIconMeterModel model = model(values, candidate);
        if (model.maxIcons > admittedIcons) {
            throw new IllegalArgumentException(
                    "maxIcons cannot exceed the authored admission bound; refresh the definition");
        }
        String source = (String) values.getOrDefault("hudSource", "");
        if (!Arrays.asList("", "health", "armor", "breath").contains(source) || !source.isEmpty() && !hudAvailable) {
            throw new IllegalArgumentException("Live icon source requires hud_observation and health, armor or breath");
        }
        if (!source.isEmpty() && !Boolean.FALSE.equals(values.get("nativeEffects"))
                && (model.capacity != 10 || model.minimum != 0 || model.maximum != (source.equals("breath") ? 300 : 20)
                        || model.units != (source.equals("breath") ? 30 : 2))) {
            throw new IllegalArgumentException("Native icon effects require ten cells with native units/capacity");
        }
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
        String source = (String) values.getOrDefault("hudSource", "");
        GuiHudFrameState nativeFrame = source.isEmpty() || Boolean.FALSE.equals(values.get("nativeEffects"))
                ? null
                : GuiHudRuntime.frame();
        return new Frame(model, skin, source, nativeFrame);
    }

    public void paintIcons(GuiContext context) {
        Frame sampled = (Frame) context.getFrameContent();
        GuiIconMeterModel model = sampled.model;
        GuiIconMeterSkin skin = sampled.skin;
        if (model == null || skin == null) {
            throw new IllegalStateException("Icon meter must prepare before painting");
        }
        if (!sampled.visible) {
            return;
        }
        for (int index = 0; index < model.capacity; index++) {
            Rect bounds = Rect.fromPositionAndSize(getLeft() + model.column(index) * model.pitchX,
                    getTop() + model.row(index) * model.pitchY + sampled.offsets[index], model.width, model.height);
            skin.empty(context, bounds, sampled.flash);
            if (sampled.reference != null) {
                skin.fill(context, bounds, model.fraction(index, sampled.reference), model.steps, model.order, true);
            }
            double fraction = sampled.air
                    ? (index < sampled.fullAir ? 1 : index < sampled.fullAir + sampled.poppingAir ? 0.5 : 0)
                    : model.fraction(index, sampled.value);
            skin.fill(context, bounds, fraction, model.steps, model.order, false);
        }
    }

    private static final class Frame implements GuiFrameContent {
        final GuiIconMeterModel model;
        final GuiIconMeterSkin skin;
        final double value;
        final Double reference;
        final boolean visible;
        final boolean flash;
        final boolean air;
        final int fullAir;
        final int poppingAir;
        final int[] offsets;

        Frame(GuiIconMeterModel model, GuiIconMeterSkin skin, String source, GuiHudFrameState frame) {
            this.model = model;
            this.skin = skin;
            boolean health = source.equals("health");
            air = frame != null && source.equals("breath");
            flash = frame != null && health && frame.flash();
            value = frame == null ? model == null ? 0 : model.value : health ? frame.health() : frame.armor();
            reference = flash
                    ? Double.valueOf(frame.previousHealth())
                    : frame == null && model != null ? model.reference : null;
            visible = frame == null
                    || frame.survival() && (health || air ? !air || frame.airVisible() : frame.armor() > 0);
            fullAir = air ? frame.fullAir() : 0;
            poppingAir = air ? frame.poppingAir() : 0;
            offsets = new int[model == null ? 0 : model.capacity];
            if (health && frame != null) {
                for (int index = 0; index < offsets.length; index++) {
                    offsets[index] = frame.heartOffset(index);
                }
            }
        }

        public boolean contains(double localX, double localY) {
            return true;
        }
    }
}
