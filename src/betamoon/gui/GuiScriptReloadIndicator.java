package betamoon.gui;

import betamoon.gui.framework.GuiAction;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.luamodloader.ScriptReloadStatus;
import org.lwjgl.input.Keyboard;

/** Animated status marker for the script reload lifecycle. */
final class GuiScriptReloadIndicator extends GuiElement {
    private static final String RESOURCE_PATH = "/resources/betamoon/gui/";
    private static final String SUCCESS_TEXTURE = RESOURCE_PATH + "symbol_success.png";
    private static final String ERROR_TEXTURE = RESOURCE_PATH + "symbol_error.png";
    private static final String SPINNER_TEXTURE = RESOURCE_PATH + "symbol_spinner.png";
    private static final int SPINNER_FRAMES = 8;
    private static final long SUCCESS_HOLD_MS = 1000L;
    private static final long SUCCESS_FADE_MS = 2000L;

    private final GuiAction errorAction;

    GuiScriptReloadIndicator(GuiAction errorAction) {
        this.errorAction = errorAction;
        setFocusable(true);
    }

    @Override
    protected void renderElement(GuiContext context) {
        ScriptReloadStatus.State state = ScriptReloadStatus.getState();
        if (state == ScriptReloadStatus.State.IDLE) {
            return;
        }
        int alpha = state == ScriptReloadStatus.State.SUCCESS ? successAlpha(context.getCurrentTimeMillis()) : 255;
        if (alpha <= 0) {
            ScriptReloadStatus.expireSuccess();
            return;
        }
        if (state == ScriptReloadStatus.State.RELOADING) {
            int frame = (int) (context.getCurrentTimeMillis() / 100L % SPINNER_FRAMES);
            context.getRenderer().drawPackagedTexture(SPINNER_TEXTURE, getBounds(), frame / (float) SPINNER_FRAMES,
                    0.0F, (frame + 1) / (float) SPINNER_FRAMES, 1.0F, alpha);
        } else if (state == ScriptReloadStatus.State.SUCCESS) {
            context.getRenderer().drawPackagedTexture(SUCCESS_TEXTURE, getBounds(), 0.0F, 0.0F, 1.0F, 1.0F, alpha);
        } else {
            context.getRenderer().drawPackagedTexture(ERROR_TEXTURE, getBounds(), 0.0F, 0.0F, 1.0F, 1.0F, alpha);
        }
        if (isHovered(context)) {
            context.showTooltip(tooltip(state));
        }
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        if (ScriptReloadStatus.getState() != ScriptReloadStatus.State.FAILED || errorAction == null) {
            return false;
        }
        boolean clicked = event.getType() == GuiInputEvent.Type.POINTER_DOWN && event.getButton() == 0
                && contains(event.getMouseX(), event.getMouseY());
        boolean activated = event.getType() == GuiInputEvent.Type.KEY_TYPED && isFocused()
                && (event.getKeyCode() == Keyboard.KEY_RETURN || event.getKeyCode() == Keyboard.KEY_NUMPADENTER
                        || event.getKeyCode() == Keyboard.KEY_SPACE);
        if (!clicked && !activated) {
            return false;
        }
        requestFocus(context);
        if (context.getMinecraft() != null) {
            context.getMinecraft().sndManager.playSoundFX("random.click", 1.0F, 1.0F);
        }
        errorAction.run();
        return true;
    }

    private static int successAlpha(long now) {
        long elapsed = now - ScriptReloadStatus.getCompletedAt();
        if (elapsed <= SUCCESS_HOLD_MS) {
            return 255;
        }
        long fadeElapsed = elapsed - SUCCESS_HOLD_MS;
        if (fadeElapsed >= SUCCESS_FADE_MS) {
            return 0;
        }
        return 255 - (int) (255L * fadeElapsed / SUCCESS_FADE_MS);
    }

    private static String tooltip(ScriptReloadStatus.State state) {
        if (state == ScriptReloadStatus.State.RELOADING) {
            return "Reloading scripts...";
        }
        if (state == ScriptReloadStatus.State.SUCCESS) {
            return "Scripts reloaded successfully";
        }
        int errors = ScriptReloadStatus.getErrorCount();
        return "Reload completed with " + errors + (errors == 1 ? " error" : " errors")
                + ". Click to view details.";
    }
}
