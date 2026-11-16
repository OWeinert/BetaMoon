package betamoon.gui.widget;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.presentation.GuiPresentationNode;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiVisualTemplate;
import java.util.Arrays;
import java.util.Map;
import org.lwjgl.opengl.GL11;

/** Prepared native sprite aliases remain ordinary, portable image controls. */
public final class GuiImageView extends GuiStyledView {
    private String sprite = "";
    private int texture = -1;

    public GuiImageView(GuiStyleSheet theme, GuiVisualTemplate template, Events events) {
        super("image", theme, template, events);
    }

    @Override
    public void validateValues(Map<String, Object> values) {
        super.validateValues(values);
        String name = (String) values.getOrDefault("nativeSprite", "");
        if (!Arrays.asList("", "minecraft:crosshair", "minecraft:hotbar", "minecraft:hotbar-cell",
                "minecraft:hotbar-selection").contains(name)) {
            throw new IllegalArgumentException("Unknown native image sprite: " + name);
        }
        if (!name.isEmpty() && !((String) values.getOrDefault("image", "")).isEmpty()) {
            throw new IllegalArgumentException("Image chooses nativeSprite or image");
        }
    }

    @Override
    public void publish(GuiPresentationNode node) {
        super.publish(node);
        sprite = (String) node.values().getOrDefault("nativeSprite", "");
    }

    @Override
    protected void updateElement(GuiContext context) {
        super.updateElement(context);
        if (!sprite.isEmpty()) {
            texture = context.getRenderer()
                    .preparePackagedTexture(sprite.equals("minecraft:crosshair") ? "/gui/icons.png" : "/gui/gui.png");
        }
    }

    public boolean paintSprite(GuiContext context) {
        if (sprite.isEmpty()) {
            return false;
        }
        boolean crosshair = sprite.equals("minecraft:crosshair");
        boolean selection = sprite.equals("minecraft:hotbar-selection");
        int width = crosshair ? 16 : selection ? 24 : sprite.equals("minecraft:hotbar") ? 182 : 22;
        int height = crosshair ? 16 : 22;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(crosshair ? GL11.GL_ONE_MINUS_DST_COLOR : GL11.GL_SRC_ALPHA,
                    crosshair ? GL11.GL_ONE_MINUS_SRC_COLOR : GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            float opacity = (float) context.getOpacity();
            // Inversion blends use RGB factors, so scale source RGB to interpolate with the
            // destination.
            GL11.glColor4f(crosshair ? opacity : 1, crosshair ? opacity : 1, crosshair ? opacity : 1,
                    crosshair ? 1 : opacity);
            Rect bounds = getBounds();
            double minV = selection ? 22.0 / 256 : 0;
            double maxV = (selection ? 44.0 : height) / 256;
            GL11.glBegin(GL11.GL_QUADS);
            try {
                GL11.glTexCoord2d(0, maxV);
                GL11.glVertex2i(bounds.getLeft(), bounds.getBottom());
                GL11.glTexCoord2d(width / 256.0, maxV);
                GL11.glVertex2i(bounds.getRight(), bounds.getBottom());
                GL11.glTexCoord2d(width / 256.0, minV);
                GL11.glVertex2i(bounds.getRight(), bounds.getTop());
                GL11.glTexCoord2d(0, minV);
                GL11.glVertex2i(bounds.getLeft(), bounds.getTop());
            } finally {
                GL11.glEnd();
            }
        } finally {
            GL11.glPopAttrib();
        }
        return true;
    }
}
