package betamoon.gui;

import betamoon.gui.api.GuiColors;
import betamoon.gui.api.GuiLayout;
import betamoon.gui.api.GuiUtils;
import betamoon.scriptloader.LuaModLoader;
import betamoon.scriptloader.LuaScriptRegistry;
import betamoon.scriptloader.ScriptMod;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiScreen;
import org.lwjgl.input.Mouse;

public class GuiScriptsScreen extends GuiScreen {
    private static final int BUTTON_BACK = 0;
    private final GuiScreen parent;
    private int backButtonY;
    private final GuiScriptListPanel listPanel = new GuiScriptListPanel();
    private final GuiScriptInfoPanel infoPanel = new GuiScriptInfoPanel();
    private static final int BUTTON_DEBUG = 1;
    private static final int BUTTON_OPEN_SCRIPTS = 2;

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
        backButtonY = GuiLayout.alignBottom(this.height, 20, 20);
        listPanel.reset();
        int debugButtonWidth = 90;
        int backButtonWidth = debugButtonWidth;
        this.controlList.add(new GuiButton(BUTTON_BACK, 10, backButtonY, backButtonWidth, 20, "Back"));
        int scriptsButtonWidth = 200;
        int scriptsButtonX = GuiLayout.centerX(this.width, scriptsButtonWidth);
        this.controlList.add(new GuiButton(BUTTON_OPEN_SCRIPTS, scriptsButtonX, backButtonY, scriptsButtonWidth, 20, "Open Scripts Folder"));
        int debugButtonX = GuiLayout.alignRight(this.width, debugButtonWidth, 10);
        this.controlList.add(new GuiButton(BUTTON_DEBUG, debugButtonX, backButtonY, debugButtonWidth, 20, "Debug"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_BACK) {
            this.mc.displayGuiScreen(this.parent);
        } else if (button.id == BUTTON_DEBUG) {
            this.mc.displayGuiScreen(new GuiDebugMenuPopup(this));
        } else if (button.id == BUTTON_OPEN_SCRIPTS) {
            openScriptsDir();
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
        // Sort only for display so the registry order stays intact.
        List sortedEntries = getSortedEntries(entries);
        int bottomSeparatorY = backButtonY - 8;
        // Bottom separator also acts as a visual bound above the Back button.
        GuiUtils.drawHorizontalLine(10, this.width - 10, bottomSeparatorY, GuiColors.LINE_WHITE);
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

    private void openScriptsDir() {
        File scriptsDir = LuaModLoader.getLuaModsDir();
        if (scriptsDir == null) {
            return;
        }
        GuiUtils.openInFileExplorer(scriptsDir);
    }
}
