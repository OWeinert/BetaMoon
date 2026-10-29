package betamoon.gui;

import betamoon.debug.DebugNativeExportTask;
import betamoon.gui.framework.GuiDialogScreen;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiMessage;
import net.minecraft.src.GuiScreen;

/**
 * Responsive native-only export progress; leaving this screen cancels
 * unfinished work.
 */
public final class GuiPopupNativeExportProgress extends GuiDialogScreen {
    private final DebugNativeExportTask task;

    public GuiPopupNativeExportProgress(GuiScreen parent, DebugNativeExportTask task) {
        super(parent, "Exporting Native Catalog");
        this.task = task;
        dialog.setPanelSize(420, 170, 100);
        dialog.setBodyInsets(14, 36, 8);
        dialog.setBody(new GuiMessage(task::getProgress));
        dialog.addFooterButton(new GuiButton("Cancel", () -> {
            task.cancel();
            showScreen(parent);
        }));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (task.isDone()) {
            showScreen(task.isCancelled() ? parent : new GuiPopupDebugExport(parent, task.getResult()));
        }
    }

    @Override
    public void onGuiClosed() {
        task.cancel();
        super.onGuiClosed();
    }
}
