package betamoon.gui.style;

import betamoon.gui.framework.GuiContext;

/**
 * Named prepared font metadata remains useful to template builders without
 * exposing a native font object.
 */
public final class GuiNamedFontFace implements GuiFontFace {
    public final String key;
    private final GuiFontFace font;
    public GuiNamedFontFace(String key, GuiFontFace font) {
        this.key = key;
        this.font = font;
    }

    public int width(GuiContext context, String text) {
        return font.width(context, text);
    }

    public int lineHeight(GuiContext context) {
        return font.lineHeight(context);
    }

    public void draw(GuiContext context, String text, int x, int y, int color, boolean shadow) {
        font.draw(context, text, x, y, color, shadow);
    }

    public void requireActive() {
        font.requireActive();
    }
}
