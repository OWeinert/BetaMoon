package betamoon.gui;

import betamoon.scriptloader.LuaScriptRegistry;
import betamoon.scriptloader.ScriptMod;
import java.util.List;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiScreen;
import org.lwjgl.input.Mouse;

public class GuiScriptsScreen extends GuiScreen {
    private final GuiScreen parent;
    private int backButtonY;
    private final GuiScriptListPanel listPanel = new GuiScriptListPanel();
    private final GuiScriptInfoPanel infoPanel = new GuiScriptInfoPanel();

    public GuiScriptsScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.controlList.clear();
        backButtonY = this.height - 40;
        listPanel.reset();
        this.controlList.add(new GuiButton(0, this.width / 2 - 100, backButtonY, "Back"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        listPanel.handleMouseInput(mouseX, mouseY, wheel, Mouse.isButtonDown(0));
        infoPanel.handleMouseInput(mouseX, mouseY, wheel, Mouse.isButtonDown(0));
    }

    @Override
    protected void mouseClicked(int var1, int var2, int var3) {
        super.mouseClicked(var1, var2, var3);
        listPanel.mouseClicked(var1, var2, var3);
        infoPanel.mouseClicked(var1, var2, var3);
    }

    @Override
    protected void mouseMovedOrUp(int var1, int var2, int var3) {
        super.mouseMovedOrUp(var1, var2, var3);
        listPanel.mouseReleased(var3);
        infoPanel.mouseReleased(var3);
    }

    @Override
    public void drawScreen(int var1, int var2, float var3) {
        this.drawDefaultBackground();
        float headerScale = 1.35F;
        List entries = LuaScriptRegistry.getEntries();
        int bottomSeparatorY = backButtonY - 8;
        GuiUtils.drawHorizontalLine(10, this.width - 10, bottomSeparatorY, 0xFFFFFFFF);
        listPanel.draw(this.fontRenderer, this.width, this.height, this.mc.displayWidth, this.mc.displayHeight, bottomSeparatorY, headerScale, entries);

        ScriptMod selected = listPanel.getSelectedEntry(entries);
        int detailLeft = listPanel.getSeparatorX() + 8;
        int detailRight = this.width - listPanel.getPadding();
        int headerY = listPanel.getHeaderTextY();
        int detailTop = listPanel.getListTop();
        int detailBottom = listPanel.getListBottom();
        infoPanel.draw(this.fontRenderer, selected, detailLeft, detailRight, headerY, detailTop, detailBottom,
            this.width, this.height, this.mc.displayWidth, this.mc.displayHeight, headerScale);
        super.drawScreen(var1, var2, var3);
    }
}
