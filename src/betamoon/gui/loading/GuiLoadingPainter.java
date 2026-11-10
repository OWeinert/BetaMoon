package betamoon.gui.loading;

import betamoon.gui.framework.GuiRenderer;
import betamoon.gui.framework.GuiTheme;
import betamoon.gui.graphics.GraphicsStateGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.ARBMultitexture;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GLContext;

/**
 * Shared noninteractive painter. All resources are prepared before either
 * rendering path starts.
 */
public final class GuiLoadingPainter {
    private final Minecraft client;
    private final GuiLoadingResources resources;
    private final GuiLoadingStatus status;
    private final GuiRenderer renderer = new GuiRenderer(GuiTheme.DEFAULT);

    public GuiLoadingPainter(Minecraft client, GuiLoadingResources resources, GuiLoadingStatus status) {
        this.client = client;
        this.resources = resources;
        this.status = status;
    }

    public boolean styled() {
        GuiLoadingResources.Candidate candidate = resources.selected();
        return candidate != null && candidate.appearance != GuiLoadingAppearance.MINECRAFT;
    }

    public void paint(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        GuiLoadingResources.Candidate candidate = resources.selected();
        GuiLoadingAppearance appearance = candidate == null ? GuiLoadingAppearance.MINECRAFT : candidate.appearance;
        try (GraphicsStateGuard guard = GraphicsStateGuard.save()) {
            prepareProjection(width, height);
            renderer.drawRect(0, 0, width, height, 0xFF000000);
            if (candidate != null && candidate.image != null) {
                GuiLoadingGeometry background = GuiLoadingGeometry.background(appearance.mode, width, height,
                        candidate.image.width(), candidate.image.height(), appearance.tileSize);
                renderer.drawTexture(candidate.image.textureId(), background.bounds, background.minU, background.minV,
                        background.maxU, background.maxV, appearance.tint);
            } else {
                renderer.drawRect(0, 0, width, height, appearance.tint);
            }
            centered(status.title, width / 2, height / 2 - 20, width, appearance.titleColor);
            centered(status.message, width / 2, height / 2 + 4, width, appearance.textColor);
            if (status.percent() >= 0) {
                int left = width / 2 - 50;
                int top = height / 2 + 16;
                renderer.drawRect(left, top, left + 100, top + 2, appearance.barBackgroundColor);
                renderer.drawRect(left, top, left + status.percent(), top + 2, appearance.barColor);
            }
        }
    }

    private void centered(String text, int center, int top, int width, int color) {
        FontRenderer font = client.fontRenderer;
        if (font == null || color >>> 24 == 0) {
            return;
        }
        String fitted = GuiRenderer.trimToWidth(font, text, Math.max(0, width - 24));
        font.drawStringWithShadow(fitted, center - font.getStringWidth(fitted) / 2, top, color);
    }

    private void prepareProjection(int width, int height) {
        ContextCapabilities capabilities = GLContext.getCapabilities();
        if (capabilities.OpenGL13) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
        } else if (capabilities.GL_ARB_multitexture) {
            ARBMultitexture.glActiveTextureARB(GL13.GL_TEXTURE0);
        }
        GL11.glViewport(0, 0, client.displayWidth, client.displayHeight);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, width, height, 0, 100, 300);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0, 0, -200);
    }
}
