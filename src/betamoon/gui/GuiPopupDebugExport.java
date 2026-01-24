package betamoon.gui;

import betamoon.debug.DebugExports;
import betamoon.gui.api.component.GuiActionButton;
import betamoon.gui.api.component.GuiTextFileLink;
import betamoon.gui.api.util.GuiColors;
import betamoon.gui.api.screen.GuiScreenPopup;
import betamoon.gui.api.util.GuiText;
import java.io.File;
import net.minecraft.src.FontRenderer;

import net.minecraft.src.GuiScreen;

public class GuiPopupDebugExport extends GuiScreenPopup {
    private static final String TOOLTIP_OPEN = "Open in File Explorer";

    private final String title;
    private final String message;
    private final String exportPath;
    private final boolean showPath;
    private final GuiActionButton closeButton;
    private final PopupMessage messagePanel = new PopupMessage();

    public GuiPopupDebugExport(GuiScreen parent, Exception error) {
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
        closeButton = new GuiActionButton("Close", () -> GuiPopupDebugExport.this.showScreen(GuiPopupDebugExport.this.parent));
    }

    @Override
    protected void initPopupGui() {
        closeButton.setMinecraft(this.mc);
        popupRoot.addChild(closeButton);
        popupRoot.addChild(messagePanel);
        messagePanel.setTooltip(TOOLTIP_OPEN);
    }

    @Override
    protected void layoutPopupComponents() {
        int buttonWidth = 80;
        int buttonX = panelLeft + panelWidth / 2 - buttonWidth / 2;
        int buttonY = panelTop + panelHeight - 30;
        closeButton.setBounds(buttonX, buttonY, buttonX + buttonWidth, buttonY + 20);
        int contentLeft = panelLeft + 14;
        int contentTop = panelTop + 36;
        int contentRight = panelLeft + panelWidth - 14;
        int contentBottom = buttonY - 6;
        messagePanel.setBounds(contentLeft, contentTop, contentRight, contentBottom);
        messagePanel.setScreenSize(this.width, this.height);
    }

    @Override
    protected String getPopupTitle() {
        return title;
    }

    @Override
    protected int getMaxPanelHeight() {
        return 140;
    }

    private final class PopupMessage extends betamoon.gui.api.component.GuiComponentBase {
        private final GuiTextFileLink pathLink = new GuiTextFileLink();
        private int screenWidth;
        private int screenHeight;
        private String tooltip;

        PopupMessage() {
        }

        void setTooltip(String tooltip) {
            this.tooltip = tooltip;
        }

        void setScreenSize(int screenWidth, int screenHeight) {
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
        }

        public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
            if (font == null) {
                return;
            }
            int contentWidth = right - left;
            if (showPath) {
                font.drawStringWithShadow(message, left, top, GuiColors.TEXT_MUTED);
                int textY = top + GuiText.getLineHeight(font) + 4;
                String displayPath = GuiText.trimToWidth(font, exportPath, contentWidth);
                int pathWidth = font.getStringWidth(displayPath);
                int pathHeight = GuiText.getLineHeight(font);
                pathLink.setText(displayPath);
                pathLink.setPath(new File(exportPath));
                pathLink.setBounds(left, textY, left + pathWidth, textY + pathHeight);
                pathLink.setScreenSize(screenWidth, screenHeight);
                pathLink.setTooltip(tooltip);
                pathLink.draw(font, mouseX, mouseY, partialTicks);
            } else {
                font.func_27278_a(message, left, top, contentWidth, GuiColors.TEXT_MUTED);
            }
        }

        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            return pathLink.mouseClicked(mouseX, mouseY, button);
        }
    }
}
