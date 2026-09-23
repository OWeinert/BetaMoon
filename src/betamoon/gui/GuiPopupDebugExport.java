package betamoon.gui;

import betamoon.debug.DebugExportResult;
import betamoon.debug.DebugExports;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiDialogScreen;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiDialog.FooterAlignment;
import betamoon.gui.widget.GuiLink;
import betamoon.io.IoUtils;
import java.io.File;
import net.minecraft.src.GuiScreen;

/** Result dialog for complete, partial, and failed debug export runs. */
public final class GuiPopupDebugExport extends GuiDialogScreen {
    private static final String TOOLTIP_OPEN = "Open in File Explorer";

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

    /** Compatibility constructor retained for the GUI showcase and older callers. */
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
        dialog.setFooterLayout(FooterAlignment.CENTER, 0, 0, 90);
        dialog.setBody(new ExportMessage());
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

    private final class ExportMessage extends GuiContainer {
        private final GuiLink pathLink = add(new GuiLink(() -> IoUtils.openPath(new File(exportPath))));

        private ExportMessage() {
            pathLink.setTooltip(TOOLTIP_OPEN);
            pathLink.setVisible(showPath);
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            if (!showPath) {
                return;
            }
            int messageHeight = context.getRenderer().wrappedTextHeight(message, getWidth());
            int textY = getTop() + messageHeight + 8;
            String displayPath = context.getRenderer().trimToWidth(exportPath, getWidth());
            pathLink.setText(displayPath);
            pathLink.arrange(context, Rect.fromPositionAndSize(getLeft(), textY,
                    Math.min(getWidth(), context.getRenderer().textWidth(displayPath)),
                    context.getRenderer().lineHeight() + 2));
        }

        @Override
        protected void renderBeforeChildren(GuiContext context) {
            context.getRenderer().drawWrappedText(message, getLeft(), getTop(), getWidth(),
                    context.getRenderer().getTheme().textMuted);
        }
    }
}
