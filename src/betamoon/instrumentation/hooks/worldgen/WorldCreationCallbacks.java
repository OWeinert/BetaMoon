package betamoon.instrumentation.hooks.worldgen;

import betamoon.gui.GuiWorldCreation;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiScreen;

public final class WorldCreationCallbacks {
    private WorldCreationCallbacks() {
    }

    public static void completed() {
    }

    public static int open(Minecraft minecraft, GuiScreen parent) {
        minecraft.displayGuiScreen(new GuiWorldCreation(parent));
        return 1;
    }
}
