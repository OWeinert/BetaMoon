package betamoon.gui;

import betamoon.debug.DebugExportResult;
import betamoon.debug.DebugExports;
import betamoon.gui.framework.GuiDialogScreen;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiDialog.FooterAlignment;
import betamoon.gui.widget.GuiMessage;
import betamoon.io.IoUtils;
import java.io.File;
import net.minecraft.src.GuiScreen;

/** Result dialog for complete, partial, and failed debug export runs. */
public final class GuiPopupDebugExport extends GuiDialogScreen {
    private final String message;
    private final String exportPath;
    private final boolean showPath;

    public GuiPopupDebugExport(GuiScreen parent, DebugExportResult result) {
        super(parent, title(result));
        message = summary(result);
        File directory = result == null ? null : result.getDirectory();
        exportPath = directory == null ? "" : directory.getAbsolutePath();
        showPath = directory != null;
        configure(parent);
    }

    /**
     * Compatibility constructor retained for the GUI showcase and older callers.
     */
    public GuiPopupDebugExport(GuiScreen parent, Exception error) {
        super(parent, error == null ? "Export Complete" : "Export Failed");
        message = error == null ? "Files exported to the debug directory." : String.valueOf(error);
        exportPath = error == null ? DebugExports.getDebugDirPath() : "";
        showPath = error == null;
        configure(parent);
    }

    private void configure(GuiScreen parent) {
        dialog.setPanelSize(420, 180, 150);
        dialog.setBodyInsets(14, 36, 8);
        dialog.setFooterLayout(FooterAlignment.CENTER, 12, 6, 90);
        dialog.setBody(new GuiMessage(() -> message + (showPath ? "\n\n" + exportPath : "")));
        if (showPath) {
            dialog.addFooterButton(new GuiButton("Open Folder", () -> IoUtils.openPath(new File(exportPath))));
        }
        dialog.addFooterButton(new GuiButton("Close", () -> showScreen(parent)));
    }

    private static String title(DebugExportResult result) {
        if (result == null || result.getStatus() == DebugExportResult.Status.FAILED) {
            return "Export Failed";
        }
        return result.getStatus() == DebugExportResult.Status.COMPLETE ? "Export Complete" : "Export Incomplete";
    }

    private static String summary(DebugExportResult result) {
        if (result == null) {
            return "No export result was produced.";
        }
        if (result.getStatus() == DebugExportResult.Status.FAILED) {
            return result.getFailure() == null ? "The export could not be started." : result.getFailure();
        }
        String counts = result.getFileCount() + " files, " + result.getRecordCount() + " records in "
                + result.getElapsedMillis() + " ms.";
        if (result.getStatus() == DebugExportResult.Status.COMPLETE) {
            return counts + (result.getWarningCount() == 0 ? "" : " Warnings: " + result.getWarningCount() + ".");
        }
        String firstFailure = null;
        for (DebugExportResult.CategoryResult category : result.getCategories()) {
            if (!category.isComplete()) {
                firstFailure = category.getTitle() + ": " + category.getFailure();
                break;
            }
        }
        return counts + " Failed categories: " + result.getFailureCount() + ". "
                + (firstFailure == null ? "" : firstFailure + ". ") + "See manifest.txt for details.";
    }

}
