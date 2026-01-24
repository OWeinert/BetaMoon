package betamoon.gui;

import betamoon.gui.api.component.GuiActionButton;
import betamoon.gui.api.layout.GuiLayout;
import betamoon.gui.api.screen.GuiMainMenuBase;

public class GuiBetaMoonMainMenu extends GuiMainMenuBase {
    private final GuiActionButton scriptsButton;

    public GuiBetaMoonMainMenu() {
        scriptsButton = new GuiActionButton("Scripts", () -> GuiBetaMoonMainMenu.this.showScreen(new GuiScreenScripts(GuiBetaMoonMainMenu.this)));
    }

    @Override
    protected void buildGui() {
        scriptsButton.setMinecraft(this.mc);
        root.addChild(scriptsButton);
    }

    @Override
    protected void layoutComponents() {
        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = GuiLayout.alignBottom(this.height, buttonHeight, 20);
        scriptsButton.setBounds(10, buttonY, 10 + buttonWidth, buttonY + buttonHeight);
        super.layoutComponents();
    }
}
