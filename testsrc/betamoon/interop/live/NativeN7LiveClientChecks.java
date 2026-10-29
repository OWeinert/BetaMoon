package betamoon.interop.live;

import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.interop.discovery.NativeQueryService;
import java.util.List;
import java.util.Map;

/**
 * Extra N7 acceptance using actual menus, native listener faults and
 * Minecraft's player respawn.
 */
final class NativeN7LiveClientChecks {
    private static final String CLIENT = "net/minecraft/client/Minecraft";
    private static Object menu;
    private static Object oldPlayer;
    private static Object originalWorld;
    private static int inventoryKey;
    private static int phase;

    private NativeN7LiveClientChecks() {
    }

    static boolean beforeWorld(Object client) throws Exception {
        if (!Boolean.TRUE.equals(NativeDiscoveryRuntime.snapshot().coverage.get("ready"))) {
            return false;
        }
        if (menu == null) {
            Map<String, Object> page = NativeQueryService.query(NativeDiscoveryRuntime.snapshot(),
                    CatalogValues.record("kind", "classes", "name", "missing.optional.N7Mod", "limit", 5));
            require(((List<?>) page.get("items")).isEmpty(), "missing optional mod produced a class");
            require(world(client) == null, "no-world discovery ran after world creation");
            inventoryKey = inventoryKey(client);
            ClassLoader loader = client.getClass().getClassLoader();
            // The constructor carries the actual runtime GuiScreen type, including
            // production obfuscation.
            Class<?> scripts = Class.forName("betamoon.gui.GuiScreenScripts", true, loader);
            require(scripts.getConstructors().length == 1, "script screen constructor became ambiguous");
            menu = scripts.getConstructors()[0].newInstance(new Object[]{null});
            NativeLiveClientDriver.call(client, CLIENT, "displayGuiScreen", "(Lnet/minecraft/src/GuiScreen;)V", menu);
            return false;
        }
        require(NativeLiveClientDriver.field(client, CLIENT, "currentScreen", "Lnet/minecraft/src/GuiScreen;") == menu,
                "script screen did not survive a rendered frame");
        require(inventoryKey(client) == inventoryKey, "menu changed the player's inventory binding");
        NativeLiveClientDriver.call(client, CLIENT, "displayGuiScreen", "(Lnet/minecraft/src/GuiScreen;)V",
                new Object[]{null});
        return true;
    }

    static boolean inWorld(Object client, Object world) throws Exception {
        if (phase == 0) {
            originalWorld = world;
            System.setProperty("betamoon.n7.phase", "cycles");
            phase = 1;
        }
        if (phase == 1 && "16".equals(System.getProperty("betamoon.n7.cycles"))) {
            require("true".equals(System.getProperty("betamoon.n7.menuSeen")), "no-world Lua roots were not exercised");
            require(count("attachments") == 1, "cycle cleanup failed to preserve only the existing observer");
            oldPlayer = player(client);
            System.setProperty("betamoon.n7.phase", "respawn");
            phase = 2;
        }
        if (phase == 2 && "confirmed".equals(System.getProperty("betamoon.n7.respawn"))) {
            require(world == originalWorld && player(client) != oldPlayer,
                    "native respawn did not replace the real player");
            require(count("attachments") == 0 && count("implementations") == 0 && count("pendingRemovals") == 0,
                    "player replacement left a stale native listener");
            require(inventoryKey(client) == inventoryKey, "reload or respawn changed an input binding");
            oldPlayer = null;
            originalWorld = null;
            menu = null;
            System.setProperty("betamoon.n7.phase", "passed");
            System.out
                    .println("[N7-LIVE] menus/no-world roots, 16 cleanup/fault cycles and real player respawn passed.");
            return true;
        }
        return false;
    }

    private static int count(String name) {
        return ((Number) NativeDiscoveryRuntime.bindingsIfAvailable().interfaces.diagnostics().get(name)).intValue();
    }

    private static Object player(Object client) throws Exception {
        return NativeLiveClientDriver.field(client, CLIENT, "thePlayer", "Lnet/minecraft/src/EntityPlayerSP;");
    }

    private static Object world(Object client) throws Exception {
        return NativeLiveClientDriver.field(client, CLIENT, "theWorld", "Lnet/minecraft/src/World;");
    }

    private static int inventoryKey(Object client) throws Exception {
        Object settings = NativeLiveClientDriver.field(client, CLIENT, "gameSettings",
                "Lnet/minecraft/src/GameSettings;");
        Object key = NativeLiveClientDriver.field(settings, "net/minecraft/src/GameSettings", "keyBindInventory",
                "Lnet/minecraft/src/KeyBinding;");
        return ((Number) NativeLiveClientDriver.field(key, "net/minecraft/src/KeyBinding", "keyCode", "I")).intValue();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
