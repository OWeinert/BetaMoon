package betamoon.gui;

import betamoon.BetaMoonMain;
import betamoon.gui.framework.GuiComponentScreen;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiLayouts.Axis;
import betamoon.gui.framework.GuiTheme;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiDivider;
import betamoon.io.IoUtils;
import betamoon.luamodloader.LuaModLoader;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptMod;
import betamoon.luamodloader.ScriptReloadStatus;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.src.GuiScreen;

/** BetaMoon script overview backed by the retained GUI scene. */
public final class GuiScreenScripts extends GuiComponentScreen {
    private static final float HEADER_SCALE = 1.35F;

    private final GuiScreen parent;
    private final GuiPanelScriptList listPanel = new GuiPanelScriptList();
    private final GuiPanelScriptInfo infoPanel = new GuiPanelScriptInfo();
    private final GuiButton backButton;
    private final GuiButton openScriptsButton;
    private final GuiButton reloadButton;
    private final GuiButton debugButton;
    private final GuiDivider bottomSeparator;
    private final GuiScriptReloadIndicator reloadIndicator;
    private final ScriptsLayout content = new ScriptsLayout();
    private boolean reloadPending;

    public GuiScreenScripts(GuiScreen parent) {
        this.parent = parent;
        backButton = new GuiButton("Back", 90, () -> showScreen(GuiScreenScripts.this.parent));
        openScriptsButton = new GuiButton("Open Scripts Folder", 140, this::openScriptsDirectory);
        reloadButton = new GuiButton("Reload Scripts", 100, this::requestReload);
        debugButton = new GuiButton("Debug", 90,
                () -> showScreen(new GuiPopupDebugMenu(GuiScreenScripts.this)));
        bottomSeparator = new GuiDivider(Axis.HORIZONTAL, GuiTheme.DEFAULT.line);
        reloadIndicator = new GuiScriptReloadIndicator(this::showErrorPopup);

        content.add(listPanel);
        content.add(infoPanel);
        content.add(bottomSeparator);
        content.add(backButton);
        content.add(openScriptsButton);
        content.add(reloadButton);
        content.add(debugButton);
        content.add(reloadIndicator);
    }

    @Override
    protected GuiElement createContent() {
        listPanel.reset();
        return content;
    }

    @Override
    protected void updateSceneState(int mouseX, int mouseY, float partialTicks) {
        List<ScriptMod> sortedEntries = getSortedEntries(LuaScriptRegistry.getEntries());
        listPanel.setHeaderScale(HEADER_SCALE);
        listPanel.setEntries(sortedEntries);
        infoPanel.setSelected(listPanel.getSelectedEntry());
        infoPanel.setHeaderScale(HEADER_SCALE);
        reloadIndicator.setEnabled(ScriptReloadStatus.getState() == ScriptReloadStatus.State.FAILED);
    }

    @Override
    protected void drawSceneBackground(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
    }

    private void openScriptsDirectory() {
        File scriptsDirectory = LuaModLoader.getLuaModsDir();
        if (scriptsDirectory != null) {
            IoUtils.openInFileExplorer(scriptsDirectory);
        }
    }

    private void requestReload() {
        if (reloadPending) {
            return;
        }
        reloadPending = true;
        reloadButton.setEnabled(false);
        ScriptReloadStatus.begin();
        getGuiContext().deferAfterRender(this::reloadScripts);
    }

    private void reloadScripts() {
        BetaMoonMain main = BetaMoonMain.getInstance();
        if (main != null) {
            main.reloadLuaScripts();
        }
        reloadPending = false;
        reloadButton.setEnabled(true);
        if (LuaScriptErrors.shouldShowPopup()) {
            showScreen(new GuiPopupScriptErrors(this));
        }
    }

    private void showErrorPopup() {
        if (!LuaScriptErrors.getEntries().isEmpty()) {
            showScreen(new GuiPopupScriptErrors(this));
        }
    }

    private static List<ScriptMod> getSortedEntries(List<ScriptMod> entries) {
        if (entries == null || entries.isEmpty()) {
            return entries;
        }
        List<ScriptMod> sorted = new ArrayList<ScriptMod>(entries);
        Comparator<ScriptMod> comparator = Comparator
                .comparing((ScriptMod entry) -> Boolean.valueOf(entry != null && entry.isFailed())).reversed()
                .thenComparing(entry -> safeName(entry == null ? null : entry.getSortName()),
                        String.CASE_INSENSITIVE_ORDER);
        Collections.sort(sorted, comparator);
        return sorted;
    }

    private static String safeName(String name) {
        return name == null ? "" : name;
    }

    private final class ScriptsLayout extends GuiContainer {
        @Override
        protected void arrangeChildren(GuiContext context) {
            int buttonHeight = 20;
            int buttonY = getBottom() - 20 - buttonHeight;
            backButton.arrange(context, Rect.fromPositionAndSize(10, buttonY, 90, buttonHeight));

            int scriptsButtonX = getLeft() + (getWidth() - 140) / 2;
            openScriptsButton.arrange(context,
                    Rect.fromPositionAndSize(scriptsButtonX, buttonY, 140, buttonHeight));

            int debugButtonX = getRight() - 10 - 90;
            debugButton.arrange(context, Rect.fromPositionAndSize(debugButtonX, buttonY, 90, buttonHeight));
            int reloadButtonX = debugButtonX - 6 - 100;
            reloadButton.arrange(context, Rect.fromPositionAndSize(reloadButtonX, buttonY, 100, buttonHeight));

            int indicatorRight = reloadButtonX - 6;
            reloadIndicator.arrange(context, Rect.fromPositionAndSize(indicatorRight - 19,
                    buttonY + (buttonHeight - 19) / 2, 19, 19));

            int separatorY = buttonY - 8;
            bottomSeparator.arrange(context, new Rect(10, separatorY, getRight() - 10, separatorY + 1));

            int listWidth = Math.min(200, Math.max(120, getWidth() / 4));
            listPanel.arrange(context, new Rect(10, 10, 10 + listWidth, buttonY - 10));

            int detailLeft = listPanel.getSeparatorX() + 8;
            infoPanel.setHeaderY(listPanel.getHeaderTextY());
            infoPanel.arrange(context, new Rect(detailLeft, listPanel.getListTop(),
                    getRight() - GuiPanelScriptList.getPanelPadding(), listPanel.getListBottom()));
        }
    }
}
