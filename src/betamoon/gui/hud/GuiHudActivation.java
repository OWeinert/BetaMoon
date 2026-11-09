package betamoon.gui.hud;

import betamoon.gui.screen.GuiPresentationActivation;

/**
 * Explicit owner request with a stable sequence for both additive and
 * replacement ordering.
 */
public final class GuiHudActivation extends GuiPresentationActivation {
    public final long sequence;
    private Runnable interactionCleanup;

    GuiHudActivation(String owner, String key, long sequence, Runnable cleanup) {
        super(owner, key, cleanup);
        this.sequence = sequence;
    }

    void interactionCleanup(Runnable cleanup) {
        interactionCleanup = cleanup;
    }

    @Override
    public void close() {
        try {
            super.close();
        } finally {
            Runnable cleanup = interactionCleanup;
            interactionCleanup = null;
            if (cleanup != null) {
                cleanup.run();
            }
        }
    }
}
