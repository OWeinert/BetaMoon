package betamoon.gui;

import betamoon.debug.DebugExportCatalog;
import betamoon.debug.DebugExportDefinition;
import betamoon.debug.DebugExportResult;
import betamoon.debug.DebugExports;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiDialogScreen;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiDialog.FooterAlignment;
import betamoon.gui.widget.GuiLink;
import betamoon.gui.widget.GuiScrollView;
import betamoon.io.IoUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.GuiScreen;

/** Catalog-driven dialog for isolated, descriptive debug exports. */
public final class GuiPopupDebugMenu extends GuiDialogScreen {
    public GuiPopupDebugMenu(GuiScreen parent) {
        super(parent, "Debug Exports");

        dialog.setPanelSize(500, 340, 240);
        dialog.setScreenMargins(24, 36);
        dialog.setBodyInsets(14, 32, 8);
        dialog.setFooterLayout(FooterAlignment.FILL, 14, 8, 0);
        dialog.setBody(new ExportMenuBody());
        dialog.addFooterButton(new GuiButton("Export All", () -> showResult(DebugExports.exportAll())));
        dialog.addFooterButton(new GuiButton("Close", () -> showScreen(parent)));
    }

    private void showResult(DebugExportResult result) {
        showScreen(new GuiPopupDebugExport(this, result));
    }

    private final class ExportMenuBody extends GuiContainer {
        private final GuiLink directory = add(new GuiLink(
                () -> IoUtils.openPath(new File(DebugExports.getDebugDirPath()))));
        private final CategoryList categories = new CategoryList();
        private final GuiScrollView scroll = add(new GuiScrollView(categories, GuiScrollView.Policy.VERTICAL));

        private ExportMenuBody() {
            String path = DebugExports.getDebugDirPath();
            directory.setText(path);
            directory.setVisible(!path.isEmpty());
            directory.setTooltip("Open the export directory");
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            String displayPath = context.getRenderer().trimToWidth(DebugExports.getDebugDirPath(), getWidth());
            directory.setText(displayPath);
            directory.setVisible(!displayPath.isEmpty());
            directory.arrange(context, Rect.fromPositionAndSize(getLeft(), getTop(), getWidth(),
                    context.getRenderer().lineHeight() + 2));
            int listTop = getTop() + context.getRenderer().lineHeight() + 8;
            scroll.setContentSize(getWidth(), categories.contentHeight());
            scroll.arrange(context, new Rect(getLeft(), listTop, getRight(), getBottom()));
        }
    }

    private final class CategoryList extends GuiContainer {
        private static final int ROW_HEIGHT = 58;
        private static final int BUTTON_WIDTH = 72;
        private final List<CategoryRow> rows = new ArrayList<CategoryRow>();

        private CategoryList() {
            for (DebugExportDefinition definition : DebugExportCatalog.definitions()) {
                rows.add(add(new CategoryRow(definition)));
            }
        }

        private int contentHeight() {
            return rows.size() * ROW_HEIGHT;
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            int y = getTop();
            for (CategoryRow row : rows) {
                row.arrange(context, Rect.fromPositionAndSize(getLeft(), y, getWidth() - 5, ROW_HEIGHT));
                y += ROW_HEIGHT;
            }
        }

        private final class CategoryRow extends GuiContainer {
            private final DebugExportDefinition definition;
            private final GuiButton export;

            private CategoryRow(DebugExportDefinition definition) {
                this.definition = definition;
                export = add(new GuiButton("Export", BUTTON_WIDTH,
                        () -> showResult(DebugExports.export(definition))));
            }

            @Override
            protected void arrangeChildren(GuiContext context) {
                int buttonY = getTop() + (getHeight() - 20) / 2;
                export.arrange(context, Rect.fromPositionAndSize(getRight() - BUTTON_WIDTH - 4, buttonY,
                        BUTTON_WIDTH, 20));
            }

            @Override
            protected void renderBeforeChildren(GuiContext context) {
                int textWidth = Math.max(0, getWidth() - BUTTON_WIDTH - 14);
                context.getRenderer().drawText(definition.getTitle(), getLeft() + 2, getTop() + 2,
                        context.getRenderer().getTheme().textPrimary);
                context.getRenderer().drawWrappedText(definition.getDescription(), getLeft() + 2, getTop() + 14,
                        textWidth, context.getRenderer().getTheme().textMuted);
                String files = context.getRenderer().trimToWidth("Files: " + definition.getFileSummary(), textWidth);
                context.getRenderer().drawText(files, getLeft() + 2, getTop() + 42,
                        context.getRenderer().getTheme().textMuted);
                context.getRenderer().drawHorizontalLine(getLeft(), getRight(), getBottom() - 2,
                        context.getRenderer().getTheme().line);
            }
        }
    }
}
