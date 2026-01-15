package betamoon.gui;

import betamoon.debug.DebugExports;
import betamoon.gui.api.GuiPopupScreen;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiScreen;
import org.lwjgl.input.Keyboard;

public class GuiDebugMenuPopup extends GuiPopupScreen {
    private static final int BUTTON_EXPORT_ALL = 0;
    private static final int BUTTON_EXPORT_RECIPES = 1;
    private static final int BUTTON_EXPORT_BLOCKS = 2;
    private static final int BUTTON_EXPORT_ITEMS = 3;
    private static final int BUTTON_CLOSE = 4;

    public GuiDebugMenuPopup(GuiScreen parent) {
        super(parent);
    }

    @Override
    protected void initPopupGui() {
        int buttonWidth = panelWidth - 40;
        int buttonX = panelLeft + 20;
        int buttonY = panelTop + 30;
        int buttonHeight = 20;
        int buttonGap = 6;

        this.controlList.add(new GuiButton(BUTTON_EXPORT_ALL, buttonX, buttonY, buttonWidth, buttonHeight, "Export All"));
        buttonY += buttonHeight + buttonGap;
        this.controlList.add(new GuiButton(BUTTON_EXPORT_RECIPES, buttonX, buttonY, buttonWidth, buttonHeight, "Export Recipes"));
        buttonY += buttonHeight + buttonGap;
        this.controlList.add(new GuiButton(BUTTON_EXPORT_BLOCKS, buttonX, buttonY, buttonWidth, buttonHeight, "Export Blocks"));
        buttonY += buttonHeight + buttonGap;
        this.controlList.add(new GuiButton(BUTTON_EXPORT_ITEMS, buttonX, buttonY, buttonWidth, buttonHeight, "Export Items"));
        int closeY = panelTop + panelHeight - 30;
        this.controlList.add(new GuiButton(BUTTON_CLOSE, buttonX, closeY, buttonWidth, buttonHeight, "Close"));
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

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE && this.parent != null) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        if (button.id == BUTTON_EXPORT_ALL) {
            Exception error = DebugExports.exportAll();
            this.mc.displayGuiScreen(new GuiDebugExportPopup(this, error));
        } else if (button.id == BUTTON_EXPORT_RECIPES) {
            Exception error = DebugExports.exportRecipes();
            this.mc.displayGuiScreen(new GuiDebugExportPopup(this, error));
        } else if (button.id == BUTTON_EXPORT_BLOCKS) {
            Exception error = DebugExports.exportBlocks();
            this.mc.displayGuiScreen(new GuiDebugExportPopup(this, error));
        } else if (button.id == BUTTON_EXPORT_ITEMS) {
            Exception error = DebugExports.exportItems();
            this.mc.displayGuiScreen(new GuiDebugExportPopup(this, error));
        } else if (button.id == BUTTON_CLOSE) {
            this.mc.displayGuiScreen(this.parent);
        }
    }
}
