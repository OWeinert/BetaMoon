package betamoon.gui;

import betamoon.gui.framework.GuiDialogScreen;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiMessage;
import net.minecraft.src.GuiScreen;

/**
 * Rescue requires a deliberate choice and completes a full save backup before
 * changing player records.
 */
public final class GuiDimensionRescueConfirmation extends GuiDialogScreen {
    public GuiDimensionRescueConfirmation(GuiScreen parent, Runnable rescue) {
        super(parent, "Rescue to the Overworld?");
        dialog.setPanelSize(430, 230, 110);
        dialog.setScreenMargins(24, 24);
        dialog.setBody(new GuiMessage(() -> "A complete backup of this save will be created under betamoon/backups. "
                + "After the backup finishes, your saved player will be moved to a safe Overworld bed or spawn.\n\n"
                + "Your inventory and the unavailable dimension's terrain are kept. This does not restore missing scripts."));
        dialog.addFooterButton(new GuiButton("Cancel", () -> showScreen(parent)));
        dialog.addFooterButton(new GuiButton("Back Up and Rescue", rescue::run));
    }
}
