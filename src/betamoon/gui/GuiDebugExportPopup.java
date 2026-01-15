package betamoon.gui;

import betamoon.debug.DebugExports;
import betamoon.gui.api.GuiPopupScreen;
import betamoon.gui.api.GuiText;
import java.io.File;

import org.lwjgl.input.Keyboard;

import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiScreen;

public class GuiDebugExportPopup extends GuiPopupScreen {
    private static final int BUTTON_CLOSE = 0;
    private static final String TOOLTIP_OPEN = "Open in File Explorer";
    private static final int PATH_COLOR = 0x7FC9FF;
    private static final int PATH_HOVER_COLOR = 0xBFE8FF;

    private final String title;
    private final String message;
    private final String exportPath;
    private final boolean showPath;
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
    }

    @Override
    protected void initPopupGui() {
        int buttonWidth = 80;
        int buttonX = panelLeft + panelWidth / 2 - buttonWidth / 2;
        int buttonY = panelTop + panelHeight - 30;
        this.controlList.add(new GuiButton(BUTTON_CLOSE, buttonX, buttonY, buttonWidth, 20, "Close"));
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
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE && this.parent != null) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        if (button.id == BUTTON_CLOSE) {
            this.mc.displayGuiScreen(this.parent);
        }
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
            this.fontRenderer.drawStringWithShadow(message, contentLeft, contentTop, 0xE0E0E0);
            int textY = contentTop + GuiText.getLineHeight(this.fontRenderer) + 4;
            String displayPath = GuiText.trimToWidth(this.fontRenderer, exportPath, contentWidth);
            pathX = contentLeft;
            pathY = textY;
            pathWidth = this.fontRenderer.getStringWidth(displayPath);
            pathHeight = GuiText.getLineHeight(this.fontRenderer);
            boolean hovered = isPathHovered(mouseX, mouseY);
            int pathColor = hovered ? PATH_HOVER_COLOR : PATH_COLOR;
            this.fontRenderer.drawStringWithShadow(displayPath, pathX, pathY, pathColor);
            if (hovered) {
                this.drawRect(pathX, pathY + pathHeight + 1, pathX + pathWidth, pathY + pathHeight + 2, 0xFFBFE8FF);
                GuiText.drawTooltip(this.fontRenderer, this.width, this.height, TOOLTIP_OPEN, mouseX, mouseY);
            }
        } else {
            this.fontRenderer.func_27278_a(message, contentLeft, contentTop, contentWidth, 0xE0E0E0);
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
