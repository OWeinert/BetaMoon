package betamoon.gui;

import betamoon.gui.api.component.GuiActionButton;
import betamoon.gui.api.layout.GuiLayout;
import betamoon.gui.api.screen.GuiMainMenuBase;
import betamoon.gui.api.component.IGuiAction;

public class GuiBetaMoonMainMenu extends GuiMainMenuBase {
    private final GuiActionButton scriptsButton;

    public GuiBetaMoonMainMenu() {
        scriptsButton = new GuiActionButton("Scripts", new IGuiAction() {
            public void onPress() {
                GuiBetaMoonMainMenu.this.mc.displayGuiScreen(new GuiScriptsScreen(GuiBetaMoonMainMenu.this));
            }
        });
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
