package betamoon.gui;

import betamoon.gui.api.GuiLayout;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiMainMenu;

public class GuiBetaMoonMainMenu extends GuiMainMenu {
    private static final int BUTTON_ID = 50;
    private static final int BUTTON_PADDING = 10;

    @Override
    public void initGui() {
        super.initGui();
        // Place the Scripts button in the lower-left corner with padding.
        int buttonY = GuiLayout.alignBottom(this.height, 20, 20);
        this.controlList.add(new GuiButton(BUTTON_ID, 10, buttonY, 90, 20, "Scripts"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_ID) {
            this.mc.displayGuiScreen(new GuiScriptsScreen(this));
            return;
        }
        super.actionPerformed(button);
    }
}
