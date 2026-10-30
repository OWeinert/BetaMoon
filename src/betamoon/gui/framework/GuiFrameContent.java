package betamoon.gui.framework;

/**
 * Optional immutable Java-only per-element geometry/content sampled with the
 * displayed frame.
 */
public interface GuiFrameContent {
    boolean contains(double localX, double localY);
}
