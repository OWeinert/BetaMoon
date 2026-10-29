package betamoon.gui.style;

import betamoon.gui.framework.GuiContext;

/** Prepared font metrics and Java drawing, shared by measuring and painting. */
public interface GuiFontFace {
    GuiFontFace NATIVE = new GuiFontFace() {
        @Override
        public int width(GuiContext context, String text) {
            return context.getRenderer().textWidth(text);
        }

        @Override
        public int lineHeight(GuiContext context) {
            return context.getRenderer().lineHeight();
        }

        @Override
        public void draw(GuiContext context, String text, int x, int y, int color, boolean shadow) {
            if (context.getFont() != null && (color >>> 24) != 0) {
                if (shadow) {
                    context.getFont().drawStringWithShadow(text, x, y, color);
                } else {
                    context.getFont().drawString(text, x, y, color);
                }
            }
        }
    };

    int width(GuiContext context, String text);

    default void requireActive() {
    }

    int lineHeight(GuiContext context);

    void draw(GuiContext context, String text, int x, int y, int color, boolean shadow);
}
