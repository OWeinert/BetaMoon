package betamoon.gui.framework;

import betamoon.gui.framework.GuiGeometry.Rect;

/**
 * Optional retained root sizing; ordinary legacy roots continue to receive the
 * complete viewport.
 */
public interface GuiViewportLayout {
    Rect viewportBounds(GuiContext context, Rect viewport);
}
