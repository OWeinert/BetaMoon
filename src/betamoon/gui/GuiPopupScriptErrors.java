package betamoon.gui;

import betamoon.gui.api.component.GuiActionButton;
import betamoon.gui.api.screen.GuiScreenPopup;
import betamoon.scriptloader.LuaScriptErrors;

import net.minecraft.src.GuiScreen;

public class GuiPopupScriptErrors extends GuiScreenPopup {
    private final GuiActionButton ignoreButton;
    private final GuiActionButton closeButton;
    private final GuiPanelScriptErrorList listPanel = new GuiPanelScriptErrorList();
    /**
     * Creates the popup for showing Lua script load errors.
     *
     * @param parent parent GUI to return to
     */
    public GuiPopupScriptErrors(GuiScreen parent) {
        super(parent);
        ignoreButton = new GuiActionButton("Ignore", () -> {
            LuaScriptErrors.ignore();
            GuiPopupScriptErrors.this.showScreen(GuiPopupScriptErrors.this.parent);
        });
        closeButton = new GuiActionButton("Close Game", () -> GuiPopupScriptErrors.this.shutdownGame());
    }

    @Override
    protected void initPopupGui() {
        ignoreButton.setMinecraft(this.mc);
        closeButton.setMinecraft(this.mc);
        popupRoot.addChild(ignoreButton);
        popupRoot.addChild(closeButton);
        popupRoot.addChild(listPanel);
    }

    @Override
    protected void layoutPopupComponents() {
        int buttonWidth = (panelWidth - 30) / 2;
        int buttonY = panelTop + panelHeight - 30;
        int buttonHeight = 20;
        int leftX = panelLeft + 10;
        int rightX = panelLeft + 20 + buttonWidth;
        ignoreButton.setBounds(leftX, buttonY, leftX + buttonWidth, buttonY + buttonHeight);
        closeButton.setBounds(rightX, buttonY, rightX + buttonWidth, buttonY + buttonHeight);
        int listTop = panelTop + 32;
        int listBottom = buttonY - 8;
        int listLeft = panelLeft + 10;
        int listRight = panelLeft + panelWidth - 10;
        listPanel.setBounds(listLeft, listTop, listRight, listBottom);
        listPanel.setDisplayMetrics(this.width, this.height, this.mc.displayWidth, this.mc.displayHeight);
    }

    @Override
    protected String getPopupTitle() {
        return "Scripts Loaded with Errors/Warnings";
    }

    @Override
    protected int getMaxPanelHeight() {
        return 260;
    }

    @Override
    protected int getMinPanelHeight() {
        return 140;
    }

}
