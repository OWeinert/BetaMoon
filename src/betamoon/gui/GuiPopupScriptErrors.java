package betamoon.gui;

import betamoon.gui.framework.GuiDialogScreen;
import betamoon.gui.widget.GuiButton;
import betamoon.gui.widget.GuiDialog.FooterAlignment;
import betamoon.luamodloader.LuaScriptErrors;
import net.minecraft.src.GuiScreen;

/** Dialog that reports Lua script errors and warnings found during loading. */
public final class GuiPopupScriptErrors extends GuiDialogScreen {
    private final GuiPanelScriptErrorList errorList;

    public GuiPopupScriptErrors(GuiScreen parent) {
        this(parent, true);
    }

    public GuiPopupScriptErrors(GuiScreen parent, boolean showWarnings) {
        super(parent, showWarnings ? "Scripts Loaded with Errors/Warnings" : "Scripts Loaded with Errors");
        errorList = new GuiPanelScriptErrorList(showWarnings);

        dialog.setPanelSize(360, 260, 140);
        dialog.setBody(errorList);
        dialog.setBodyInsets(10, 32, 8);
        dialog.setFooterLayout(FooterAlignment.FILL, 10, 10, 0);
        dialog.addFooterButton(new GuiButton("Ignore", () -> ignoreErrors()));
        dialog.addFooterButton(new GuiButton("Close Game", () -> shutdownGame()));
    }

    private void ignoreErrors() {
        LuaScriptErrors.ignore();
        showScreen(parent);
    }
}
