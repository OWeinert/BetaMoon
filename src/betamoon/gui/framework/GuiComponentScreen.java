package betamoon.gui.framework;

import betamoon.client.control.input.ClientInputRuntime;
import net.minecraft.src.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Minecraft screen adapter for one retained {@link GuiScene}. */
public abstract class GuiComponentScreen extends GuiScreen {
    private final GuiScene scene = new GuiScene();
    private int pointerX;
    private int pointerY;
    private int inputBatchDepth;
    private boolean closed;

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
        closed = false;
        controlList.clear();
        updateSceneEnvironment();
        GuiElement content = createContent();
        if (scene.getContent() != content) {
            scene.setContent(content);
        }
        prepareSceneState();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        pointerX = mouseX;
        pointerY = mouseY;
        drawSceneBackground(mouseX, mouseY, partialTicks);
        scene.render(mouseX, mouseY, partialTicks);
    }

    /**
     * Render this scene without its background when another dialog owns the dim
     * layer.
     */
    protected final void drawScene(int mouseX, int mouseY, float partialTicks) {
        pointerX = mouseX;
        pointerY = mouseY;
        scene.render(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        closed = true;
        scene.cancelPointerCapture();
        super.onGuiClosed();
    }

    @Override
    public void updateScreen() {
        if (closed) {
            return;
        }
        scene.runActions();
        if (closed) {
            return;
        }
        onSceneUpdate();
        prepareSceneState();
    }

    /**
     * Update/publication entry, including initial activation and completed input
     * batches.
     */
    protected final void prepareSceneState() {
        updateSceneEnvironment();
        updateSceneState(pointerX, pointerY, 0);
        scene.prepare();
    }

    @Override
    public void handleMouseInput() {
        if (ClientInputRuntime.consumesCurrentMouseEvent()) {
            return;
        }
        inputBatchDepth++;
        try {
            super.handleMouseInput();
            int mouseX = Mouse.getEventX() * width / mc.displayWidth;
            int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            int wheel = Mouse.getEventDWheel();
            if (wheel != 0) {
                scene.mouseScrolled(mouseX, mouseY, wheel, isShiftDown());
            }
            int capturedButton = scene.capturedPointerButton();
            if (capturedButton >= 0 || Mouse.isButtonDown(0)) {
                scene.mouseDragged(mouseX, mouseY, Mouse.isButtonDown(capturedButton >= 0 ? capturedButton : 0));
            }
            pointerX = mouseX;
            pointerY = mouseY;
        } finally {
            inputBatchDepth--;
            prepareAfterInput();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        pointerX = mouseX;
        pointerY = mouseY;
        if (scene.mousePressed(mouseX, mouseY, button)) {
            ClientInputRuntime.router().claimGuiGesture(button, this);
        } else {
            super.mouseClicked(mouseX, mouseY, button);
        }
        prepareAfterInput();
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        pointerX = mouseX;
        pointerY = mouseY;
        if (!scene.mouseReleased(mouseX, mouseY, button)) {
            super.mouseMovedOrUp(mouseX, mouseY, button);
        }
        prepareAfterInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (scene.keyTyped(typedChar, keyCode, isShiftDown())) {
            if (keyCode > 0) {
                ClientInputRuntime.router().claimGuiKey(keyCode, this);
            }
        } else {
            super.keyTyped(typedChar, keyCode);
        }
        prepareAfterInput();
    }

    @Override
    public void handleKeyboardInput() {
        if (ClientInputRuntime.consumesCurrentKeyboardEvent()) {
            return;
        }
        inputBatchDepth++;
        try {
            if (Keyboard.getEventKeyState()) {
                super.handleKeyboardInput();
            } else {
                scene.keyReleased(Keyboard.getEventKey(), isShiftDown());
            }
        } finally {
            inputBatchDepth--;
            prepareAfterInput();
        }
    }

    private void prepareAfterInput() {
        if (inputBatchDepth == 0 && !closed) {
            prepareSceneState();
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
