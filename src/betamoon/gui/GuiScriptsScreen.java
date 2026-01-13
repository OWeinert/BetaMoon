package betamoon.gui;

import betamoon.scriptloader.LuaScriptRegistry;
import betamoon.scriptloader.ScriptMod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiScreen;
import org.lwjgl.input.Mouse;

public class GuiScriptsScreen extends GuiScreen {
    private final GuiScreen parent;
    private int backButtonY;
    private final GuiScriptListPanel listPanel = new GuiScriptListPanel();
    private final GuiScriptInfoPanel infoPanel = new GuiScriptInfoPanel();

    /**
     * Creates the scripts screen with a parent GUI to return to.
     *
     * @param parent parent GUI screen
     */
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
        List sortedEntries = getSortedEntries(entries);
        int bottomSeparatorY = backButtonY - 8;
        GuiUtils.drawHorizontalLine(10, this.width - 10, bottomSeparatorY, 0xFFFFFFFF);
        listPanel.draw(this.fontRenderer, this.width, this.height, this.mc.displayWidth, this.mc.displayHeight, bottomSeparatorY, headerScale, sortedEntries);

        ScriptMod selected = listPanel.getSelectedEntry(sortedEntries);
        int detailLeft = listPanel.getSeparatorX() + 8;
        int detailRight = this.width - listPanel.getPadding();
        int headerY = listPanel.getHeaderTextY();
        int detailTop = listPanel.getListTop();
        int detailBottom = listPanel.getListBottom();
        infoPanel.draw(this.fontRenderer, selected, detailLeft, detailRight, headerY, detailTop, detailBottom,
            this.width, this.height, this.mc.displayWidth, this.mc.displayHeight, headerScale);
        super.drawScreen(var1, var2, var3);
    }

    /**
     * Returns a GUI-only sorted list with failed scripts first and names sorted alphabetically.
     *
     * @param entries unsorted script entries
     * @return sorted list for display
     */
    private static List getSortedEntries(List entries) {
        if (entries == null || entries.isEmpty()) {
            return entries;
        }
        List sorted = new ArrayList(entries);
        Collections.sort(sorted, new Comparator() {
            public int compare(Object left, Object right) {
                ScriptMod a = (ScriptMod) left;
                ScriptMod b = (ScriptMod) right;
                boolean failedA = a != null && a.isFailed();
                boolean failedB = b != null && b.isFailed();
                if (failedA != failedB) {
                    return failedA ? -1 : 1;
                }
                String nameA = a == null ? "" : safeName(a.getSortName());
                String nameB = b == null ? "" : safeName(b.getSortName());
                return nameA.compareToIgnoreCase(nameB);
            }
        });
        return sorted;
    }

    /**
     * Normalizes a name for sorting, falling back to an empty string.
     *
     * @param name input name
     * @return non-null name for sorting
     */
    private static String safeName(String name) {
        return name == null ? "" : name;
    }
}
