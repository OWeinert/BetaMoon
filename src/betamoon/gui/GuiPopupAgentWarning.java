package betamoon.gui;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.net.URL;

import betamoon.BetaMoonMain;
import betamoon.gui.api.component.GuiActionButton;
import betamoon.gui.api.component.GuiComponentBase;
import betamoon.gui.api.component.GuiTextClickable;
import betamoon.gui.api.screen.GuiScreenPopup;
import betamoon.gui.api.util.GuiColors;
import net.minecraft.src.FontRenderer;
import net.minecraft.src.GuiScreen;

/**
 * One-time warning shown when BetaMoon was started without its JVM agent.
 */
public final class GuiPopupAgentWarning extends GuiScreenPopup {
    private static final String MESSAGE = "The BetaMoon Java agent is not enabled. Some BetaMoon features may be "
        + "unavailable. Add this argument to the instance's Java arguments:";
    private static final String AGENT_ARGUMENT = getAgentArgument();

    private final GuiActionButton continueButton;
    private final WarningMessage message = new WarningMessage();

    public GuiPopupAgentWarning(GuiScreen parent) {
        super(parent);
        setTitleColor(GuiColors.TEXT_WARNING);
        continueButton = new GuiActionButton("Continue", () ->
            GuiPopupAgentWarning.this.showScreen(GuiPopupAgentWarning.this.parent));
    }

    @Override
    protected void initPopupGui() {
        continueButton.setMinecraft(this.mc);
        popupRoot.addChild(message);
        popupRoot.addChild(continueButton);
    }

    @Override
    protected void layoutPopupComponents() {
        int contentLeft = panelLeft + 14;
        int contentTop = panelTop + 36;
        int contentRight = panelLeft + panelWidth - 14;
        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonX = panelLeft + (panelWidth - buttonWidth) / 2;
        int buttonY = panelTop + panelHeight - 30;
        message.setBounds(contentLeft, contentTop, contentRight, buttonY - 8);
        message.setScreenSize(this.width, this.height);
        continueButton.setBounds(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight);
    }

    @Override
    protected String getPopupTitle() {
        return "BetaMoon Agent Warning";
    }

    @Override
    protected int getMaxPanelHeight() {
        return 170;
    }

    @Override
    protected int getMinPanelHeight() {
        return 150;
    }

    private static String getAgentArgument() {
        try {
            URL location = BetaMoonMain.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null && "file".equalsIgnoreCase(location.getProtocol())) {
                File jarFile = new File(location.toURI());
                if (jarFile.isFile() && jarFile.getName().toLowerCase().endsWith(".jar")) {
                    String launcherSafePath = jarFile.getAbsolutePath().replace(File.separatorChar, '/');
                    return "-javaagent:\"" + launcherSafePath + "\"";
                }
            }
        } catch (Exception ignored) {
            // Keep the warning useful if this classloader does not expose its JAR location.
        }
        return "-javaagent:<path-to-betamoon.jar>";
    }

    private final class WarningMessage extends GuiComponentBase {
        private final GuiTextClickable agentArgumentLink;
        private String status = "Click the argument to copy it.";
        private int statusColor = GuiColors.TEXT_MUTED;
        private int screenWidth;
        private int screenHeight;

        WarningMessage() {
            agentArgumentLink = new GuiTextClickable(() -> copyAgentArgument());
            agentArgumentLink.setText(AGENT_ARGUMENT);
            agentArgumentLink.setTooltip("Copy agent argument");
            agentArgumentLink.setWrapText(true);
        }

        void setScreenSize(int screenWidth, int screenHeight) {
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
        }

        private void copyAgentArgument() {
            try {
                StringSelection selection = new StringSelection(AGENT_ARGUMENT);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                status = "Copied to clipboard.";
                statusColor = GuiColors.TEXT_PRIMARY;
            } catch (Exception error) {
                status = "Could not copy to clipboard.";
                statusColor = GuiColors.TEXT_ERROR;
            }
        }

        public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
            if (font == null) {
                return;
            }
            int contentWidth = Math.max(0, right - left);
            font.func_27278_a(MESSAGE, left, top, contentWidth, GuiColors.TEXT_MUTED);

            int messageHeight = font.func_27277_a(MESSAGE, contentWidth);
            int linkTop = top + messageHeight + 4;
            int linkHeight = font.func_27277_a(AGENT_ARGUMENT, contentWidth);
            agentArgumentLink.setBounds(left, linkTop, right, linkTop + linkHeight);
            agentArgumentLink.setScreenSize(screenWidth, screenHeight);
            agentArgumentLink.draw(font, mouseX, mouseY, partialTicks);

            int statusTop = linkTop + linkHeight + 4;
            font.drawStringWithShadow(status, left, statusTop, statusColor);
        }

        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            return agentArgumentLink.mouseClicked(mouseX, mouseY, button);
        }
    }
}
