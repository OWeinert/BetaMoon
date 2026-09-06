package betamoon.gui.api.component;

import betamoon.gui.api.util.GuiText;
import betamoon.luamodloader.NonReloadableScriptRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;
import net.minecraft.src.Tessellator;
import org.lwjgl.opengl.GL11;

/** Draws the warning shown beside scripts that must remain active until restart. */
public final class GuiNonReloadableIndicator {
    private static final String TEXTURE = "/resources/betamoon/gui/symbol_warning.png";

    private GuiNonReloadableIndicator() {
    }

    /** Returns whether the given script currently owns startup-only content. */
    public static boolean isVisible(String sourceFileName) {
        return NonReloadableScriptRegistry.contains(sourceFileName);
    }

    /** Draws the complete warning sprite scaled to the requested square. */
    public static void draw(Minecraft minecraft, int x, int y, int size) {
        if (minecraft == null || size <= 0) return;
        minecraft.renderEngine.bindTexture(minecraft.renderEngine.getTexture(TEXTURE));
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + size, 0.0D, 0.0D, 1.0D);
        tessellator.addVertexWithUV(x + size, y + size, 0.0D, 1.0D, 1.0D);
        tessellator.addVertexWithUV(x + size, y, 0.0D, 1.0D, 0.0D);
        tessellator.addVertexWithUV(x, y, 0.0D, 0.0D, 0.0D);
        tessellator.draw();
        GL11.glDisable(GL11.GL_BLEND);
    }

    /** Draws an explanation when the mouse is over the supplied icon bounds. */
    public static void drawTooltip(FontRenderer font, int screenWidth, int screenHeight,
        String sourceFileName, int x, int y, int size, int mouseX, int mouseY) {
        if (mouseX < x || mouseX >= x + size || mouseY < y || mouseY >= y + size) return;
        String reason = NonReloadableScriptRegistry.reason(sourceFileName);
        String detail = reason == null || reason.length() == 0 ? "startup-only content" : reason;
        GuiText.drawTooltip(font, screenWidth, screenHeight,
            "Not hot-reloadable: " + detail + ". Restart Minecraft to apply changes.", mouseX, mouseY);
    }
}
