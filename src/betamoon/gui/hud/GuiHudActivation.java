package betamoon.gui.hud;

import betamoon.gui.screen.GuiPresentationActivation;

/**
 * Explicit owner request with a stable sequence for both additive and
 * replacement ordering.
 */
public final class GuiHudActivation extends GuiPresentationActivation {
    public final long sequence;

    GuiHudActivation(String owner, String key, long sequence, Runnable cleanup) {
        super(owner, key, cleanup);
        this.sequence = sequence;
    }
}
