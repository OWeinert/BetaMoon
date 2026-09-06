package betamoon.gui;

import betamoon.gui.api.component.GuiActionButton;
import betamoon.gui.api.util.GuiColors;
import betamoon.gui.api.layout.GuiLayout;
import betamoon.gui.api.component.GuiLine;
import betamoon.gui.api.component.GuiReloadStatusIndicator;
import betamoon.gui.api.screen.GuiScreenBase;
import betamoon.io.IoUtils;
import betamoon.luamodloader.LuaModLoader;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptMod;
import betamoon.BetaMoonMain;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.src.GuiScreen;

public class GuiScreenScripts extends GuiScreenBase {
    private final GuiScreen parent;
    private final GuiPanelScriptList listPanel = new GuiPanelScriptList();
    private final GuiPanelScriptInfo infoPanel = new GuiPanelScriptInfo();
    private final GuiActionButton backButton;
    private final GuiActionButton openScriptsButton;
    private final GuiActionButton reloadButton;
    private final GuiActionButton debugButton;
    private final GuiLine bottomSeparator;
    private final GuiReloadStatusIndicator reloadIndicator;
    private int backButtonY;
    private boolean reloadPending;

    /**
     * Creates the scripts screen with a parent GUI to return to.
     *
     * @param parent parent GUI screen
     */
    public GuiScreenScripts(GuiScreen parent) {
        this.parent = parent;
        backButton = new GuiActionButton("Back", () -> GuiScreenScripts.this.showScreen(GuiScreenScripts.this.parent));
        openScriptsButton = new GuiActionButton("Open Scripts Folder", () -> openScriptsDir());
        reloadButton = new GuiActionButton("Reload Scripts", () -> requestReload());
        debugButton = new GuiActionButton("Debug", () -> GuiScreenScripts.this.showScreen(new GuiPopupDebugMenu(GuiScreenScripts.this)));
        bottomSeparator = new GuiLine(false, GuiColors.LINE_WHITE);
        reloadIndicator = new GuiReloadStatusIndicator(() -> showErrorPopup());
    }

    @Override
    protected void buildGui() {
        listPanel.reset();
        backButton.setMinecraft(this.mc);
        openScriptsButton.setMinecraft(this.mc);
        reloadButton.setMinecraft(this.mc);
        reloadIndicator.setMinecraft(this.mc);
        reloadIndicator.setDisplaySize(this.width, this.height);
        debugButton.setMinecraft(this.mc);
        root.addChild(listPanel);
        root.addChild(infoPanel);
        root.addChild(bottomSeparator);
        root.addChild(backButton);
        root.addChild(openScriptsButton);
        root.addChild(reloadButton);
        root.addChild(debugButton);
        // Draw last so its hover tooltip stays above the neighboring buttons.
        root.addChild(reloadIndicator);
    }

    @Override
    protected void layoutComponents() {
        float headerScale = 1.35F;
        backButtonY = GuiLayout.alignBottom(this.height, 20, 20);
        int debugButtonWidth = 90;
        int backButtonWidth = debugButtonWidth;
        int buttonHeight = 20;
        backButton.setBounds(10, backButtonY, 10 + backButtonWidth, backButtonY + buttonHeight);
        int scriptsButtonWidth = 140;
        int scriptsButtonX = GuiLayout.centerX(this.width, scriptsButtonWidth);
        openScriptsButton.setBounds(scriptsButtonX, backButtonY, scriptsButtonX + scriptsButtonWidth, backButtonY + buttonHeight);
        int debugButtonX = GuiLayout.alignRight(this.width, debugButtonWidth, 10);
        int reloadButtonWidth = 100;
        int reloadButtonX = debugButtonX - reloadButtonWidth - 6;
        reloadButton.setBounds(reloadButtonX, backButtonY,
            reloadButtonX + reloadButtonWidth, backButtonY + buttonHeight);
        int indicatorSize = 19;
        int indicatorRight = reloadButtonX - 6;
        reloadIndicator.setBounds(indicatorRight - indicatorSize,
            backButtonY + (buttonHeight - indicatorSize) / 2, indicatorRight,
            backButtonY + (buttonHeight - indicatorSize) / 2 + indicatorSize);
        reloadIndicator.setDisplaySize(this.width, this.height);
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
        List sorted = new ArrayList(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            sorted.add(entries.get(i));
        }
        Comparator comparator = Comparator
            .comparing((Object entry) -> Boolean.valueOf(entry != null && ((ScriptMod) entry).isFailed()))
            .reversed()
            .thenComparing(entry -> safeName(entry == null ? null : ((ScriptMod) entry).getSortName()),
                String.CASE_INSENSITIVE_ORDER);
        Collections.sort(sorted, comparator);
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
        IoUtils.openInFileExplorer(scriptsDir);
    }

    /** Reloads every Lua script and immediately presents any resulting issues. */
    private void reloadScripts() {
        BetaMoonMain main = BetaMoonMain.getInstance();
        if (main == null) {
            return;
        }
        main.reloadLuaScripts();
        reloadPending = false;
        reloadButton.setEnabled(true);
        if (LuaScriptErrors.shouldShowPopup()) {
            showScreen(new GuiPopupScriptErrors(this));
        }
    }

    private void requestReload() {
        if (reloadPending) return;
        reloadPending = true;
        reloadButton.setEnabled(false);
        reloadIndicator.beginReload();
    }

    /** Runs the pending reload only after the spinner has appeared in a rendered frame. */
    public void updateScreen() {
        super.updateScreen();
        if (reloadPending && reloadIndicator.hasDrawnReloadingFrame()) {
            reloadScripts();
        }
    }

    private void showErrorPopup() {
        if (!LuaScriptErrors.getEntries().isEmpty()) {
            showScreen(new GuiPopupScriptErrors(this));
        }
    }
}
