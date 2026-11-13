package betamoon.gui.style;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiGeometry.Rect;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Prepared art for a passive icon meter; all drawables retain their existing
 * atlas owners.
 */
public final class GuiIconMeterSkin {
    public final int width;
    public final int height;
    public final int pitchX;
    public final int pitchY;
    public final GuiDrawable empty;
    public final GuiDrawable flashEmpty;
    private final GuiDrawable full;
    private final GuiDrawable reference;
    private final Map<Double, GuiDrawable> partials;
    private final Map<Double, GuiDrawable> referencePartials;
    private final int emptyTint;
    private final int fillTint;
    private final int referenceTint;

    public GuiIconMeterSkin(int width, int height, int pitchX, int pitchY, GuiDrawable empty, GuiDrawable full,
            Map<Double, GuiDrawable> partials, GuiDrawable reference, Map<Double, GuiDrawable> referencePartials,
            GuiDrawable flashEmpty, int emptyTint, int fillTint, int referenceTint) {
        if (width < 1 || width > 256 || height < 1 || height > 256 || pitchX < 1 || pitchX > 512 || pitchY < 1
                || pitchY > 512 || full == null) {
            throw new IllegalArgumentException("Invalid icon meter skin dimensions or full drawable");
        }
        this.width = width;
        this.height = height;
        this.pitchX = pitchX;
        this.pitchY = pitchY;
        this.empty = empty;
        this.full = full;
        this.partials = ordered(partials);
        this.reference = reference;
        this.referencePartials = ordered(referencePartials);
        this.flashEmpty = flashEmpty;
        this.emptyTint = emptyTint;
        this.fillTint = fillTint;
        this.referenceTint = referenceTint;
    }

    private static Map<Double, GuiDrawable> ordered(Map<Double, GuiDrawable> values) {
        if (values.size() > 32) {
            throw new IllegalArgumentException("Icon skins support at most 32 partial states");
        }
        for (Map.Entry<Double, GuiDrawable> value : values.entrySet()) {
            if (!Double.isFinite(value.getKey()) || value.getKey() <= 0 || value.getKey() >= 1
                    || value.getValue() == null) {
                throw new IllegalArgumentException("Partial skin states require unique fractions within (0,1)");
            }
        }
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }

    public int layers() {
        return 1 + (empty == null ? 0 : 1) + (reference == null ? 0 : 1);
    }

    public void empty(GuiContext context, Rect bounds, boolean flash) {
        GuiDrawable drawable = flash && flashEmpty != null ? flashEmpty : empty;
        if (drawable != null) {
            drawable.draw(context, bounds, emptyTint);
        }
    }

    public void fill(GuiContext context, Rect bounds, double fraction, boolean steps, String direction,
            boolean previous) {
        GuiDrawable drawable = previous ? reference : full;
        if (drawable == null || fraction <= 0) {
            return;
        }
        Map<Double, GuiDrawable> choices = previous ? referencePartials : partials;
        if (steps && fraction < 1) {
            drawable = null;
            for (Map.Entry<Double, GuiDrawable> entry : choices.entrySet()) {
                if (entry.getKey() <= fraction + 1.0e-9) {
                    drawable = entry.getValue();
                }
            }
            if (drawable == null) {
                return;
            }
        }
        if (!steps && fraction < 1) {
            int width = bounds.getWidth();
            int height = bounds.getHeight();
            boolean vertical = direction.equals("top_to_bottom") || direction.equals("bottom_to_top");
            int filled = (int) Math.round((vertical ? height : width) * fraction);
            Rect clip = vertical
                    ? Rect.fromPositionAndSize(bounds.getLeft(),
                            bounds.getTop() + (direction.equals("bottom_to_top") ? height - filled : 0), width, filled)
                    : Rect.fromPositionAndSize(
                            bounds.getLeft() + (direction.equals("right_to_left") ? width - filled : 0),
                            bounds.getTop(), filled, height);
            context.getRenderer().pushClip(clip);
            try {
                drawable.draw(context, bounds, previous ? referenceTint : fillTint);
            } finally {
                context.getRenderer().popClip();
            }
        } else {
            drawable.draw(context, bounds, previous ? referenceTint : fillTint);
        }
    }
}
