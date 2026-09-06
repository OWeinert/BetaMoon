package betamoon.gui.api.component;

import betamoon.gui.api.util.GuiText;
import betamoon.luamodloader.ScriptReloadStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;
import net.minecraft.src.Tessellator;
import org.lwjgl.opengl.GL11;

/** Draws and handles the compact script-reload status indicator. */
public final class GuiReloadStatusIndicator extends GuiComponentBase {
    private static final String RESOURCE_PATH = "/resources/betamoon/gui/";
    private static final String SUCCESS_TEXTURE = RESOURCE_PATH + "symbol_success.png";
    private static final String ERROR_TEXTURE = RESOURCE_PATH + "symbol_error.png";
    private static final String SPINNER_TEXTURE = RESOURCE_PATH + "symbol_spinner.png";
    private static final int SPINNER_FRAMES = 8;
    private static final long SUCCESS_HOLD_MS = 1000L;
    private static final long SUCCESS_FADE_MS = 2000L;
    private IGuiAction errorAction;
    private Minecraft minecraft;
    private int screenWidth;
    private int screenHeight;
    private boolean drewReloadingFrame;

    public GuiReloadStatusIndicator(IGuiAction errorAction) {
        this.errorAction = errorAction;
    }

    public void setMinecraft(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void setDisplaySize(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /** Starts the visual reload state and waits for its first rendered frame. */
    public void beginReload() {
        drewReloadingFrame = false;
        ScriptReloadStatus.begin();
    }

    public boolean hasDrawnReloadingFrame() {
        return drewReloadingFrame;
    }

    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
        ScriptReloadStatus.State state = ScriptReloadStatus.getState();
        if (state == ScriptReloadStatus.State.IDLE) {
            return;
        }
        int alpha = state == ScriptReloadStatus.State.SUCCESS ? successAlpha() : 255;
        if (alpha <= 0) {
            ScriptReloadStatus.expireSuccess();
            return;
        }
        if (state == ScriptReloadStatus.State.RELOADING) {
            drawSpinner(alpha);
            drewReloadingFrame = true;
        } else if (state == ScriptReloadStatus.State.SUCCESS) {
            drawSuccess(alpha);
        } else {
            drawFailure(alpha);
        }
        if (isMouseOver(mouseX, mouseY)) {
            GuiText.drawTooltip(font, screenWidth, screenHeight, tooltip(state), mouseX, mouseY);
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || ScriptReloadStatus.getState() != ScriptReloadStatus.State.FAILED
            || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (minecraft != null) {
            minecraft.sndManager.playSoundFX("random.click", 1.0F, 1.0F);
        }
        if (errorAction != null) {
            errorAction.onPress();
        }
        return true;
    }

    private int successAlpha() {
        long elapsed = System.currentTimeMillis() - ScriptReloadStatus.getCompletedAt();
        if (elapsed <= SUCCESS_HOLD_MS) return 255;
        long fadeElapsed = elapsed - SUCCESS_HOLD_MS;
        if (fadeElapsed >= SUCCESS_FADE_MS) return 0;
        return 255 - (int) (255L * fadeElapsed / SUCCESS_FADE_MS);
    }

    private void drawSpinner(int alpha) {
        int frame = (int) (System.currentTimeMillis() / 100L % SPINNER_FRAMES);
        drawSprite(SPINNER_TEXTURE, frame / (float) SPINNER_FRAMES,
            (frame + 1) / (float) SPINNER_FRAMES, alpha);
    }

    private void drawSuccess(int alpha) {
        drawSprite(SUCCESS_TEXTURE, 0.0F, 1.0F, alpha);
    }

    private void drawFailure(int alpha) {
        drawSprite(ERROR_TEXTURE, 0.0F, 1.0F, alpha);
    }

    /** Draws a 16-pixel sprite across the component's existing 17-pixel bounds. */
    private void drawSprite(String texture, float minU, float maxU, int alpha) {
        if (minecraft == null) return;
        minecraft.renderEngine.bindTexture(minecraft.renderEngine.getTexture(texture));
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha / 255.0F);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(left, bottom, 0.0D, minU, 1.0D);
        tessellator.addVertexWithUV(right, bottom, 0.0D, maxU, 1.0D);
        tessellator.addVertexWithUV(right, top, 0.0D, maxU, 0.0D);
        tessellator.addVertexWithUV(left, top, 0.0D, minU, 0.0D);
        tessellator.draw();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private String tooltip(ScriptReloadStatus.State state) {
        if (state == ScriptReloadStatus.State.RELOADING) return "Reloading scripts...";
        if (state == ScriptReloadStatus.State.SUCCESS) return "Scripts reloaded successfully";
        int errors = ScriptReloadStatus.getErrorCount();
        return "Reload completed with " + errors + (errors == 1 ? " error" : " errors")
            + ". Click to view details.";
    }
}
