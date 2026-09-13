package betamoon.gui.framework;

import betamoon.gui.widget.GuiDialog;
import net.minecraft.src.GuiScreen;
import org.lwjgl.input.Keyboard;

/** Scene-backed modal screen that renders an existing parent behind its dialog. */
public abstract class GuiDialogScreen extends GuiComponentScreen {
    protected final GuiScreen parent;
    protected final GuiDialog dialog;

    protected GuiDialogScreen(GuiScreen parent, String title) {
        this.parent = parent;
        this.dialog = new GuiDialog(title);
    }

    @Override
    protected final GuiElement createContent() {
        return dialog;
    }

    @Override
    protected final void drawSceneBackground(int mouseX, int mouseY, float partialTicks) {
        if (parent != null) {
            parent.drawScreen(mouseX, mouseY, partialTicks);
        } else {
            drawDefaultBackground();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE && parent != null) {
            showScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}
