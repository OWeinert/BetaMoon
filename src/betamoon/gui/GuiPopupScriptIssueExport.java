package betamoon.gui;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiDialogScreen;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiDialog.FooterAlignment;
import betamoon.gui.widget.GuiLink;
import betamoon.gui.widget.GuiMessage;
import betamoon.gui.widget.GuiScrollView;
import java.nio.file.Path;
import net.minecraft.src.GuiScreen;
import org.lwjgl.input.Keyboard;

/** Report-save result that preserves its caller and offers the complete, clickable path on success. */
final class GuiPopupScriptIssueExport extends GuiDialogScreen {
    private static final String INSTRUCTION = "Click the file path to open the report.";
    private final Path report;
    private final GuiScriptIssueActions actions;
    private final ExportContent content = new ExportContent();
    private final GuiScrollView scroll = new GuiScrollView(content, GuiScrollView.Policy.VERTICAL);
    private final GuiButton close;
    private String failure = "";
    private boolean initialFocus;

    GuiPopupScriptIssueExport(GuiScreen parent, Path report, GuiScriptIssueActions actions) {
        super(parent, report == null ? "Report Not Saved" : "Report Saved");
        this.report = report;
        this.actions = actions;

        dialog.setPanelSize(360, 210, 110);
        dialog.setScreenMargins(24, 24);
        dialog.setBodyInsets(12, 32, 12);
        dialog.setFooterLayout(FooterAlignment.CENTER, 12, 6, 90);
        if (report == null) {
            failure = actions.detail();
            dialog.setBody(new GuiMessage(() -> "The report could not be saved.\n\n" + failure));
        } else {
            content.path.setText(report.toString());
            content.path.setWrap(true);
            content.path.setTooltip("Open the saved report");
            dialog.setBody(new ExportBody());
        }
        close = dialog.addFooterButton(new GuiButton("Close", () -> showScreen(parent)));
    }

    @Override
    protected void updateSceneState(int mouseX, int mouseY, float partialTicks) {
        if (!initialFocus) {
            getGuiContext().requestFocus(close);
            initialFocus = true;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            showScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void openReport() {
        failure = actions.openReport(report) ? "" : actions.detail();
        getGuiContext().requestLayout();
    }

    private final class ExportBody extends GuiContainer {
        private ExportBody() {
            add(scroll);
        }

        @Override
        protected Size measureElement(GuiContext context, Constraints constraints) {
            return content.measure(context, constraints);
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            int height = content.measure(context, Constraints.upTo(getWidth(), Integer.MAX_VALUE)).getHeight();
            scroll.setContentSize(getWidth(), height);
            scroll.arrange(context, getBounds());
        }
    }

    private final class ExportContent extends GuiContainer {
        private final GuiLink path = add(new GuiLink(GuiPopupScriptIssueExport.this::openReport) {
            @Override
            protected void renderElement(GuiContext context) {
                super.renderElement(context);
                if (isFocused()) {
                    context.getRenderer().drawHorizontalLine(getLeft() - 1, getRight() + 1, getTop() - 1, 0xFFFFFFA0);
                    context.getRenderer().drawHorizontalLine(getLeft() - 1, getRight() + 1, getBottom(), 0xFFFFFFA0);
                    context.getRenderer().drawVerticalLine(getTop() - 1, getBottom() + 1, getLeft() - 1, 0xFFFFFFA0);
                    context.getRenderer().drawVerticalLine(getTop() - 1, getBottom() + 1, getRight(), 0xFFFFFFA0);
                }
            }

            @Override
            protected void onFocusChanged(GuiContext context, boolean focused) {
                if (focused) {
                    scroll.reveal(getBounds());
                }
            }
        });

        @Override
        protected Size measureElement(GuiContext context, Constraints constraints) {
            int width = Math.max(8, constraints.getMaxWidth() - 8);
            int height = context.getRenderer().wrappedTextHeight(INSTRUCTION, width) + 8
                    + path.measure(context, Constraints.upTo(width, Integer.MAX_VALUE)).getHeight();
            if (!failure.isEmpty()) {
                height += 8 + context.getRenderer().wrappedTextHeight("Could not open report.\n" + failure, width);
            }
            return new Size(constraints.getMaxWidth(), height + 2);
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            int width = Math.max(8, getWidth() - 8);
            int top = getTop() + context.getRenderer().wrappedTextHeight(INSTRUCTION, width) + 8;
            int height = path.measure(context, Constraints.upTo(width, Integer.MAX_VALUE)).getHeight();
            path.arrange(context, Rect.fromPositionAndSize(getLeft() + 2, top, width, height));
        }

        @Override
        protected void renderBeforeChildren(GuiContext context) {
            int width = Math.max(8, getWidth() - 8);
            context.getRenderer().drawWrappedText(INSTRUCTION, getLeft() + 2, getTop(), width,
                    context.getRenderer().getTheme().textPrimary);
            if (!failure.isEmpty()) {
                context.getRenderer().drawWrappedText("Could not open report.\n" + failure, getLeft() + 2,
                        path.getBottom() + 8, width, context.getRenderer().getTheme().textError);
            }
        }
    }
}
