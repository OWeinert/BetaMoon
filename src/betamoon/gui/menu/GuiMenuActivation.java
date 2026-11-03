package betamoon.gui.menu;

import betamoon.gui.screen.GuiPresentationActivation;

/**
 * Explicit owned menu claim; registration alone never changes a native menu.
 */
public final class GuiMenuActivation extends GuiPresentationActivation {
    GuiMenuActivation(String owner, String key, Runnable cleanup) {
        super(owner, key, cleanup);
    }
}
