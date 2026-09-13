package betamoon.gui;

import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiIngameMenu;

/** Adds BetaMoon's Scripts screen to Minecraft's in-game pause menu. */
public final class GuiBetaMoonIngameMenu extends GuiIngameMenu {
    private static final int SCRIPTS_BUTTON_ID = 900;

    @Override
    @SuppressWarnings("unchecked") // Minecraft exposes the inherited control list without a generic type.
    public void initGui() {
        super.initGui();

        // Vanilla leaves this full-width row unused between Statistics and Options.
        int buttonY = this.height / 4 + 56;
        this.controlList.add(new GuiButton(SCRIPTS_BUTTON_ID, this.width / 2 - 100, buttonY, "Scripts"));
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
