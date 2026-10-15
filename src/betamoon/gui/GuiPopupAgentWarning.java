package betamoon.gui;

import betamoon.BetaMoonClient;
import betamoon.gui.framework.GuiDialogScreen;
import betamoon.gui.framework.GuiTheme;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiDialog.FooterAlignment;
import betamoon.gui.widget.GuiMessage;
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

    private String status = "Copy this argument into the instance Java arguments.";

    public GuiPopupAgentWarning(GuiScreen parent) {
        super(parent, "BetaMoon Agent Warning");

        dialog.setTitleColor(GuiTheme.DEFAULT.textWarning);
        dialog.setPanelSize(360, 190, 100);
        dialog.setBodyInsets(14, 36, 8);
        dialog.setFooterLayout(FooterAlignment.FILL, 12, 6, 0);
        dialog.setBody(new GuiMessage(() -> MESSAGE + "\n\n" + AGENT_ARGUMENT + "\n\n" + status));
        dialog.addFooterButton(new GuiButton("Copy Argument", this::copyAgentArgument));
        dialog.addFooterButton(new GuiButton("Continue", () -> showScreen(parent)));
    }

    private void copyAgentArgument() {
        try {
            StringSelection selection = new StringSelection(AGENT_ARGUMENT);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            status = "Copied to clipboard.";
        } catch (Exception error) {
            status = "Could not copy to clipboard.";
        }
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

}
