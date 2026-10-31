package betamoon.gui.framework;

import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.presentation.GuiViewPublication;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Runs the existing retained measurement with prospective values, then restores
 * its measurement caches.
 */
public final class GuiMeasurementPreview {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private GuiMeasurementPreview() {
    }

    public static void validate(GuiElement element, Map<GuiElement, GuiViewPublication> values) {
        GuiContext context = element.layoutContext();
        if (context == null || values.isEmpty()) {
            return;
        }
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Retained measurement preview cannot reenter itself");
        }
        GuiElement root = element;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        Frame frame = new Frame(values);
        CURRENT.set(frame);
        try (LuaCallbackScope callback = LuaCallbackScope.binding()) {
            root.measure(context, Constraints.tight(context.getScreenWidth(), context.getScreenHeight()));
        } finally {
            for (Map.Entry<GuiElement, Size> saved : frame.sizes.entrySet()) {
                saved.getKey().restoreMeasuredSize(saved.getValue());
            }
            CURRENT.remove();
        }
    }

    public static GuiViewPublication values(GuiElement element) {
        Frame frame = CURRENT.get();
        return frame == null ? null : frame.values.get(element);
    }

    static void remember(GuiElement element) {
        Frame frame = CURRENT.get();
        if (frame != null) {
            frame.sizes.putIfAbsent(element, element.getMeasuredSize());
        }
    }

    static boolean collapsed(GuiElement element, boolean current) {
        GuiViewPublication publication = values(element);
        if (publication == null) {
            return current;
        }
        Map<String, Object> values = publication.values;
        return Boolean.FALSE.equals(values.get("visible")) || Boolean.FALSE.equals(values.get("__conditionVisible"))
                || "collapsed".equals(values.get("visibility"));
    }

    private static final class Frame {
        final Map<GuiElement, GuiViewPublication> values;
        final Map<GuiElement, Size> sizes = new IdentityHashMap<>();

        Frame(Map<GuiElement, GuiViewPublication> values) {
            this.values = values;
        }
    }
}
