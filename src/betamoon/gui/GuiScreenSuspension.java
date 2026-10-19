package betamoon.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.ScaledResolution;
import net.minecraft.src.World;

/**
 * Temporarily replaces input/rendering without running the suspended screen's
 * native close callback.
 */
public final class GuiScreenSuspension {
    private final Minecraft minecraft;
    private final EntityPlayer player;
    private final World sourceWorld;
    private final GuiScreen overlay;
    private GuiScreen suspended;
    private boolean finished;

    public GuiScreenSuspension(Minecraft minecraft, GuiScreen overlay) {
        this.minecraft = minecraft;
        this.overlay = overlay;
        suspended = minecraft.currentScreen;
        player = minecraft.thePlayer;
        sourceWorld = minecraft.theWorld;
        minecraft.setIngameNotInFocus();
        minecraft.currentScreen = overlay;
        ScaledResolution resolution = new ScaledResolution(minecraft.gameSettings, minecraft.displayWidth,
                minecraft.displayHeight);
        overlay.setWorldAndResolution(minecraft, resolution.getScaledWidth(), resolution.getScaledHeight());
        minecraft.skipRenderWorld = false;
    }

    public void closeSuspended() {
        if (finished) {
            return;
        }
        if (suspended != null) {
            GuiScreen previous = suspended;
            suspended = null;
            minecraft.currentScreen = previous;
            minecraft.displayGuiScreen(overlay);
        } else if (minecraft.currentScreen != overlay) {
            minecraft.displayGuiScreen(overlay);
        }
    }

    public void finish(boolean success) {
        if (finished) {
            return;
        }
        finished = true;
        if (!success && suspended != null && minecraft.thePlayer == player && minecraft.theWorld == sourceWorld
                && player.worldObj == minecraft.theWorld && minecraft.currentScreen == overlay) {
            overlay.onGuiClosed();
            minecraft.currentScreen = suspended;
            suspended = null;
            if (minecraft.currentScreen == null) {
                minecraft.setIngameFocus();
            }
            return;
        }
        if (suspended != null && minecraft.thePlayer != player) {
            player.craftingInventory.onCraftGuiClosed(player);
            player.craftingInventory = player.inventorySlots;
        }
        suspended = null;
        if (minecraft.currentScreen == overlay) {
            minecraft.displayGuiScreen(null);
        }
    }

    /**
     * Recovery resumes the durable snapshot; native close callbacks must not mutate
     * its discarded cursor/container.
     */
    public void abandonForRecovery() {
        if (finished) {
            return;
        }
        finished = true;
        suspended = null;
        if (minecraft.currentScreen == overlay) {
            overlay.onGuiClosed();
            minecraft.currentScreen = null;
        }
    }
}
