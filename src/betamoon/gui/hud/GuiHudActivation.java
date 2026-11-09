package betamoon.gui.hud;

import betamoon.gui.screen.GuiPresentationActivation;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Explicit owner request with a stable sequence for both additive and
 * replacement ordering.
 */
public final class GuiHudActivation extends GuiPresentationActivation {
    public final long sequence;
    private Set<GuiHudNativePart> requestedParts;
    private Runnable interactionCleanup;

    GuiHudActivation(String owner, String key, long sequence, Set<GuiHudNativePart> parts, Runnable cleanup) {
        super(owner, key, cleanup);
        this.sequence = sequence;
        definitionChanged(parts);
    }

    void definitionChanged(Set<GuiHudNativePart> parts) {
        EnumSet<GuiHudNativePart> copy = EnumSet.noneOf(GuiHudNativePart.class);
        copy.addAll(parts);
        requestedParts = Collections.unmodifiableSet(copy);
        Runnable cleanup = interactionCleanup;
        interactionCleanup = null;
        if (cleanup != null) {
            cleanup.run();
        }
    }

    public Set<GuiHudNativePart> requestedParts() {
        return requestedParts;
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
