package betamoon.gui.style;

/**
 * A prepared atlas lease. Its existing resource owner supplies lifetime checks
 * and disposal.
 */
public interface GuiAtlasLease {
    int width();

    int height();

    int textureId();

    void requireActive();
}
