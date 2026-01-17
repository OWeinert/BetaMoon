package betamoon.gui;

import betamoon.gui.api.GuiActionButton;
import betamoon.gui.api.GuiColors;
import betamoon.gui.api.GuiPopupScreen;
import betamoon.gui.api.GuiUtils;
import betamoon.scriptloader.LuaScriptErrors;
import java.util.List;

import net.minecraft.src.GuiScreen;

public class GuiScriptErrorPopup extends GuiPopupScreen {
    private final GuiActionButton ignoreButton;
    private final GuiActionButton closeButton;
    /**
     * Creates the popup for showing Lua script load errors.
     *
     * @param parent parent GUI to return to
     */
    public GuiScriptErrorPopup(GuiScreen parent) {
        super(parent);
        ignoreButton = new GuiActionButton("Ignore", new GuiActionButton.Action() {
            public void onPress() {
                LuaScriptErrors.ignore();
                GuiScriptErrorPopup.this.mc.displayGuiScreen(GuiScriptErrorPopup.this.parent);
            }
        });
        closeButton = new GuiActionButton("Close Game", new GuiActionButton.Action() {
            public void onPress() {
                GuiScriptErrorPopup.this.mc.shutdown();
            }
        });
    }

    @Override
    protected void initPopupGui() {
        ignoreButton.setMinecraft(this.mc);
        closeButton.setMinecraft(this.mc);
        popupRoot.addChild(ignoreButton);
        popupRoot.addChild(closeButton);
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
    }

    @Override
    protected String getPopupTitle() {
        return "Lua Scripts Failed Loading!";
    }

    @Override
    protected int getMaxPanelHeight() {
        return 260;
    }

    @Override
    protected int getMinPanelHeight() {
        return 140;
    }

    @Override
    protected void drawPopupContents(int mouseX, int mouseY, float partialTicks) {
        int left = panelLeft;
        int top = panelTop;
        int right = panelLeft + panelWidth;
        // Draw each error entry with separators between them.
        List entries = LuaScriptErrors.getEntries();
        int y = top + 36;
        int contentWidth = panelWidth - 40;
        int count = entries.size();
        for (int i = 0; i < count; i++) {
            String entry = (String) entries.get(i);
            int entryY = y;
            this.fontRenderer.func_27278_a(entry, left + 20, entryY, contentWidth, GuiColors.TEXT_MUTED);
            int entryHeight = this.fontRenderer.func_27277_a(entry, contentWidth);
            y = entryY + entryHeight + 10;
            if (i < count - 1) {
                int lineY = entryY + entryHeight + 4;
                GuiUtils.drawHorizontalLine(left + 20, right - 20, lineY, GuiUtils.COLOR_LIST_SEPERATOR);
            }
        }
    }

}
