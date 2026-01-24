package betamoon.gui;

import betamoon.debug.DebugExports;
import betamoon.gui.api.component.GuiActionButton;
import betamoon.gui.api.screen.GuiScreenPopup;
import net.minecraft.src.GuiScreen;

public class GuiPopupDebugMenu extends GuiScreenPopup {
    private final GuiActionButton exportAllButton;
    private final GuiActionButton exportRecipesButton;
    private final GuiActionButton exportBlocksButton;
    private final GuiActionButton exportItemsButton;
    private final GuiActionButton closeButton;

    public GuiPopupDebugMenu(GuiScreen parent) {
        super(parent);
        exportAllButton = new GuiActionButton("Export All", () -> {
            Exception error = DebugExports.exportAll();
            GuiPopupDebugMenu.this.showScreen(new GuiPopupDebugExport(GuiPopupDebugMenu.this, error));
        });
        exportRecipesButton = new GuiActionButton("Export Recipes", () -> {
            Exception error = DebugExports.exportRecipes();
            GuiPopupDebugMenu.this.showScreen(new GuiPopupDebugExport(GuiPopupDebugMenu.this, error));
        });
        exportBlocksButton = new GuiActionButton("Export Blocks", () -> {
            Exception error = DebugExports.exportBlocks();
            GuiPopupDebugMenu.this.showScreen(new GuiPopupDebugExport(GuiPopupDebugMenu.this, error));
        });
        exportItemsButton = new GuiActionButton("Export Items", () -> {
            Exception error = DebugExports.exportItems();
            GuiPopupDebugMenu.this.showScreen(new GuiPopupDebugExport(GuiPopupDebugMenu.this, error));
        });
        closeButton = new GuiActionButton("Close", () -> GuiPopupDebugMenu.this.showScreen(GuiPopupDebugMenu.this.parent));
    }

    @Override
    protected void initPopupGui() {
        exportAllButton.setMinecraft(this.mc);
        exportRecipesButton.setMinecraft(this.mc);
        exportBlocksButton.setMinecraft(this.mc);
        exportItemsButton.setMinecraft(this.mc);
        closeButton.setMinecraft(this.mc);
        popupRoot.addChild(exportAllButton);
        popupRoot.addChild(exportRecipesButton);
        popupRoot.addChild(exportBlocksButton);
        popupRoot.addChild(exportItemsButton);
        popupRoot.addChild(closeButton);
    }

    @Override
    protected void layoutPopupComponents() {
        int buttonWidth = panelWidth - 40;
        int buttonX = panelLeft + 20;
        int buttonY = panelTop + 30;
        int buttonHeight = 20;
        int buttonGap = 6;

        exportAllButton.setBounds(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight);
        buttonY += buttonHeight + buttonGap;
        exportRecipesButton.setBounds(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight);
        buttonY += buttonHeight + buttonGap;
        exportBlocksButton.setBounds(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight);
        buttonY += buttonHeight + buttonGap;
        exportItemsButton.setBounds(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight);
        int closeY = panelTop + panelHeight - 30;
        closeButton.setBounds(buttonX, closeY, buttonX + buttonWidth, closeY + buttonHeight);
    }

    @Override
    protected String getPopupTitle() {
        return "Debug Menu";
    }

    @Override
    protected int getMaxPanelWidth() {
        return 260;
    }

    @Override
    protected int getMaxPanelHeight() {
        return 200;
    }

    @Override
    protected int getPanelHorizontalMargin() {
        return 60;
    }

}
