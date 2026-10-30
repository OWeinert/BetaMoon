package betamoon.gui.screen;

import betamoon.client.control.input.ClientInputRuntime;
import betamoon.gui.framework.GuiComponentScreen;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiScene;
import net.minecraft.src.GuiScreen;
import org.lwjgl.input.Keyboard;
import java.util.function.Consumer;

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
        runUi(current -> current.prepareLogical(() -> super.initGui()));
    }

    @Override
    public void updateScreen() {
        runUi(current -> current.prepareLogical(() -> super.updateScreen()));
    }

    @Override
    public void drawScreen(int x, int y, float partialTicks) {
        GuiNativeScreenPage painting = page;
        if (painting == null || !painting.usable()) {
            drawDefaultBackground();
            return;
        }
        try {
            painting.paint(() -> super.drawScreen(x, y, partialTicks));
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            painting.renderFailed(failure);
        }
    }

    @Override
    public void handleMouseInput() {
        runUi(current -> current.input(() -> super.handleMouseInput()));
    }

    @Override
    public void handleKeyboardInput() {
        runUi(current -> current.input(() -> super.handleKeyboardInput()));
    }

    private void runUi(Consumer<GuiNativeScreenPage> operation) {
        GuiNativeScreenPage current = page;
        if (current == null || !current.usable()) {
            return;
        }
        try {
            operation.accept(current);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            current.renderFailed(failure);
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
