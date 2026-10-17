package betamoon.gui;

import betamoon.gui.framework.GuiDialogScreen;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiMessage;
import net.minecraft.src.GuiScreen;

/** Reports unreadable world-generation records before joining a saved world. */
public final class GuiWorldGenerationError extends GuiDialogScreen {
    public GuiWorldGenerationError(GuiScreen parent, String message) {
        super(parent, "World generation unavailable");
        dialog.setPanelSize(400, 210, 100);
        dialog.setScreenMargins(24, 24);
        dialog.setBody(new GuiMessage(() -> message
                + "\n\nThe generation records could not be read safely. Restore a consistent world backup."));
        dialog.addFooterButton(new GuiButton("Cancel", () -> showScreen(parent)));
    }
}
