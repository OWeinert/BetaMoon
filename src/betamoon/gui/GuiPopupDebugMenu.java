package betamoon.gui;

import betamoon.debug.DebugExports;
import betamoon.gui.framework.GuiDialogScreen;
import betamoon.gui.framework.GuiLayouts;
import betamoon.gui.framework.GuiLayouts.Axis;
import betamoon.gui.framework.GuiLayouts.Length;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiDialog.FooterAlignment;
import net.minecraft.src.GuiScreen;

/** Dialog that exposes BetaMoon's development data exporters. */
public final class GuiPopupDebugMenu extends GuiDialogScreen {
    public GuiPopupDebugMenu(GuiScreen parent) {
        super(parent, "Debug Menu");

        dialog.setPanelSize(260, 200, 0);
        dialog.setScreenMargins(60, 80);
        dialog.setBodyInsets(20, 30, 8);
        dialog.setFooterLayout(FooterAlignment.FILL, 20, 0, 0);
        dialog.setBody(createExportButtons());
        dialog.addFooterButton(new GuiButton("Close", () -> showScreen(parent)));
    }

    private GuiLayouts.Stack createExportButtons() {
        GuiLayouts.Stack buttons = new GuiLayouts.Stack(Axis.VERTICAL);
        buttons.setGap(6);
        buttons.addItem(exportButton("Export All", () -> DebugExports.exportAll()), Length.fill(), Length.fixed(20));
        buttons.addItem(exportButton("Export Recipe Types", () -> DebugExports.exportRecipeTypes()), Length.fill(),
                Length.fixed(20));
        buttons.addItem(exportButton("Export Recipes", () -> DebugExports.exportRecipes()), Length.fill(),
                Length.fixed(20));
        buttons.addItem(exportButton("Export Blocks", () -> DebugExports.exportBlocks()), Length.fill(),
                Length.fixed(20));
        buttons.addItem(exportButton("Export Items", () -> DebugExports.exportItems()), Length.fill(),
                Length.fixed(20));
        return buttons;
    }

    private GuiButton exportButton(String label, ExportAction action) {
        return new GuiButton(label, () -> {
            Exception error = action.export();
            showScreen(new GuiPopupDebugExport(this, error));
        });
    }

    @FunctionalInterface
    private interface ExportAction {
        Exception export();
    }
}
