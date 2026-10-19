package betamoon.instrumentation.hooks.event;

import betamoon.client.tick.ClientTickEvents;
import betamoon.teleport.TeleportRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.src.ModLoader;
import net.minecraft.src.World;

public final class TickEventCallbacks {
    private TickEventCallbacks() {
    }

    public static int begin(Minecraft minecraft) {
        ClientTickEvents.begin(minecraft);
        return 0;
    }

    public static void completed(Minecraft minecraft) {
        ClientTickEvents.completed(minecraft);
    }

    public static void advanceWorld(World world) {
        if (TeleportRuntime.frozen()) {
            return;
        }
        world.tick();
        ClientTickEvents.worldAdvanced(ModLoader.getMinecraftInstance(), world);
    }
}
