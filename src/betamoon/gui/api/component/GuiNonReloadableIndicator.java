package betamoon.gui.api.component;

import betamoon.gui.api.util.GuiText;
import betamoon.luamodloader.NonReloadableScriptRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;
import net.minecraft.src.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Draws the warning shown beside scripts that must remain active until restart.
 */
public final class GuiNonReloadableIndicator extends GuiComponentBase {
    public static final int DEFAULT_SIZE = 16;
    private static final String TEXTURE = "/resources/betamoon/gui/symbol_reload_blocked.png";
    private String sourceFileName;
    private Minecraft minecraft;
    private int screenWidth;
    private int screenHeight;
    private boolean tooltipDeferred;

    public GuiNonReloadableIndicator(String sourceFileName) {
        this.sourceFileName = sourceFileName;
        setBounds(0, 0, DEFAULT_SIZE, DEFAULT_SIZE);
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public void setMinecraft(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void layout(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /**
     * Allows a parent to draw the tooltip after its scroll viewport has stopped
     * clipping.
     */
    public void setTooltipDeferred(boolean deferred) {
        tooltipDeferred = deferred;
    }

    public boolean isVisible() {
        return isVisible(sourceFileName);
    }

    public boolean isHovered(int mouseX, int mouseY) {
        return isVisible() && right > left && bottom > top && isMouseOver(mouseX, mouseY) && mouseX < right
                && mouseY < bottom;
    }

    /** Returns whether the given script currently owns startup-only content. */
    public static boolean isVisible(String sourceFileName) {
        return NonReloadableScriptRegistry.contains(sourceFileName);
    }

    /** Draws the complete warning sprite within this component's bounds. */
    @Override
    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
        if (!isVisible() || minecraft == null || right <= left || bottom <= top) {
            return;
        }
        minecraft.renderEngine.bindTexture(minecraft.renderEngine.getTexture(TEXTURE));
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(left, bottom, 0.0D, 0.0D, 1.0D);
        tessellator.addVertexWithUV(right, bottom, 0.0D, 1.0D, 1.0D);
        tessellator.addVertexWithUV(right, top, 0.0D, 1.0D, 0.0D);
        tessellator.addVertexWithUV(left, top, 0.0D, 0.0D, 0.0D);
        tessellator.draw();
        GL11.glDisable(GL11.GL_BLEND);
        if (!tooltipDeferred) {
            drawTooltip(font, mouseX, mouseY);
        }
    }

    /** Draws an explanation when the mouse is over the visible component. */
    public void drawTooltip(FontRenderer font, int mouseX, int mouseY) {
        if (!isHovered(mouseX, mouseY)) {
            return;
        }
        String reason = NonReloadableScriptRegistry.reason(sourceFileName);
        String detail = reason == null || reason.length() == 0 ? "startup-only content" : reason;
        GuiText.drawTooltip(font, screenWidth, screenHeight,
                "Not hot-reloadable: " + detail + ". Restart Minecraft to apply changes.", mouseX, mouseY);
    }
}
