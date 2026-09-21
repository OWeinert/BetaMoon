package betamoon.gui;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiDialogScreen;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.widget.GuiButton;
import betamoon.io.IoUtils;
import betamoon.update.UpdateRelease;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import net.minecraft.src.GuiScreen;

/** Compact-menu details and a recoverable browser/clipboard fallback. */
final class GuiUpdateDetails extends GuiDialogScreen {
    private final UpdateRelease release;
    private String status;

    GuiUpdateDetails(GuiScreen parent, String installed, UpdateRelease release, boolean browserFailed) {
        super(parent, "BetaMoon update available");
        this.release = release;
        status = browserFailed
                ? "Could not open browser. Copy the link below."
                : "Open the download page in your browser.";
        dialog.setPanelSize(420, 210, 160);
        dialog.setScreenMargins(16, 20);
        dialog.setBody(new GuiElement() {
            @Override
            protected void renderElement(GuiContext context) {
                String text = "Installed: " + installed + "\nAvailable: " + release.getVersion() + "\n\n" + status
                        + "\n\n" + release.getPageUri();
                context.getRenderer().drawWrappedText(text, getLeft(), getTop(), getWidth(), 0xFFFFFFFF);
            }
        });
        dialog.addFooterButton(new GuiButton("View on " + release.getSourceName(), this::openPage));
        dialog.addFooterButton(new GuiButton("Copy link", this::copyLink));
        dialog.addFooterButton(new GuiButton("Back", () -> showScreen(parent)));
    }

    private void openPage() {
        status = IoUtils.openWebPage(release.getPageUri())
                ? "Opened in your browser."
                : "Could not open browser. Copy the link below.";
    }

    private void copyLink() {
        try {
            StringSelection text = new StringSelection(release.getPageUri().toString());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(text, text);
            status = "Link copied.";
        } catch (Exception unavailable) {
            status = "Clipboard unavailable. Use the URL below.";
        }
    }
}
