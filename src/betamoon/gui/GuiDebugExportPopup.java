package betamoon.gui;

import betamoon.debug.DebugExports;
import betamoon.gui.api.GuiActionButton;
import betamoon.gui.api.GuiColors;
import betamoon.gui.api.GuiPopupScreen;
import betamoon.gui.api.GuiText;
import betamoon.gui.api.GuiUtils;
import java.io.File;

import net.minecraft.src.GuiScreen;

public class GuiDebugExportPopup extends GuiPopupScreen {
    private static final String TOOLTIP_OPEN = "Open in File Explorer";

    private final String title;
    private final String message;
    private final String exportPath;
    private final boolean showPath;
    private final GuiActionButton closeButton;
    private int pathX;
    private int pathY;
    private int pathWidth;
    private int pathHeight;

    public GuiDebugExportPopup(GuiScreen parent, Exception error) {
        super(parent);
        if (error == null) {
            this.title = "Export Complete";
            this.message = "Files exported to:";
            this.exportPath = DebugExports.getDebugDirPath();
            this.showPath = true;
        } else {
            this.title = "Export Failed";
            this.message = String.valueOf(error);
            this.exportPath = "";
            this.showPath = false;
        }
        closeButton = new GuiActionButton("Close", new GuiActionButton.Action() {
            public void onPress() {
                GuiDebugExportPopup.this.mc.displayGuiScreen(GuiDebugExportPopup.this.parent);
            }
        });
    }

    @Override
    protected void initPopupGui() {
        closeButton.setMinecraft(this.mc);
        popupRoot.addChild(closeButton);
    }

    @Override
    protected void layoutPopupComponents() {
        int buttonWidth = 80;
        int buttonX = panelLeft + panelWidth / 2 - buttonWidth / 2;
        int buttonY = panelTop + panelHeight - 30;
        closeButton.setBounds(buttonX, buttonY, buttonX + buttonWidth, buttonY + 20);
    }

    @Override
    protected String getPopupTitle() {
        return title;
    }

    @Override
    protected int getMaxPanelHeight() {
        return 140;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && isPathHovered(mouseX, mouseY)) {
            openExportPath();
        }
    }

    @Override
    protected void drawPopupContents(int mouseX, int mouseY, float partialTicks) {
        int contentLeft = panelLeft + 14;
        int contentTop = panelTop + 36;
        int contentWidth = panelWidth - 28;
        if (showPath) {
            this.fontRenderer.drawStringWithShadow(message, contentLeft, contentTop, GuiColors.TEXT_MUTED);
            int textY = contentTop + GuiText.getLineHeight(this.fontRenderer) + 4;
            String displayPath = GuiText.trimToWidth(this.fontRenderer, exportPath, contentWidth);
            pathX = contentLeft;
            pathY = textY;
            pathWidth = this.fontRenderer.getStringWidth(displayPath);
            pathHeight = GuiText.getLineHeight(this.fontRenderer);
            boolean hovered = isPathHovered(mouseX, mouseY);
            int pathColor = hovered ? GuiColors.LINK_PATH_HOVER : GuiColors.LINK_PATH;
            this.fontRenderer.drawStringWithShadow(displayPath, pathX, pathY, pathColor);
            if (hovered) {
                this.drawRect(pathX, pathY + pathHeight + 1, pathX + pathWidth, pathY + pathHeight + 2, GuiColors.LINK_PATH_HOVER_UNDERLINE);
                GuiText.drawTooltip(this.fontRenderer, this.width, this.height, TOOLTIP_OPEN, mouseX, mouseY);
            }
        } else {
            this.fontRenderer.func_27278_a(message, contentLeft, contentTop, contentWidth, GuiColors.TEXT_MUTED);
        }
    }

    private boolean isPathHovered(int mouseX, int mouseY) {
        if (!showPath || pathWidth <= 0 || pathHeight <= 0) {
            return false;
        }
        return mouseX >= pathX && mouseX <= pathX + pathWidth
            && mouseY >= pathY && mouseY <= pathY + pathHeight;
    }

    private void openExportPath() {
        if (exportPath == null || exportPath.isEmpty()) {
            return;
        }
        File dir = new File(exportPath);
        GuiUtils.openInFileExplorer(dir);
    }
}
