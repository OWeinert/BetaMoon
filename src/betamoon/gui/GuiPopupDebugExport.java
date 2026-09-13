package betamoon.gui;

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

/** Result dialog shown after a debug export finishes. */
public final class GuiPopupDebugExport extends GuiDialogScreen {
    private static final String TOOLTIP_OPEN = "Open in File Explorer";

    private final String message;
    private final String exportPath;
    private final boolean showPath;

    public GuiPopupDebugExport(GuiScreen parent, Exception error) {
        super(parent, error == null ? "Export Complete" : "Export Failed");

        if (error == null) {
            message = "Files exported to:";
            exportPath = DebugExports.getDebugDirPath();
            showPath = true;
        } else {
            message = String.valueOf(error);
            exportPath = "";
            showPath = false;
        }

        dialog.setPanelSize(360, 140, 0);
        dialog.setBodyInsets(14, 36, 6);
        dialog.setFooterLayout(FooterAlignment.CENTER, 0, 0, 80);
        dialog.setBody(new ExportMessage());
        dialog.addFooterButton(new GuiButton("Close", () -> showScreen(parent)));
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
            int textY = getTop() + context.getRenderer().lineHeight() + 4;
            String displayPath = context.getRenderer().trimToWidth(exportPath, getWidth());
            pathLink.setText(displayPath);
            pathLink.arrange(context, Rect.fromPositionAndSize(getLeft(), textY,
                    context.getRenderer().textWidth(displayPath), context.getRenderer().lineHeight()));
        }

        @Override
        protected void renderBeforeChildren(GuiContext context) {
            if (showPath) {
                context.getRenderer().drawText(message, getLeft(), getTop(),
                        context.getRenderer().getTheme().textMuted);
            } else {
                context.getRenderer().drawWrappedText(message, getLeft(), getTop(), getWidth(),
                        context.getRenderer().getTheme().textMuted);
            }
        }
    }
}
