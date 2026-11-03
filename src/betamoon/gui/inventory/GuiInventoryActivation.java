package betamoon.gui.inventory;

import betamoon.gui.screen.GuiPresentationActivation;

/**
 * Owned request to present the native player inventory with a registered
 * profile.
 */
public final class GuiInventoryActivation extends GuiPresentationActivation {
    GuiInventoryActivation(String owner, String key, Runnable cleanup) {
        super(owner, key, cleanup);
    }
}
