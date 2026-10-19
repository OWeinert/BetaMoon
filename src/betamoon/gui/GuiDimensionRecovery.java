package betamoon.gui;

import betamoon.gui.framework.GuiDialogScreen;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiMessage;
import net.minecraft.src.GuiScreen;

/**
 * Recovery choices are supplied only after their save/player preflight has
 * completed.
 */
public final class GuiDimensionRecovery extends GuiDialogScreen {
    public GuiDimensionRecovery(GuiScreen parent, String detail, Runnable retry, Runnable rescue) {
        super(parent, "Dimension unavailable");
        dialog.setPanelSize(440, 240, 110);
        dialog.setScreenMargins(24, 24);
        dialog.setTitleColor(0xffffcc55);
        dialog.setBody(new GuiMessage(() -> detail + "\n\nYour saved player has been kept. "
                + "Restore the required scripts or a consistent world backup, then retry."));
        dialog.addFooterButton(new GuiButton("Back", () -> showScreen(parent)));
        if (retry != null) {
            dialog.addFooterButton(new GuiButton("Retry", retry::run));
        }
        if (rescue != null) {
            dialog.addFooterButton(new GuiButton("Rescue to Overworld...", rescue::run));
        }
    }
}
