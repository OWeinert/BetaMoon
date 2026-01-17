package betamoon.gui;

import betamoon.gui.api.component.GuiActionButton;
import betamoon.gui.api.util.GuiColors;
import betamoon.gui.api.layout.GuiLayout;
import betamoon.gui.api.component.GuiLine;
import betamoon.gui.api.screen.GuiScreenBase;
import betamoon.gui.api.util.GuiUtils;
import betamoon.gui.api.component.IGuiAction;
import betamoon.scriptloader.LuaModLoader;
import betamoon.scriptloader.LuaScriptRegistry;
import betamoon.scriptloader.ScriptMod;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.src.GuiScreen;

public class GuiScriptsScreen extends GuiScreenBase {
    private final GuiScreen parent;
    private final GuiScriptListPanel listPanel = new GuiScriptListPanel();
    private final GuiScriptInfoPanel infoPanel = new GuiScriptInfoPanel();
    private final GuiActionButton backButton;
    private final GuiActionButton openScriptsButton;
    private final GuiActionButton debugButton;
    private final GuiLine bottomSeparator;
    private int backButtonY;

    /**
     * Creates the scripts screen with a parent GUI to return to.
     *
     * @param parent parent GUI screen
     */
    public GuiScriptsScreen(GuiScreen parent) {
        this.parent = parent;
        backButton = new GuiActionButton("Back", new IGuiAction() {
            public void onPress() {
                GuiScriptsScreen.this.mc.displayGuiScreen(GuiScriptsScreen.this.parent);
            }
        });
        openScriptsButton = new GuiActionButton("Open Scripts Folder", new IGuiAction() {
            public void onPress() {
                openScriptsDir();
            }
        });
        debugButton = new GuiActionButton("Debug", new IGuiAction() {
            public void onPress() {
                GuiScriptsScreen.this.mc.displayGuiScreen(new GuiDebugMenuPopup(GuiScriptsScreen.this));
            }
        });
        bottomSeparator = new GuiLine(false, GuiColors.LINE_WHITE);
    }

    @Override
    protected void buildGui() {
        listPanel.reset();
        backButton.setMinecraft(this.mc);
        openScriptsButton.setMinecraft(this.mc);
        debugButton.setMinecraft(this.mc);
        root.addChild(listPanel);
        root.addChild(infoPanel);
        root.addChild(bottomSeparator);
        root.addChild(backButton);
        root.addChild(openScriptsButton);
        root.addChild(debugButton);
    }

    @Override
    protected void layoutComponents() {
        float headerScale = 1.35F;
        backButtonY = GuiLayout.alignBottom(this.height, 20, 20);
        int debugButtonWidth = 90;
        int backButtonWidth = debugButtonWidth;
        int buttonHeight = 20;
        backButton.setBounds(10, backButtonY, 10 + backButtonWidth, backButtonY + buttonHeight);
        int scriptsButtonWidth = 200;
        int scriptsButtonX = GuiLayout.centerX(this.width, scriptsButtonWidth);
        openScriptsButton.setBounds(scriptsButtonX, backButtonY, scriptsButtonX + scriptsButtonWidth, backButtonY + buttonHeight);
        int debugButtonX = GuiLayout.alignRight(this.width, debugButtonWidth, 10);
        debugButton.setBounds(debugButtonX, backButtonY, debugButtonX + debugButtonWidth, backButtonY + buttonHeight);
        int bottomSeparatorY = backButtonY - 8;
        bottomSeparator.setBounds(10, bottomSeparatorY, this.width - 10, bottomSeparatorY + 1);

        int listWidth = Math.min(200, Math.max(120, this.width / 4));
        int listLeft = 10;
        int listRight = listLeft + listWidth;
        int listTop = 10;
        int listBottom = backButtonY - 10;
        listPanel.setBounds(listLeft, listTop, listRight, listBottom);
        listPanel.setHeaderScale(headerScale);
        listPanel.setDisplayMetrics(this.width, this.height, this.mc.displayWidth, this.mc.displayHeight);
        listPanel.layout(this.width, this.height);

        int detailLeft = listPanel.getSeparatorX() + 8;
        int detailRight = this.width - listPanel.getPadding();
        int detailTop = listPanel.getListTop();
        int detailBottom = listPanel.getListBottom();
        infoPanel.setBounds(detailLeft, detailTop, detailRight, detailBottom);
        infoPanel.setHeaderY(listPanel.getHeaderTextY());
        infoPanel.setHeaderScale(headerScale);
        infoPanel.setDisplayMetrics(this.width, this.height, this.mc.displayWidth, this.mc.displayHeight);
        super.layoutComponents();
    }

    @Override
    protected void updateGuiState(int mouseX, int mouseY, float partialTicks) {
        float headerScale = 1.35F;
        List entries = LuaScriptRegistry.getEntries();
        List sortedEntries = getSortedEntries(entries);
        listPanel.setHeaderScale(headerScale);
        listPanel.setEntries(sortedEntries);
        ScriptMod selected = listPanel.getSelectedEntry();
        infoPanel.setSelected(selected);
        infoPanel.setHeaderScale(headerScale);
    }

    @Override
    protected void drawBackground(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
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
