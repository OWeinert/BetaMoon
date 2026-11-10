package betamoon.gui;

import betamoon.gui.hud.GuiHudActivation;
import betamoon.gui.hud.GuiHudNativePart;
import betamoon.gui.hud.GuiHudRuntime;
import betamoon.gui.interop.GuiResourceBudget;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.instrumentation.diagnostics.TransformationReport;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.instrumentation.registry.BuiltinHookModules;
import betamoon.instrumentation.registry.HookRegistry;
import betamoon.instrumentation.transform.BetaMoonTransformer;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.luaapi.client.ui.GuiComponentParser;
import betamoon.luaapi.client.ui.GuiHudParser;
import betamoon.luaapi.client.ui.GuiScreenParser;
import betamoon.luamodloader.ScriptResourceTracker;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.ContainerPlayer;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityRenderer;
import net.minecraft.src.GuiIngame;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.InventoryPlayer;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.Material;
import net.minecraft.src.PlayerController;
import net.minecraft.src.PlayerControllerSP;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.opengl.GL11;
import sun.misc.Unsafe;

/**
 * Executes real transformed native HUD rendering in the existing
 * pbuffer/fake-client fixture.
 */
final class GuiNativeHudRenderChecks {
    private static final String OWNER = "native-hud-render-fixture";
    private static final String HUD = "net.minecraft.src.GuiIngame";
    private static final String CALLBACKS = "betamoon.instrumentation.hooks.gui.GuiHudCallbacks";
    private static final String[] HOOKS = {"betamoon:gui_hud:native_frame", "betamoon:gui_hud:textures",
            "betamoon:gui_hud:hotbar_items", "betamoon:gui_hud:armor", "betamoon:gui_hud:chat",
            "betamoon:fluid_camera:air_hud"};

    static void run(Minecraft client, TinyMappingResolver mappings, File output) throws Exception {
        EntityPlayerSP previousPlayer = client.thePlayer;
        World previousWorld = client.theWorld;
        PlayerController previousController = client.playerController;
        EntityRenderer previousRenderer = client.entityRenderer;
        GuiScreen previousScreen = client.currentScreen;
        boolean fancy = client.gameSettings.fancyGraphics;
        boolean debug = client.gameSettings.showDebugInfo;
        int scale = client.gameSettings.guiScale;
        int width = client.displayWidth;
        int height = client.displayHeight;
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        long resourceBytes = GuiResourceBudget.allocated();
        int sessions = sessions();
        try {
            client.currentScreen = null;
            client.gameSettings.fancyGraphics = false;
            client.gameSettings.showDebugInfo = false;
            client.gameSettings.guiScale = 1;
            client.displayWidth = 320;
            client.displayHeight = 240;
            Player player = player();
            client.theWorld = new HudWorld();
            player.worldObj = client.theWorld;
            client.thePlayer = player;
            client.playerController = new PlayerControllerSP(client);
            client.entityRenderer = new EntityRenderer(client);
            GuiIngame nativeHud = new GuiIngame(client);
            nativeHud.addChatMessage("Native chat remains stored");
            Object transformed = transformed(client, mappings);
            Class<?> type = transformed.getClass();
            type.getMethod("addChatMessage", String.class).invoke(transformed, "Native chat remains stored");
            Method draw = type.getMethod("renderGameOverlay", float.class, boolean.class, int.class, int.class);
            Field chat = type.getDeclaredField("chatMessageList");
            chat.setAccessible(true);
            Object messages = chat.get(transformed);
            GuiHudRuntime.close();
            BufferedImage baseline = render(nativeHud,
                    GuiIngame.class.getMethod("renderGameOverlay", float.class, boolean.class, int.class, int.class),
                    client);
            BufferedImage untouched = render(transformed, draw, client);
            require(equal(baseline, untouched), "Transformed native HUD changed pixels without an active claim");
            ImageIO.write(baseline, "png", new File(output, "hud-native-original.png"));
            Globals lua = JsePlatform.standardGlobals();
            for (GuiHudNativePart part : GuiHudNativePart.values()) {
                GuiHudActivation first = claim(lua, OWNER + "-a", "fixture:native-hide-a", part.name + "={hide=true}");
                GuiHudActivation second = claim(lua, OWNER + "-b", "fixture:native-hide-b", part.name + "={hide=true}");
                GuiHudRuntime.tick(client);
                BufferedImage hidden = render(transformed, draw, client);
                require(!equal(baseline, hidden), "Actual transformed renderer did not hide " + part.name);
                require(outsideUnchanged(baseline, hidden, part),
                        "Hiding " + part.name + " changed another native HUD region");
                require(status(first, part).equals("hidden") && status(second, part).equals("hidden"),
                        "Actual native seam did not publish independent hide claims for " + part.name);
                ScriptResourceTracker.unload(OWNER + "-a");
                require(equal(hidden, render(transformed, draw, client)),
                        "Closing one owner restored a native part still claimed by another: " + part.name);
                second.close();
                require(equal(baseline, render(transformed, draw, client)),
                        "Last-owner close failed to restore actual native pixels for " + part.name);
                require(chat.get(transformed) == messages && ((List<?>) messages).size() == 1,
                        "Native chat filtering replaced or mutated the backing messages");
                GuiHudRuntime.tick(client);
            }
            GuiClientUiRuntime.components().add(GuiComponentParser.parse(OWNER,
                    lua.load("return {key='fixture:native-hud-paint',size={width=12,height=12},paint=function() "
                            + "paintCalls=(paintCalls or 0)+1; return {{op='rect',width=12,height=12,color='#00DD88'}} end}")
                            .call()));
            StringBuilder replacements = new StringBuilder();
            int[][] positions = {{154, 114}, {70, 220}, {70, 208}, {172, 208}, {70, 196}, {2, 184}};
            for (GuiHudNativePart part : GuiHudNativePart.values()) {
                String key = "fixture:native-" + part.name + "-content";
                int[] position = positions[part.ordinal()];
                GuiClientUiRuntime.screens()
                        .add(GuiScreenParser.parse(OWNER,
                                lua.load("return {key='" + key + "',root={component='fixture:native-hud-paint',"
                                        + "anchorX=0,anchorY=0,x=" + position[0] + ",y=" + position[1] + "}}").call(),
                                GuiClientUiRuntime.components()));
                if (replacements.length() > 0) {
                    replacements.append(',');
                }
                replacements.append(part.name).append("={replace='").append(key).append("'}");
            }
            GuiHudActivation replacement = claim(lua, "fixture:native-replacements", replacements.toString());
            GuiHudRuntime.tick(client);
            BufferedImage custom = render(transformed, draw, client);
            for (GuiHudNativePart part : GuiHudNativePart.values()) {
                require(status(replacement, part).equals("replaced"),
                        "Actual transformed renderer did not paint/replace " + part.name + ": "
                                + status(replacement, part));
            }
            ImageIO.write(custom, "png", new File(output, "hud-native-replacements.png"));
            int paintCalls = lua.get("paintCalls").toint();
            GuiRenderProfile.measure(client, output, "native-hud-replacements", () -> frame(transformed, draw, client));
            require(lua.get("paintCalls").toint() == paintCalls, "Native render frames reentered prepared Lua paint");
            replacement.close();
            require(equal(baseline, render(transformed, draw, client)),
                    "Closing all replacements changed native output");
            GuiHudRuntime.tick(client);
            GuiRenderProfile.measure(client, output, "native-hud", () -> frame(transformed, draw, client));
            require(player.health == 20 && player.inventory.mainInventory[0].stackSize == 9,
                    "Native HUD customization changed health or inventory");
        } finally {
            ScriptResourceTracker.unload(OWNER);
            ScriptResourceTracker.unload(OWNER + "-a");
            ScriptResourceTracker.unload(OWNER + "-b");
            GuiHudRuntime.close();
            for (String hook : HOOKS) {
                AgentRuntime.getReport().registered(hook);
            }
            client.currentScreen = previousScreen;
            client.thePlayer = previousPlayer;
            client.theWorld = previousWorld;
            client.playerController = previousController;
            client.entityRenderer = previousRenderer;
            client.gameSettings.fancyGraphics = fancy;
            client.gameSettings.showDebugInfo = debug;
            client.gameSettings.guiScale = scale;
            client.displayWidth = width;
            client.displayHeight = height;
            GL11.glDepthFunc(depthFunction);
        }
        require(GuiResourceBudget.allocated() == resourceBytes, "Actual native HUD fixture retained owned resources");
        require(sessions() == sessions, "Repeated native HUD claims retained client sessions");
        System.out.println("Transformed native HUD rendering passed: unchanged no-claim pixels, six independently "
                + "hidden/replaced parts, last-owner restoration, preserved chat/items, prepared Lua reuse and profiles.");
    }

    private static Object transformed(Minecraft client, TinyMappingResolver mappings) throws Exception {
        TransformationReport report = new TransformationReport();
        HookRegistry registry = new HookRegistry(report);
        BuiltinHookModules.registerAll(registry);
        BetaMoonTransformer transformer = new BetaMoonTransformer(
                registry.freeze(mappings, RuntimeNamespace.NAMED, RuntimeNamespace.NAMED), mappings, report, true,
                false);
        byte[] source = bytes(HUD);
        byte[] transformed = transformer.transform(null, HUD.replace('.', '/'), null, null, source);
        require(transformed != null && !report.hasFailures(), "Native HUD transformation failed before execution");
        byte[] callbacks = bytes(CALLBACKS);
        ClassLoader loader = new ClassLoader(GuiNativeHudRenderChecks.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(HUD) && !name.equals(CALLBACKS)) {
                    return super.loadClass(name, resolve);
                }
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        byte[] code = name.equals(HUD) ? transformed : callbacks;
                        loaded = defineClass(name, code, 0, code.length);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
        };
        for (String hook : HOOKS) {
            require(report.isApplied(hook), "Native fixture did not apply required seam " + hook);
            AgentRuntime.getReport().applied(hook, HUD);
        }
        return loader.loadClass(HUD).getConstructor(Minecraft.class).newInstance(client);
    }

    private static byte[] bytes(String name) throws Exception {
        try (InputStream input = GuiNativeHudRenderChecks.class
                .getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            require(input != null, "Missing named native fixture class " + name);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static GuiHudActivation claim(Globals lua, String key, String parts) {
        return claim(lua, OWNER, key, parts);
    }

    private static GuiHudActivation claim(Globals lua, String owner, String key, String parts) {
        GuiHudRuntime.registry().add(
                GuiHudParser.parse(owner, lua.load("return {key='" + key + "',nativeParts={" + parts + "}}").call()));
        return GuiHudRuntime.registry().activate(owner, key);
    }

    private static int sessions() {
        return ((Number) NativeDiscoveryRuntime.bindingsIfAvailable().clients.diagnostics().get("sessions")).intValue();
    }

    private static String status(GuiHudActivation claim, GuiHudNativePart part) {
        return String.valueOf(((Map<?, ?>) GuiHudRuntime.parts(claim).get(part.name)).get("status"));
    }

    private static BufferedImage render(Object hud, Method draw, Minecraft client) throws Exception {
        frame(hud, draw, client);
        return GuiShowcaseRenderTest.capture(320, 240);
    }

    private static void frame(Object hud, Method draw, Minecraft client) throws Exception {
        GuiShowcaseRenderTest.setupFrame();
        // Minecraft.startGame establishes LEQUAL; RenderItem restores depth before
        // native chat text.
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        draw.invoke(hud, 0F, false, 0, 0);
        GuiHudRuntime.clientOverlay(client, 0, 0, 0);
    }

    private static Player player() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Player player = (Player) ((Unsafe) field.get(null)).allocateInstance(Player.class);
        player.inventory = new InventoryPlayer(player);
        player.inventorySlots = new ContainerPlayer(player.inventory, true);
        player.craftingInventory = player.inventorySlots;
        player.health = 20;
        player.prevHealth = 20;
        player.air = 180;
        Field bounds = Entity.class.getDeclaredField("boundingBox");
        bounds.setAccessible(true);
        bounds.set(player, AxisAlignedBB.getBoundingBox(0, 64, 0, 0.6, 65.8, 0.6));
        player.posY = 64;
        player.inventory.mainInventory[0] = new ItemStack(Item.stick, 9);
        return player;
    }

    private static final class Player extends EntityPlayerSP {
        private Player() {
            super(null, null, null, 0);
        }

        @Override
        public int getPlayerArmorValue() {
            return 10;
        }

        @Override
        public boolean isInsideOfMaterial(Material material) {
            return material == Material.water;
        }
    }

    private static final class HudWorld extends World {
        private HudWorld() {
            super(null, "offscreen_hud_fixture", new WorldProvider() {
            }, 0L);
        }

        @Override
        protected IChunkProvider getChunkProvider() {
            return null;
        }

        @Override
        public boolean blockExists(int x, int y, int z) {
            return false;
        }

        @Override
        public int getBlockId(int x, int y, int z) {
            return 0;
        }
    }

    private static boolean equal(BufferedImage left, BufferedImage right) {
        for (int y = 0; y < left.getHeight(); y++) {
            for (int x = 0; x < left.getWidth(); x++) {
                if (left.getRGB(x, y) != right.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean outsideUnchanged(BufferedImage baseline, BufferedImage hidden, GuiHudNativePart part) {
        int[][] bounds = {{153, 113, 170, 130}, {68, 216, 253, 240}, {68, 207, 151, 218}, {169, 207, 252, 218},
                {68, 198, 151, 209}, {0, 190, 320, 202}};
        int[] region = bounds[part.ordinal()];
        for (int y = 0; y < baseline.getHeight(); y++) {
            for (int x = 0; x < baseline.getWidth(); x++) {
                if ((x < region[0] || x >= region[2] || y < region[1] || y >= region[3])
                        && baseline.getRGB(x, y) != hidden.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
