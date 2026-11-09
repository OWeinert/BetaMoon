package betamoon.gui.hud;

import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.instrumentation.agent.BetaMoonAgent;

/**
 * Client-lifetime detached queue; presentation resources remain owned by the
 * existing overlay host.
 */
public final class GuiNotificationRuntime {
    private static final long STARTED = System.nanoTime();
    private static final GuiNotificationQueue QUEUE = new GuiNotificationQueue(GuiNotificationRuntime::millis);
    private static final GuiNotificationRegistry REGISTRY = new GuiNotificationRegistry();
    private static WeakReference<Minecraft> client = new WeakReference<>(null);

    public static GuiNotificationQueue queue() {
        return QUEUE;
    }

    public static GuiNotificationRegistry registry() {
        return REGISTRY;
    }

    static long millis() {
        return (System.nanoTime() - STARTED) / 1000000;
    }

    static void client(Minecraft value) {
        client = new WeakReference<>(value);
    }

    static void unavailable(Minecraft current) {
        QUEUE.advance(current == null ? null : current.theWorld, current == null ? null : current.thePlayer, false,
                (notification, occupied) -> null);
    }

    public static GuiNotificationQueue.ShowResult show(GuiNotificationDefinition definition,
            GuiNotificationOptions options) {
        LuaCallbackScope.requireUiStateWrite();
        Minecraft current = client.get();
        if (current == null) {
            return new GuiNotificationQueue.ShowResult(null, "client_unavailable");
        }
        if (current.fontRenderer == null || current.renderEngine == null || current.displayWidth <= 0
                || current.displayHeight <= 0 || NativeDiscoveryRuntime.bindingsIfAvailable() == null) {
            return new GuiNotificationQueue.ShowResult(null, "client_resources_unavailable");
        }
        if (BetaMoonAgent.isRegistered() && !AgentRuntime.getReport().isApplied("betamoon:gui_hud:client_frame")) {
            return new GuiNotificationQueue.ShowResult(null, "render_hook_unavailable");
        }
        if (options.lifetime == GuiNotificationOptions.Lifetime.WORLD
                && (current.theWorld == null || current.theWorld.multiplayerWorld || current.thePlayer == null
                        || current.thePlayer.worldObj != current.theWorld || current.thePlayer.health <= 0)) {
            return new GuiNotificationQueue.ShowResult(null, "world_unavailable");
        }
        return QUEUE.show(definition, options, current.theWorld, current.thePlayer);
    }

    private GuiNotificationRuntime() {
    }
}
