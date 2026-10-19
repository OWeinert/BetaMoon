package betamoon.gui;

import betamoon.gui.framework.GuiDialogScreen;
import betamoon.luamodloader.LuaScriptErrors;
import java.nio.file.Path;
import net.minecraft.src.GuiScreen;
import org.lwjgl.input.Keyboard;

/** Dialog that reports Lua script errors and warnings found during loading. */
public final class GuiPopupScriptErrors extends GuiDialogScreen {
    private final GuiPanelScriptErrorList errorList;
    private final GuiScriptIssueFooter footer;
    private final GuiScriptIssueActions actions;
    private long issueRevision = -1;
    private boolean initialFocus;

    public GuiPopupScriptErrors(GuiScreen parent) {
        this(parent, true);
    }

    public GuiPopupScriptErrors(GuiScreen parent, boolean showWarnings) {
        this(parent, showWarnings, new GuiScriptIssueActions());
    }

    GuiPopupScriptErrors(GuiScreen parent, boolean showWarnings, GuiScriptIssueActions actions) {
        super(parent, "Script Issues");
        this.actions = actions;
        errorList = new GuiPanelScriptErrorList(showWarnings, actions);
        footer = new GuiScriptIssueFooter(actions, errorList::issues, this::exportErrors,
                this::dismissErrors, this::shutdownGame);

        dialog.setPanelSize(360, 320, 100);
        dialog.setScreenMargins(24, 24);
        dialog.setBody(errorList);
        dialog.setBodyInsets(10, 32, 8);
        dialog.setFooter(footer);
    }

    @Override
    protected void updateSceneState(int mouseX, int mouseY, float partialTicks) {
        if (issueRevision != LuaScriptErrors.getRevision()) {
            issueRevision = LuaScriptErrors.getRevision();
            getGuiContext().requestLayout();
        }
        if (!initialFocus) {
            footer.focusDismiss(getGuiContext());
            initialFocus = true;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            dismissErrors();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void dismissErrors() {
        LuaScriptErrors.ignore();
        showScreen(parent);
    }

    private void exportErrors() {
        Path report = actions.export(LuaScriptErrors.getEntries());
        if (report != null) {
            showScreen(new GuiPopupScriptIssueExport(this, report, actions));
        }
    }
}
