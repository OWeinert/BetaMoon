package betamoon.gui;

import betamoon.BetaMoonClient;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.widget.GuiButton;
import net.minecraft.src.GuiMainMenu;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Minecraft main menu with BetaMoon's retained Scripts button layered on top.
 */
public final class GuiBetaMoonMainMenu extends GuiMainMenu {
    private final GuiScene betaMoonScene = new GuiScene();
    private final MainMenuActions betaMoonActions = new MainMenuActions();

    @Override
    public void initGui() {
        super.initGui();
        updateSceneEnvironment();
        betaMoonScene.setContent(betaMoonActions);
        betaMoonActions.updateNotice();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateSceneEnvironment();
        super.drawScreen(mouseX, mouseY, partialTicks);
        betaMoonScene.render(mouseX, mouseY, partialTicks);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        betaMoonActions.updateNotice();
        betaMoonScene.update();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            betaMoonScene.mouseScrolled(mouseX, mouseY, wheel, isShiftDown());
        }
        if (Mouse.isButtonDown(0)) {
            betaMoonScene.mouseDragged(mouseX, mouseY, true);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        betaMoonScene.mousePressed(mouseX, mouseY, button);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        super.mouseMovedOrUp(mouseX, mouseY, button);
        betaMoonScene.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (!betaMoonScene.keyTyped(typedChar, keyCode, isShiftDown())) {
            super.keyTyped(typedChar, keyCode);
        }
    }

    private void updateSceneEnvironment() {
        int displayWidth = mc == null ? width : mc.displayWidth;
        int displayHeight = mc == null ? height : mc.displayHeight;
        betaMoonScene.updateEnvironment(mc, fontRenderer, width, height, displayWidth, displayHeight);
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private final class MainMenuActions extends GuiContainer {
        private final GuiButton scriptsButton = add(new GuiButton("Scripts", 90,
                () -> mc.displayGuiScreen(new GuiScreenScripts(GuiBetaMoonMainMenu.this))));
        private final GuiUpdateNotice updateNotice = add(
                new GuiUpdateNotice(GuiBetaMoonMainMenu.this, BetaMoonClient.getInstance().version()));

        private void updateNotice() {
            updateNotice.setRelease(BetaMoonClient.getInstance().getAvailableUpdate());
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            scriptsButton.arrange(context, Rect.fromPositionAndSize(getLeft() + 10, getBottom() - 40, 90, 20));
            Rect notice = GuiUpdateNotice.noticeBounds(getWidth(), getHeight());
            updateNotice.setCompact(notice.getWidth() < GuiUpdateNotice.CARD_WIDTH);
            updateNotice.arrange(context, notice);
        }
    }
}
