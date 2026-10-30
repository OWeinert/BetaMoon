package betamoon.client.tick;

import betamoon.BetaMoonClient;
import betamoon.client.atmosphere.AtmosphereRuntime;
import betamoon.entity.modifier.EntityModifierRuntime;
import betamoon.event.Events;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.event.context.GameEventCtx;
import betamoon.event.context.GuiEventCtx;
import betamoon.event.context.WorldEventCtx;
import betamoon.instrumentation.agent.BetaMoonAgent;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.multiblock.MultiblockRuntime;
import betamoon.runtime.RuntimeEnvironment;
import betamoon.system.WorldServiceRuntime;
import betamoon.teleport.TeleportRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;

/**
 * Logical client and world boundaries, independent of rendering and mutable
 * world time.
 */
public final class ClientTickEvents {
    private ClientTickEvents() {
    }

    public static void requireAvailable() {
        if (RuntimeEnvironment.isClient() && !BetaMoonAgent.isRegistered()) {
            throw new LuaError("Logical tick events require the BetaMoon Java agent; add -javaagent:<betamoon.jar>.");
        }
    }

    public static void begin(Minecraft minecraft) {
        synchronizeLifecycle(minecraft);
    }

    public static void completed(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        synchronizeLifecycle(minecraft);
        World world = minecraft.theWorld;
        AtmosphereRuntime.tick(minecraft);
        NativeDiscoveryRuntime.poll();
        GuiClientUiRuntime.tick(minecraft);
        GuiScreen screen = minecraft.currentScreen;
        Events.GAME_TICK.publish(new GameEventCtx(minecraft, world));
        if (screen != null) {
            Events.GUI_TICK.publish(new GuiEventCtx(minecraft, screen));
        }
        TeleportRuntime.tick(minecraft);
    }

    /**
     * Called only after Minecraft has actually invoked the current world's tick
     * method.
     */
    public static void worldAdvanced(Minecraft minecraft, World world) {
        if (minecraft == null || world == null || minecraft.theWorld != world) {
            return;
        }
        synchronizeLifecycle(minecraft);
        MultiblockRuntime.tick(world);
        WorldServiceRuntime.tick(world);
        if (minecraft.theWorld == world) {
            GuiClientUiRuntime.worldTick(minecraft, world);
            Events.WORLD_TICK.publish(new WorldEventCtx(minecraft, world));
        }
    }

    private static void synchronizeLifecycle(Minecraft minecraft) {
        NativeDiscoveryRuntime.observeClient(minecraft);
        if (minecraft != null) {
            EntityModifierRuntime.synchronize(minecraft.theWorld, minecraft.thePlayer);
        }
        BetaMoonClient client = BetaMoonClient.getInstance();
        if (client != null && minecraft != null) {
            client.synchronizeTickLifecycle(minecraft);
        }
    }
}
