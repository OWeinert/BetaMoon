package betamoon.gui.graphics;

import betamoon.gui.style.GuiDrawable;
import betamoon.gui.style.GuiIconMeterSkin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Immutable symbolic icon-skin metadata; compilation resolves only prepared
 * drawables.
 */
public final class GuiIconSkinDefinition extends GuiResourceDefinitions.Resource {
    public final int width;
    public final int height;
    public final int pitchX;
    public final int pitchY;
    public final String empty;
    public final String full;
    public final String reference;
    public final String flashEmpty;
    public final Map<Double, String> partials;
    public final Map<Double, String> referencePartials;
    public final int emptyTint;
    public final int fillTint;
    public final int referenceTint;

    public GuiIconSkinDefinition(int width, int height, int pitchX, int pitchY, String empty, String full,
            Map<Double, String> partials, String reference, Map<Double, String> referencePartials, String flashEmpty,
            int emptyTint, int fillTint, int referenceTint) {
        super(GuiResourceDefinitions.Kind.ICON_SKIN);
        this.width = width;
        this.height = height;
        this.pitchX = pitchX;
        this.pitchY = pitchY;
        this.empty = empty;
        this.full = full;
        this.partials = Collections.unmodifiableMap(new TreeMap<>(partials));
        this.reference = reference;
        this.referencePartials = Collections.unmodifiableMap(new TreeMap<>(referencePartials));
        this.flashEmpty = flashEmpty;
        this.emptyTint = emptyTint;
        this.fillTint = fillTint;
        this.referenceTint = referenceTint;
    }

    public List<String> references() {
        List<String> values = new ArrayList<>(partials.values());
        values.addAll(referencePartials.values());
        values.add(full);
        if (empty != null) {
            values.add(empty);
        }
        if (reference != null) {
            values.add(reference);
        }
        if (flashEmpty != null) {
            values.add(flashEmpty);
        }
        return values;
    }

    public GuiIconMeterSkin prepare(Function<String, GuiDrawable> drawables) {
        return new GuiIconMeterSkin(width, height, pitchX, pitchY, empty == null ? null : drawables.apply(empty),
                drawables.apply(full), resolve(partials, drawables),
                reference == null ? null : drawables.apply(reference), resolve(referencePartials, drawables),
                flashEmpty == null ? null : drawables.apply(flashEmpty), emptyTint, fillTint, referenceTint);
    }

    private static Map<Double, GuiDrawable> resolve(Map<Double, String> names,
            Function<String, GuiDrawable> drawables) {
        Map<Double, GuiDrawable> result = new TreeMap<>();
        for (Map.Entry<Double, String> entry : names.entrySet()) {
            result.put(entry.getKey(), drawables.apply(entry.getValue()));
        }
        return result;
    }
}
