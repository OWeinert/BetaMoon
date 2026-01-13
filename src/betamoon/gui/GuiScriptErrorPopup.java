package betamoon.gui;

import betamoon.scriptloader.LuaScriptErrors;
import java.util.List;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiScreen;

public class GuiScriptErrorPopup extends GuiScreen {
    private final GuiScreen parent;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;

    /**
     * Creates the popup for showing Lua script load errors.
     *
     * @param parent parent GUI to return to
     */
    public GuiScriptErrorPopup(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        updatePanelGeometry();
        this.controlList.clear();
        // Lay out buttons within the popup bounds.
        int buttonWidth = (panelWidth - 30) / 2;
        int buttonY = panelTop + panelHeight - 30;
        this.controlList.add(new GuiButton(0, panelLeft + 10, buttonY, buttonWidth, 20, "Ignore"));
        this.controlList.add(new GuiButton(1, panelLeft + 20 + buttonWidth, buttonY, buttonWidth, 20, "Close Game"));
    }

    @Override
    protected void keyTyped(char var1, int var2) {
        // Require explicit choice; ignore escape key.
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
    public void drawScreen(int var1, int var2, float var3) {
        updatePanelGeometry();
        // Render on top of the parent screen when available.
        if (this.parent != null) {
            this.parent.drawScreen(var1, var2, var3);
        } else {
            this.drawDefaultBackground();
        }

        int left = panelLeft;
        int top = panelTop;
        int right = panelLeft + panelWidth;
        int bottom = panelTop + panelHeight;

        this.drawRect(left - 4, top - 4, right + 4, bottom + 4, 0xDD000000);
        this.drawRect(left, top, right, bottom, 0xFA1A1A1A);
        GuiUtils.drawScaledCenteredString(this.fontRenderer, "Lua Scripts Failed Loading!", this.width / 2, top + 8, 0xFFFFFF, 1.2F);
        GuiUtils.drawHorizontalLine(left + 10, right - 10, top + 24, 0xFFFFFFFF);

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

        super.drawScreen(var1, var2, var3);
    }

    /**
     * Updates popup sizing and centers it within the screen.
     */
    private void updatePanelGeometry() {
        panelWidth = Math.min(360, this.width - 40);
        panelHeight = Math.min(this.height - 80, 260);
        if (panelHeight < 180) {
            panelHeight = Math.max(140, this.height - 80);
        }
        panelLeft = this.width / 2 - panelWidth / 2;
        panelTop = this.height / 2 - panelHeight / 2;
    }

}
