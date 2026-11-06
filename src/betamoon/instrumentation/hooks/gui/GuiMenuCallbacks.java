package betamoon.instrumentation.hooks.gui;

import betamoon.gui.menu.GuiMenuRuntime;
import betamoon.gui.inventory.GuiInventoryRuntime;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiScreen;

public final class GuiMenuCallbacks {
    public static int begin() {
        return 0;
    }

    public static int changing(Minecraft client, GuiScreen next) {
        GuiMenuRuntime.changing(client, next);
        GuiInventoryRuntime.changing(client, next);
        return 0;
    }

    public static void afterChange() {
    }

    public static void initialized(GuiScreen screen, Minecraft client, List<?> buttons) {
        GuiInventoryRuntime.initialized(client, screen);
        if (!GuiMenuRuntime.manual(screen)) {
            GuiMenuRuntime.initialized(client, screen, buttons);
        }
    }

    public static int beforePaint(GuiScreen screen) {
        return !GuiMenuRuntime.manual(screen) && GuiMenuRuntime.suppressNative(screen) ? 1 : 0;
    }

    public static void paint(GuiScreen screen, int x, int y, float partialTicks) {
        if (!GuiMenuRuntime.manual(screen)) {
            GuiMenuRuntime.paint(screen, x, y, partialTicks);
        }
    }

    public static void closed(GuiScreen screen) {
        GuiMenuRuntime.closed(screen);
        GuiInventoryRuntime.closed(screen);
    }

    private GuiMenuCallbacks() {
    }
}
