package betamoon.gui.api.component;

import betamoon.gui.api.util.GuiText;
import betamoon.gui.api.util.GuiUtils;
import betamoon.luamodloader.ScriptReloadStatus;
import betamoon.utils.McColors;
import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;

/** Draws and handles the compact script-reload status indicator. */
public final class GuiReloadStatusIndicator extends GuiComponentBase {
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
        int[][] points = new int[][] {
            { 7, 1 }, { 11, 3 }, { 13, 7 }, { 11, 11 },
            { 7, 13 }, { 3, 11 }, { 1, 7 }, { 3, 3 }
        };
        int active = (int) (System.currentTimeMillis() / 100L % points.length);
        for (int i = 0; i < points.length; i++) {
            int distance = (i - active + points.length) % points.length;
            int pointAlpha = Math.max(45, alpha - distance * 26);
            int x = left + points[i][0];
            int y = top + points[i][1];
            GuiUtils.drawRect(x, y, x + 2, y + 2, McColors.YELLOW.getArgb(pointAlpha));
        }
    }

    private void drawSuccess(int alpha) {
        drawCircle(McColors.DARK_GREEN.getArgb(alpha));
        int white = McColors.WHITE.getArgb(alpha);
        GuiUtils.drawRect(left + 4, top + 8, left + 6, top + 10, white);
        GuiUtils.drawRect(left + 6, top + 10, left + 8, top + 12, white);
        GuiUtils.drawRect(left + 8, top + 8, left + 10, top + 10, white);
        GuiUtils.drawRect(left + 10, top + 6, left + 12, top + 8, white);
        GuiUtils.drawRect(left + 12, top + 4, left + 14, top + 6, white);
    }

    private void drawFailure(int alpha) {
        drawCircle(McColors.DARK_RED.getArgb(alpha));
        int white = McColors.WHITE.getArgb(alpha);
        GuiUtils.drawRect(left + 7, top + 4, left + 10, top + 10, white);
        GuiUtils.drawRect(left + 7, top + 12, left + 10, top + 14, white);
    }

    private void drawCircle(int color) {
        GuiUtils.drawRect(left + 5, top + 1, left + 12, top + 2, color);
        GuiUtils.drawRect(left + 3, top + 2, left + 14, top + 4, color);
        GuiUtils.drawRect(left + 2, top + 4, left + 15, top + 13, color);
        GuiUtils.drawRect(left + 3, top + 13, left + 14, top + 15, color);
        GuiUtils.drawRect(left + 5, top + 15, left + 12, top + 16, color);
    }

    private String tooltip(ScriptReloadStatus.State state) {
        if (state == ScriptReloadStatus.State.RELOADING) return "Reloading scripts...";
        if (state == ScriptReloadStatus.State.SUCCESS) return "Scripts reloaded successfully";
        int errors = ScriptReloadStatus.getErrorCount();
        return "Reload completed with " + errors + (errors == 1 ? " error" : " errors")
            + ". Click to view details.";
    }
}
