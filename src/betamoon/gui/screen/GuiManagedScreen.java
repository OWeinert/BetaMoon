package betamoon.gui.screen;

import betamoon.client.control.input.ClientInputRuntime;
import betamoon.gui.framework.GuiComponentScreen;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiScene;
import net.minecraft.src.GuiScreen;
import org.lwjgl.input.Keyboard;

/**
 * Ordinary native adapter over the same scene used by Java menus; no generated
 * host class.
 */
public final class GuiManagedScreen extends GuiComponentScreen {
    private GuiNativeScreenPage page;

    void page(GuiNativeScreenPage page) {
        this.page = page;
    }

    GuiScene scene() {
        return getScene();
    }

    @Override
    protected GuiElement createContent() {
        return page == null ? null : page.tree().root();
    }

    @Override
    protected void drawSceneBackground(int x, int y, float partialTicks) {
        drawDefaultBackground();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return page != null && page.usable() && page.pauseGame();
    }

    @Override
    public void initGui() {
        if (page != null && page.usable()) {
            page.observe(() -> super.initGui());
        }
    }

    @Override
    public void updateScreen() {
        if (page != null && page.usable()) {
            page.observe(() -> super.updateScreen());
        }
    }

    @Override
    public void drawScreen(int x, int y, float partialTicks) {
        if (page == null || !page.usable()) {
            drawDefaultBackground();
            return;
        }
        try {
            page.paint(() -> super.drawScreen(x, y, partialTicks));
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            page.renderFailed(failure);
        }
    }

    @Override
    public void handleMouseInput() {
        if (page != null && page.usable()) {
            page.input(() -> super.handleMouseInput());
        }
    }

    @Override
    public void handleKeyboardInput() {
        if (page != null && page.usable()) {
            page.input(() -> super.handleKeyboardInput());
        }
    }

    @Override
    protected void keyTyped(char character, int code) {
        if (code != Keyboard.KEY_ESCAPE) {
            super.keyTyped(character, code);
            return;
        }
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (!getScene().keyTyped(character, code, shift) && page != null) {
            page.activation().back();
        }
        ClientInputRuntime.router().claimGuiKey(code, this);
        prepareSceneState();
    }

    @Override
    public void onGuiClosed() {
        GuiNativeScreenPage previous = page;
        if (previous != null) {
            previous.nativeClosed();
        }
        super.onGuiClosed();
    }
}
