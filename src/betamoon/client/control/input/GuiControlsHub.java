package betamoon.client.control.input;

import net.minecraft.src.GameSettings;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiControls;
import net.minecraft.src.GuiOptions;
import net.minecraft.src.GuiScreen;
import org.lwjgl.input.Keyboard;

/** Keeps Minecraft and ModLoader key settings intact while exposing named BetaMoon actions. */
public final class GuiControlsHub extends GuiScreen {
    private final GuiScreen parent;
    private final GameSettings settings;

    public GuiControlsHub(GuiScreen parent, GameSettings settings) {
        this.parent = parent;
        this.settings = settings;
    }

    public static boolean isProtectedScreen(GuiScreen screen) {
        return screen instanceof GuiControlsHub || screen instanceof GuiBetaMoonActions
                || screen instanceof GuiControls || screen instanceof GuiOptions;
    }

    @Override
    public void initGui() {
        controlList.clear();
        int left = width / 2 - 100;
        int top = height / 2 - 35;
        controlList.add(new GuiButton(1, left, top, "Minecraft and mod hotkeys"));
        controlList.add(new GuiButton(2, left, top + 25, "BetaMoon actions"));
        controlList.add(new GuiButton(3, left, top + 60, "Back"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) {
            mc.displayGuiScreen(new GuiControls(this, settings));
        } else if (button.id == 2) {
            mc.displayGuiScreen(new GuiBetaMoonActions(this));
        } else if (button.id == 3) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "Controls", width / 2, height / 2 - 65, 0xffffff);
        super.drawScreen(mouseX, mouseY, partialTick);
    }
}
