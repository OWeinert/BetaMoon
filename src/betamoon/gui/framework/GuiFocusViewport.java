package betamoon.gui.framework;

import betamoon.gui.graphics.GraphicsClip;

/**
 * Projects focused descendant bounds after revealing/clipping them in this
 * viewport's layout coordinates.
 */
public interface GuiFocusViewport {
    GraphicsClip revealFocus(GraphicsClip bounds);
}
