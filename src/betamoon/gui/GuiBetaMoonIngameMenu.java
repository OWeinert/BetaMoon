package betamoon.gui;

import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiIngameMenu;

/** Adds BetaMoon's Scripts screen to Minecraft's in-game pause menu. */
public final class GuiBetaMoonIngameMenu extends GuiIngameMenu {
    private static final int SCRIPTS_BUTTON_ID = 900;
    private static final int SCRIPTS_BUTTON_WIDTH = 90;
    private static final int SCRIPTS_BUTTON_HEIGHT = 20;
    private static final int MENU_EDGE_INSET = 10;
    private static final int MENU_BOTTOM_INSET = 20;

    @Override
    @SuppressWarnings("unchecked") // Minecraft exposes the inherited control list without a generic type.
    public void initGui() {
        super.initGui();

        int buttonY = this.height - MENU_BOTTOM_INSET - SCRIPTS_BUTTON_HEIGHT;
        this.controlList.add(new GuiButton(SCRIPTS_BUTTON_ID, MENU_EDGE_INSET, buttonY,
                SCRIPTS_BUTTON_WIDTH, SCRIPTS_BUTTON_HEIGHT, "Scripts"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == SCRIPTS_BUTTON_ID) {
            this.mc.displayGuiScreen(new GuiScreenScripts(this));
            return;
        }
        super.actionPerformed(button);
    }
}
