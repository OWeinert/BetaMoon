package betamoon.instrumentation.hooks.gui;

import betamoon.gui.inventory.GuiInventoryRuntime;
import net.minecraft.src.GuiContainer;
import net.minecraft.src.GuiInventory;
import net.minecraft.src.InventoryPlayer;
import net.minecraft.src.ItemStack;

/**
 * Native presentation seams; inventory transactions continue through Minecraft.
 */
public final class GuiInventoryCallbacks {
    public static int beforeDraw(GuiContainer screen, int x, int y, float partialTicks) {
        GuiInventoryRuntime.beforeDraw(screen, x, y, partialTicks);
        return 0;
    }

    public static void afterDraw(GuiContainer screen) {
        GuiInventoryRuntime.afterDraw(screen);
    }

    public static void failedDraw(GuiContainer screen) {
        GuiInventoryRuntime.failedDraw(screen);
    }

    public static int closing(GuiContainer screen) {
        GuiInventoryRuntime.closed(screen);
        return 0;
    }

    public static int background(GuiInventory screen) {
        return GuiInventoryRuntime.background(screen) ? 1 : 0;
    }

    public static int foreground(GuiInventory screen) {
        return GuiInventoryRuntime.foreground(screen) ? 1 : 0;
    }

    public static void afterLayer() {
    }

    public static ItemStack cursor(InventoryPlayer inventory) {
        GuiInventoryRuntime.beforeCursor(inventory);
        return inventory.getItemStack();
    }

    public static int beforeHover(GuiContainer screen) {
        return GuiInventoryRuntime.suppressHover(screen) ? 1 : 0;
    }

    public static boolean afterHover(boolean hovered, int decision) {
        return decision == 0 && hovered;
    }

    private GuiInventoryCallbacks() {
    }
}
