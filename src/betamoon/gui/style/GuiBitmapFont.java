package betamoon.gui.style;

import betamoon.gui.framework.GuiContext;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.src.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Bounded bitmap-font descriptor over an already prepared atlas; glyph geometry
 * is emitted in bulk.
 */
public final class GuiBitmapFont implements GuiFontFace {
    public static final class Glyph {
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final int advance;
        public final int offsetX;
        public final int offsetY;

        public Glyph(int x, int y, int width, int height, int advance, int offsetX, int offsetY) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.advance = advance;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    private final GuiAtlasLease atlas;
    private final int lineHeight;
    private final int[] codepoints;
    private final Glyph[] glyphs;
    private final Glyph fallback;

    public GuiBitmapFont(GuiAtlasLease atlas, int lineHeight, int fallback, Map<Integer, Glyph> glyphs) {
        if (atlas == null || glyphs == null || glyphs.isEmpty() || glyphs.size() > 4096 || lineHeight < 1
                || lineHeight > 512 || atlas.width() < 1 || atlas.height() < 1 || atlas.width() > 8192
                || atlas.height() > 8192 || !glyphs.containsKey(fallback)) {
            throw new IllegalArgumentException("Invalid bitmap font atlas, line height, glyph budget or fallback");
        }
        atlas.requireActive();
        this.atlas = atlas;
        this.lineHeight = lineHeight;
        codepoints = new int[glyphs.size()];
        int index = 0;
        for (Map.Entry<Integer, Glyph> entry : glyphs.entrySet()) {
            int codepoint = entry.getKey();
            Glyph glyph = entry.getValue();
            if (!Character.isValidCodePoint(codepoint) || codepoint >= 0xD800 && codepoint <= 0xDFFF || glyph == null
                    || glyph.x < 0 || glyph.y < 0 || glyph.width < 0 || glyph.height < 0
                    || (long) glyph.x + glyph.width > atlas.width() || (long) glyph.y + glyph.height > atlas.height()
                    || glyph.advance < 0 || glyph.advance > 1024 || Math.abs((long) glyph.offsetX) > 1024
                    || Math.abs((long) glyph.offsetY) > 1024) {
                throw new IllegalArgumentException("Invalid bitmap glyph: " + codepoint);
            }
            codepoints[index++] = codepoint;
        }
        Arrays.sort(codepoints);
        this.glyphs = new Glyph[codepoints.length];
        for (int i = 0; i < codepoints.length; i++) {
            this.glyphs[i] = glyphs.get(codepoints[i]);
        }
        this.fallback = glyphs.get(fallback);
    }

    @Override
    public void requireActive() {
        atlas.requireActive();
    }

    @Override
    public int width(GuiContext context, String text) {
        atlas.requireActive();
        if (text == null || text.length() > 65536) {
            throw new IllegalArgumentException("Bitmap text requires at most 65536 UTF-16 units");
        }
        int width = 0;
        for (int i = 0; i < text.length();) {
            int codepoint = text.codePointAt(i);
            width += glyph(codepoint).advance;
            i += Character.charCount(codepoint);
        }
        return width;
    }

    @Override
    public int lineHeight(GuiContext context) {
        atlas.requireActive();
        return lineHeight;
    }

    @Override
    public void draw(GuiContext context, String text, int x, int y, int color, boolean shadow) {
        atlas.requireActive();
        if (text.length() > 65536) {
            throw new IllegalArgumentException("Bitmap text exceeds 65536 UTF-16 units");
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.textureId());
            if (shadow) {
                batch(text, x + 1, y + 1, color & 0xFF000000 | (color & 0xFCFCFC) >>> 2);
            }
            batch(text, x, y, color);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private void batch(String text, int x, int y, int color) {
        GL11.glColor4f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F,
                (color >>> 24) / 255F);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        for (int i = 0; i < text.length();) {
            int codepoint = text.codePointAt(i);
            Glyph glyph = glyph(codepoint);
            int left = x + glyph.offsetX;
            int top = y + glyph.offsetY;
            double u = glyph.x / (double) atlas.width();
            double v = glyph.y / (double) atlas.height();
            double right = (glyph.x + glyph.width) / (double) atlas.width();
            double bottom = (glyph.y + glyph.height) / (double) atlas.height();
            if (glyph.width > 0 && glyph.height > 0) {
                tessellator.addVertexWithUV(left, top + glyph.height, 0, u, bottom);
                tessellator.addVertexWithUV(left + glyph.width, top + glyph.height, 0, right, bottom);
                tessellator.addVertexWithUV(left + glyph.width, top, 0, right, v);
                tessellator.addVertexWithUV(left, top, 0, u, v);
            }
            x += glyph.advance;
            i += Character.charCount(codepoint);
        }
        tessellator.draw();
    }

    private Glyph glyph(int codepoint) {
        int index = Arrays.binarySearch(codepoints, codepoint);
        return index >= 0 ? glyphs[index] : fallback;
    }
}
