package betamoon.gui.screen;

import betamoon.gui.component.GuiComponentRegistry;
import betamoon.gui.GuiBetaMoonMainMenu;
import betamoon.gui.style.GuiThemeRegistry;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.luaapi.LuaApiUtils;
import betamoon.teleport.TeleportRuntime;
import java.lang.ref.WeakReference;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiMainMenu;
import net.minecraft.src.World;

/**
 * Logical client host coordinator. Registration alone never opens a native
 * screen.
 */
public final class GuiClientUiRuntime {
    private static final GuiComponentRegistry COMPONENTS = new GuiComponentRegistry();
    private static final GuiThemeRegistry THEMES = new GuiThemeRegistry();
    private static final GuiScreenRegistry SCREENS = new GuiScreenRegistry();
    private static ClientHost host;

    public static GuiComponentRegistry components() {
        return COMPONENTS;
    }

    public static GuiThemeRegistry themes() {
        return THEMES;
    }

    public static GuiScreenRegistry screens() {
        return SCREENS;
    }

    public static GuiScreenActivation current() {
        return host == null ? null : host.controller.current();
    }

    public static GuiScreenController.OpenResult open(String owner, String key, Map<String, Object> state,
            Map<String, Object> data) {
        if (host == null) {
            return GuiScreenController.unavailable("client_unavailable");
        }
        return host.controller.open(owner, key, state, data);
    }

    public static void tick(Minecraft client) {
        if (client == null) {
            return;
        }
        if (host == null || host.client != client) {
            if (host != null) {
                host.controller.reset("client_replaced");
            }
            host = new ClientHost(client);
        }
        host.controller.tick();
    }

    public static void worldTick(Minecraft client, World world) {
        if (host != null && host.client == client && client.theWorld == world) {
            host.controller.worldTick();
        }
    }

    private static final class ClientHost implements GuiScreenController.Host {
        final Minecraft client;
        final GuiScreenController controller = new GuiScreenController(SCREENS, this);
        WeakReference<Object> world = new WeakReference<>(null);
        WeakReference<Object> player = new WeakReference<>(null);
        boolean hadWorld;
        boolean hadPlayer;
        boolean alive;
        long environment;

        ClientHost(Minecraft client) {
            this.client = client;
            environment();
        }

        public long environment() {
            boolean currentAlive = worldAvailable();
            if (world.get() != client.theWorld || player.get() != client.thePlayer
                    || hadWorld != (client.theWorld != null) || hadPlayer != (client.thePlayer != null)
                    || alive != currentAlive) {
                world = new WeakReference<>(client.theWorld);
                player = new WeakReference<>(client.thePlayer);
                hadWorld = client.theWorld != null;
                hadPlayer = client.thePlayer != null;
                alive = currentAlive;
                environment++;
            }
            return environment;
        }

        public boolean available() {
            NativeBindings bindings = NativeDiscoveryRuntime.bindingsIfAvailable();
            return bindings != null && bindings.onClientThread() && client.fontRenderer != null
                    && client.renderEngine != null;
        }

        public boolean worldAvailable() {
            return client.theWorld != null && !client.theWorld.multiplayerWorld && client.thePlayer != null
                    && client.thePlayer.worldObj == client.theWorld && client.thePlayer.health > 0;
        }

        public boolean paused() {
            return client.isGamePaused || TeleportRuntime.frozen()
                    || client.currentScreen != null && client.currentScreen.doesGuiPauseGame();
        }

        public boolean canOpen() {
            return client.currentScreen == null || (client.currentScreen.getClass() == GuiMainMenu.class
                    || client.currentScreen.getClass() == GuiBetaMoonMainMenu.class);
        }

        public Object screenIdentity() {
            return client.currentScreen;
        }

        public boolean current(GuiScreenActivation activation) {
            return activation.page instanceof GuiNativeScreenPage
                    && client.currentScreen == ((GuiNativeScreenPage) activation.page).screen();
        }

        public GuiScreenController.Page prepare(GuiScreenActivation activation, GuiScreenController.History history) {
            return new GuiNativeScreenPage(client, activation, SCREENS, COMPONENTS, history);
        }

        public void display(GuiScreenActivation activation) {
            client.displayGuiScreen(((GuiNativeScreenPage) activation.page).screen());
        }

        public void fallback() {
            client.displayGuiScreen(null);
        }

        public void failure(String owner, String path, Throwable failure) {
            LuaApiUtils.warnForScript(owner, "UI", path + ": " + failure.getMessage(), failure);
        }
    }
    private GuiClientUiRuntime() {
    }
}
