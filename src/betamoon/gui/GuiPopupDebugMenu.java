package betamoon.gui;

import betamoon.debug.DebugExportCatalog;
import betamoon.debug.DebugExportDefinition;
import betamoon.debug.DebugExportResult;
import betamoon.debug.DebugExports;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiDialogScreen;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiCheckboxControl;
import betamoon.gui.widget.GuiDialog.FooterAlignment;
import betamoon.gui.widget.GuiLink;
import betamoon.gui.widget.GuiScrollView;
import betamoon.io.IoUtils;
import betamoon.luamodloader.LuaScriptErrors;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.GuiScreen;

/** Pinned script-report action above a catalog-driven list of individual debug exports. */
public final class GuiPopupDebugMenu extends GuiDialogScreen {
    private final GuiScriptIssueActions reportActions;
    private boolean includeNativeCatalogs;

    public GuiPopupDebugMenu(GuiScreen parent) {
        this(parent, new GuiScriptIssueActions());
    }

    GuiPopupDebugMenu(GuiScreen parent, GuiScriptIssueActions reportActions) {
        super(parent, "Debug Tools");
        this.reportActions = reportActions;

        dialog.setPanelSize(500, 340, 240);
        dialog.setScreenMargins(24, 12);
        dialog.setBodyInsets(14, 32, 8);
        dialog.setFooterLayout(FooterAlignment.FILL, 12, 6, 0);
        dialog.setBody(new ExportMenuBody());
        dialog.addFooterButton(new GuiButton("Export All", () -> showResult(DebugExports.exportAll(includeNativeCatalogs))));
        dialog.addFooterButton(new GuiButton("Close", () -> showScreen(parent)));
    }

    private void showResult(DebugExportResult result) {
        showScreen(new GuiPopupDebugExport(this, result));
    }

    private void saveReport() {
        Path report = reportActions.export(LuaScriptErrors.getEntries());
        showScreen(new GuiPopupScriptIssueExport(this, report, reportActions));
    }

    private void openExportFolder() {
        String path = DebugExports.getDebugDirPath();
        if (!path.isEmpty()) {
            IoUtils.openPath(new File(path));
        }
    }

    private final class ExportMenuBody extends GuiContainer {
        private final ReportPanel report = add(new ReportPanel());
        private final ExportHeading heading = add(new ExportHeading());
        private final CategoryList categories = new CategoryList();
        private final GuiScrollView scroll = add(new GuiScrollView(categories, GuiScrollView.Policy.VERTICAL));
        private final GuiCheckboxControl nativeCatalogs = add(new GuiCheckboxControl("Include detailed native catalogs",
                false, selected -> includeNativeCatalogs = selected));

        private ExportMenuBody() {
            nativeCatalogs.setTooltip(
                    "Applies to Export All. Native access > Export always includes detailed catalogs.");
        }

        @Override
        protected Size measureElement(GuiContext context, Constraints constraints) {
            int width = constraints.getMaxWidth();
            Constraints childConstraints = Constraints.upTo(width, Integer.MAX_VALUE);
            int height = report.measure(context, childConstraints).getHeight() + 8
                    + heading.measure(context, childConstraints).getHeight() + 4
                    + categories.contentHeight(context, width)
                    + nativeCatalogs.measure(context, childConstraints).getHeight() + 4;
            return new Size(width, height);
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            Constraints constraints = Constraints.upTo(getWidth(), Integer.MAX_VALUE);
            int reportHeight = report.measure(context, constraints).getHeight();
            report.arrange(context, Rect.fromPositionAndSize(getLeft(), getTop(), getWidth(), reportHeight));
            int headingHeight = heading.measure(context, constraints).getHeight();
            heading.arrange(context,
                    Rect.fromPositionAndSize(getLeft(), report.getBottom() + 8, getWidth(), headingHeight));
            int listTop = Math.min(getBottom(), heading.getBottom() + 4);
            scroll.setContentSize(getWidth(), categories.contentHeight(context, getWidth()));
            int checkboxHeight = nativeCatalogs.measure(context, constraints).getHeight();
            int checkboxTop = Math.max(listTop, getBottom() - checkboxHeight);
            nativeCatalogs.arrange(context,
                    Rect.fromPositionAndSize(getLeft(), checkboxTop, getWidth(), checkboxHeight));
            scroll.arrange(context, new Rect(getLeft(), listTop, getRight(), Math.max(listTop, checkboxTop - 4)));
        }
    }

    private final class ReportPanel extends GuiContainer {
        private static final int PADDING = 8;
        private static final int BUTTON_WIDTH = 90;
        private final DebugExportDefinition definition = DebugExportCatalog.find(DebugExportCatalog.SCRIPT_REPORT_ID);
        private final GuiButton save = add(
                new GuiButton("Save report", BUTTON_WIDTH, GuiPopupDebugMenu.this::saveReport));

        private int textWidth(int width) {
            return Math.max(8, width - PADDING * 2 - BUTTON_WIDTH - 10);
        }

        @Override
        protected Size measureElement(GuiContext context, Constraints constraints) {
            int width = textWidth(constraints.getMaxWidth());
            int height = context.getRenderer().wrappedTextHeight(definition.getTitle(), width) + 4
                    + context.getRenderer().wrappedTextHeight(definition.getDescription(), width) + PADDING * 2;
            return new Size(constraints.getMaxWidth(), Math.max(46, height));
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            save.arrange(context, Rect.fromPositionAndSize(getRight() - PADDING - BUTTON_WIDTH,
                    getTop() + (getHeight() - 20) / 2, BUTTON_WIDTH, 20));
        }

        @Override
        protected void renderBeforeChildren(GuiContext context) {
            context.getRenderer().drawPopupPanel(getBounds());
            context.getRenderer().drawRect(getLeft() + 3, getTop() + 3, getRight() - 3, getBottom() - 3, 0xFF252525);
            int width = textWidth(getWidth());
            int textLeft = getLeft() + PADDING;
            int textTop = getTop() + PADDING;
            context.getRenderer().drawWrappedText(definition.getTitle(), textLeft, textTop, width,
                    context.getRenderer().getTheme().textPrimary);
            int descriptionTop = textTop + context.getRenderer().wrappedTextHeight(definition.getTitle(), width) + 4;
            context.getRenderer().drawWrappedText(definition.getDescription(), textLeft, descriptionTop, width,
                    context.getRenderer().getTheme().textMuted);
        }
    }

    private final class ExportHeading extends GuiContainer {
        private static final String TITLE = "Individual exports";
        private static final String LINK = "Open export folder";
        private final GuiLink directory = add(new GuiLink(GuiPopupDebugMenu.this::openExportFolder));

        private ExportHeading() {
            directory.setText(LINK);
            directory.setVisible(!DebugExports.getDebugDirPath().isEmpty());
            directory.setTooltipSupplier(DebugExports::getDebugDirPath);
        }

        private boolean stacked(GuiContext context, int width) {
            return directory.isVisible()
                    && context.getRenderer().textWidth(TITLE) + 12 + context.getRenderer().textWidth(LINK) > width;
        }

        @Override
        protected Size measureElement(GuiContext context, Constraints constraints) {
            int lineHeight = context.getRenderer().lineHeight();
            return new Size(constraints.getMaxWidth(),
                    stacked(context, constraints.getMaxWidth()) ? lineHeight * 2 + 6 : lineHeight + 2);
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            int width = Math.min(getWidth(), context.getRenderer().textWidth(LINK));
            int top = getTop() + (stacked(context, getWidth()) ? context.getRenderer().lineHeight() + 4 : 0);
            directory.arrange(context,
                    Rect.fromPositionAndSize(getRight() - width, top, width, context.getRenderer().lineHeight() + 2));
        }

        @Override
        protected void renderBeforeChildren(GuiContext context) {
            context.getRenderer().drawText(TITLE, getLeft(), getTop(), context.getRenderer().getTheme().textPrimary);
        }
    }

    private final class CategoryList extends GuiContainer {
        private static final int BUTTON_WIDTH = 90;
        private final List<CategoryRow> rows = new ArrayList<CategoryRow>();

        private CategoryList() {
            for (DebugExportDefinition definition : DebugExportCatalog.definitions()) {
                if (!DebugExportCatalog.SCRIPT_REPORT_ID.equals(definition.getId())) {
                    rows.add(add(new CategoryRow(definition)));
                }
            }
        }

        private int contentHeight(GuiContext context, int width) {
            int height = 0;
            for (CategoryRow row : rows) {
                height += row.height(context, Math.max(0, width - 5));
            }
            return height;
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            int y = getTop();
            for (CategoryRow row : rows) {
                int width = Math.max(0, getWidth() - 5);
                int height = row.height(context, width);
                row.arrange(context, Rect.fromPositionAndSize(getLeft(), y, width, height));
                y += height;
            }
        }

        private final class CategoryRow extends GuiContainer {
            private final DebugExportDefinition definition;
            private final GuiButton export;

            private CategoryRow(DebugExportDefinition definition) {
                this.definition = definition;
                export = add(new GuiButton("Export", BUTTON_WIDTH, () -> showResult(DebugExports.export(definition))));
            }

            private int textWidth(int width) {
                return Math.max(8, width - BUTTON_WIDTH - 14);
            }

            private int height(GuiContext context, int width) {
                int textWidth = textWidth(width);
                int titleHeight = context.getRenderer().wrappedTextHeight(definition.getTitle(), textWidth);
                int descriptionHeight = context.getRenderer().wrappedTextHeight(definition.getDescription(), textWidth);
                return Math.max(58, 2 + titleHeight + 4 + descriptionHeight + 4
                        + context.getRenderer().lineHeight() + 6);
            }

            @Override
            protected void arrangeChildren(GuiContext context) {
                int buttonY = getTop() + (getHeight() - 20) / 2;
                export.arrange(context,
                        Rect.fromPositionAndSize(getRight() - BUTTON_WIDTH - 4, buttonY, BUTTON_WIDTH, 20));
            }

            @Override
            protected void renderBeforeChildren(GuiContext context) {
                int textWidth = textWidth(getWidth());
                int descriptionY = getTop() + 2
                        + context.getRenderer().wrappedTextHeight(definition.getTitle(), textWidth) + 4;
                context.getRenderer().drawWrappedText(definition.getTitle(), getLeft() + 2, getTop() + 2, textWidth,
                        context.getRenderer().getTheme().textPrimary);
                context.getRenderer().drawWrappedText(definition.getDescription(), getLeft() + 2, descriptionY,
                        textWidth, context.getRenderer().getTheme().textMuted);
                int filesY = descriptionY
                        + context.getRenderer().wrappedTextHeight(definition.getDescription(), textWidth) + 4;
                String files = context.getRenderer().trimToWidth("Files: " + definition.getFileSummary(), textWidth);
                context.getRenderer().drawText(files, getLeft() + 2, filesY,
                        context.getRenderer().getTheme().textMuted);
                context.getRenderer().drawHorizontalLine(getLeft(), getRight(), getBottom() - 2,
                        context.getRenderer().getTheme().line);
            }
        }
    }
}
