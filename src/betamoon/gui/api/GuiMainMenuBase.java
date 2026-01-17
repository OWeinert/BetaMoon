package betamoon.gui.api;

import net.minecraft.src.GuiMainMenu;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public abstract class GuiMainMenuBase extends GuiMainMenu {
    protected final GuiRoot root = new GuiRoot();
    private int lastWidth = -1;
    private int lastHeight = -1;

    protected abstract void buildGui();

    public void initGui() {
        super.initGui();
        root.clear();
        buildGui();
        layoutComponents();
    }

    protected void layoutComponents() {
        root.setBounds(0, 0, this.width, this.height);
        root.layout(this.width, this.height);
        lastWidth = this.width;
        lastHeight = this.height;
    }

    protected void updateGuiState(int mouseX, int mouseY, float partialTicks) {
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (this.width != lastWidth || this.height != lastHeight) {
            layoutComponents();
        }
        updateGuiState(mouseX, mouseY, partialTicks);
        super.drawScreen(mouseX, mouseY, partialTicks);
        root.draw(this.fontRenderer, mouseX, mouseY, partialTicks);
    }

    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        root.mouseScrolled(mouseX, mouseY, wheel, shiftDown);
        root.mouseDragged(mouseX, mouseY, Mouse.isButtonDown(0));
    }

    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        root.mouseClicked(mouseX, mouseY, button);
    }

    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        super.mouseMovedOrUp(mouseX, mouseY, button);
        root.mouseReleased(mouseX, mouseY, button);
    }

    protected void keyTyped(char typedChar, int keyCode) {
        if (!root.keyTyped(typedChar, keyCode)) {
            super.keyTyped(typedChar, keyCode);
        }
    }
}
