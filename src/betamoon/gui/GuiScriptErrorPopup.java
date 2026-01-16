package betamoon.gui;

import betamoon.gui.api.GuiPopupScreen;
import betamoon.gui.api.GuiUtils;
import betamoon.scriptloader.LuaScriptErrors;
import java.util.List;

import org.lwjgl.input.Keyboard;

import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiScreen;

public class GuiScriptErrorPopup extends GuiPopupScreen {
    /**
     * Creates the popup for showing Lua script load errors.
     *
     * @param parent parent GUI to return to
     */
    public GuiScriptErrorPopup(GuiScreen parent) {
        super(parent);
    }

    @Override
    protected void initPopupGui() {
        // Lay out buttons within the popup bounds.
        int buttonWidth = (panelWidth - 30) / 2;
        int buttonY = panelTop + panelHeight - 30;
        this.controlList.add(new GuiButton(0, panelLeft + 10, buttonY, buttonWidth, 20, "Ignore"));
        this.controlList.add(new GuiButton(1, panelLeft + 20 + buttonWidth, buttonY, buttonWidth, 20, "Close Game"));
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
        if (button.id == 0) {
            LuaScriptErrors.ignore();
            this.mc.displayGuiScreen(this.parent);
        }
        if (button.id == 1) {
            this.mc.shutdown();
        }
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
            this.fontRenderer.func_27278_a(entry, left + 20, entryY, contentWidth, 0xE0E0E0);
            int entryHeight = this.fontRenderer.func_27277_a(entry, contentWidth);
            y = entryY + entryHeight + 10;
            if (i < count - 1) {
                int lineY = entryY + entryHeight + 4;
                GuiUtils.drawHorizontalLine(left + 20, right - 20, lineY, GuiUtils.COLOR_LIST_SEPERATOR);
            }
        }
    }

}
