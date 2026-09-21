package betamoon.gui;

import betamoon.BetaMoonClient;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiDialogScreen;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiTheme;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiDialog.FooterAlignment;
import betamoon.gui.widget.GuiLink;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.net.URL;
import net.minecraft.src.GuiScreen;

/** One-time warning shown when BetaMoon was started without its JVM agent. */
public final class GuiPopupAgentWarning extends GuiDialogScreen {
    private static final String MESSAGE = "The BetaMoon Java agent is not enabled. Some BetaMoon features may be "
            + "unavailable. Add this argument to the instance's Java arguments:";
    private static final String AGENT_ARGUMENT = getAgentArgument();

    public GuiPopupAgentWarning(GuiScreen parent) {
        super(parent, "BetaMoon Agent Warning");

        dialog.setTitleColor(GuiTheme.DEFAULT.textWarning);
        dialog.setPanelSize(360, 170, 150);
        dialog.setBodyInsets(14, 36, 8);
        dialog.setFooterLayout(FooterAlignment.CENTER, 0, 0, 90);
        dialog.setBody(new WarningMessage());
        dialog.addFooterButton(new GuiButton("Continue", () -> showScreen(parent)));
    }

    private static String getAgentArgument() {
        try {
            URL location = BetaMoonClient.class.getProtectionDomain().getCodeSource().getLocation();
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

    private static final class WarningMessage extends GuiContainer {
        private final GuiLink argumentLink = add(new GuiLink(this::copyAgentArgument));
        private String status = "Click the argument to copy it.";
        private int statusColor = GuiTheme.DEFAULT.textMuted;
        private int statusTop;

        private WarningMessage() {
            argumentLink.setText(AGENT_ARGUMENT);
            argumentLink.setTooltip("Copy agent argument");
            argumentLink.setWrap(true);
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            int messageHeight = context.getRenderer().wrappedTextHeight(MESSAGE, getWidth());
            int linkTop = getTop() + messageHeight + 4;
            int linkHeight = context.getRenderer().wrappedTextHeight(AGENT_ARGUMENT, getWidth());
            argumentLink.arrange(context, new Rect(getLeft(), linkTop, getRight(), linkTop + linkHeight));
            statusTop = linkTop + linkHeight + 4;
        }

        @Override
        protected void renderBeforeChildren(GuiContext context) {
            context.getRenderer().drawWrappedText(MESSAGE, getLeft(), getTop(), getWidth(),
                    context.getRenderer().getTheme().textMuted);
            context.getRenderer().drawText(status, getLeft(), statusTop, statusColor);
        }

        private void copyAgentArgument() {
            try {
                StringSelection selection = new StringSelection(AGENT_ARGUMENT);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                status = "Copied to clipboard.";
                statusColor = GuiTheme.DEFAULT.textPrimary;
            } catch (Exception error) {
                status = "Could not copy to clipboard.";
                statusColor = GuiTheme.DEFAULT.textError;
            }
        }
    }
}
