package betamoon.gui.framework;

import net.minecraft.src.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Minecraft screen adapter for one retained {@link GuiScene}. */
public abstract class GuiComponentScreen extends GuiScreen {
    private final GuiScene scene = new GuiScene();

    protected abstract GuiElement createContent();

    protected void drawSceneBackground(int mouseX, int mouseY, float partialTicks) {
    }

    protected void updateSceneState(int mouseX, int mouseY, float partialTicks) {
    }

    protected void onSceneUpdate() {
    }

    protected final GuiScene getScene() {
        return scene;
    }

    protected final GuiContext getGuiContext() {
        return scene.getContext();
    }

    protected final void showScreen(GuiScreen screen) {
        if (mc != null) {
            mc.displayGuiScreen(screen);
        }
    }

    protected final void shutdownGame() {
        if (mc != null) {
            mc.shutdown();
        }
    }

    @Override
    public void initGui() {
        controlList.clear();
        updateSceneEnvironment();
        scene.setContent(createContent());
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateSceneEnvironment();
        updateSceneState(mouseX, mouseY, partialTicks);
        drawSceneBackground(mouseX, mouseY, partialTicks);
        scene.render(mouseX, mouseY, partialTicks);
    }

    @Override
    public void updateScreen() {
        scene.update();
        onSceneUpdate();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scene.mouseScrolled(mouseX, mouseY, wheel, isShiftDown());
        }
        if (Mouse.isButtonDown(0)) {
            scene.mouseDragged(mouseX, mouseY, true);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (!scene.mousePressed(mouseX, mouseY, button)) {
            super.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        if (!scene.mouseReleased(mouseX, mouseY, button)) {
            super.mouseMovedOrUp(mouseX, mouseY, button);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (!scene.keyTyped(typedChar, keyCode, isShiftDown())) {
            super.keyTyped(typedChar, keyCode);
        }
    }

    private void updateSceneEnvironment() {
        int displayWidth = mc == null ? width : mc.displayWidth;
        int displayHeight = mc == null ? height : mc.displayHeight;
        scene.updateEnvironment(mc, fontRenderer, width, height, displayWidth, displayHeight);
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }
}
