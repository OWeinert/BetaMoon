package betamoon.gui;

import betamoon.client.control.input.ClientInputRuntime;
import betamoon.gui.menu.GuiMenuRuntime;
import net.minecraft.src.GuiIngameMenu;

/**
 * Engine adapter for the common native-menu contribution host, including
 * agent-free recovery.
 */
public final class GuiBetaMoonIngameMenu extends GuiIngameMenu {
    @Override
    public void initGui() {
        super.initGui();
        GuiMenuRuntime.initialized(mc, this, controlList);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        GuiMenuRuntime.tick(mc);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GuiMenuRuntime.nativePaint(this, controlList, () -> super.drawScreen(mouseX, mouseY, partialTicks));
        GuiMenuRuntime.paint(this, mouseX, mouseY, partialTicks);
    }

    @Override
    public void handleKeyboardInput() {
        if (!ClientInputRuntime.consumesCurrentKeyboardEvent() && !GuiMenuRuntime.keyboard(this)) {
            super.handleKeyboardInput();
        }
    }

    @Override
    public void handleMouseInput() {
        if (!ClientInputRuntime.consumesCurrentMouseEvent() && !GuiMenuRuntime.mouse(this)) {
            super.handleMouseInput();
        }
    }

    @Override
    public void onGuiClosed() {
        GuiMenuRuntime.closed(this);
        super.onGuiClosed();
    }
}
