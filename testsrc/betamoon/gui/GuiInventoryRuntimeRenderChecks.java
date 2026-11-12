package betamoon.gui;

import betamoon.gui.inventory.GuiInventoryActivation;
import betamoon.gui.inventory.GuiInventoryNativeTest;
import betamoon.gui.inventory.GuiInventoryRuntime;
import betamoon.gui.inventory.GuiInventoryPreview;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.luaapi.client.ui.GuiInventoryParser;
import betamoon.luaapi.client.ui.GuiScreenParser;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.GuiInventory;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.World;
import net.minecraft.src.Entity;
import net.minecraft.src.Render;
import net.minecraft.src.RenderManager;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.opengl.GL11;

/**
 * Driver-backed presentation lifecycle using a fake client and the real shared
 * document runtime.
 */
final class GuiInventoryRuntimeRenderChecks {
    private static final String OWNER = "inventory-render-fixture";

    static void run(Minecraft client, World world, File output) throws Exception {
        NativeLoaderRegistry loaders = new NativeLoaderRegistry();
        loaders.id(GuiInventoryRuntimeRenderChecks.class.getClassLoader());
        TinyMappingResolver mappings;
        try (FileInputStream input = new FileInputStream("tools/rmcp/conf/mappings.tiny")) {
            mappings = TinyMappingResolver.read(input);
        }
        NativeBindings bindings = new NativeBindings(new NativeNames(mappings.snapshot(), "named"), loaders);
        Field installed = NativeDiscoveryRuntime.class.getDeclaredField("bindings");
        installed.setAccessible(true);
        Object previous = installed.get(null);
        World previousWorld = client.theWorld;
        EntityPlayerSP previousPlayer = client.thePlayer;
        GuiScreen previousScreen = client.currentScreen;
        installed.set(null, bindings);
        try {
            client.theWorld = world;
            client.thePlayer = GuiInventoryNativeTest.player(world);
            GuiInventory screen = new GuiInventory(client.thePlayer);
            client.currentScreen = screen;
            screen.setWorldAndResolution(client, 320, 240);
            Globals lua = JsePlatform.standardGlobals();
            GuiClientUiRuntime.screens().add(GuiScreenParser.parse(OWNER,
                    lua.load("return {key='fixture:inventory-content',requires={'native_inventory','native_items'},"
                            + "root={type='button',width=120,height=20,anchorY=0,"
                            + "bind=function(ctx) layoutBinds=(layoutBinds or 0)+1; boundLayout=ctx.inventory:layout(); "
                            + "return {text='Inventory settings'} end},"
                            + "on={update=function(ctx) local layout=ctx.inventory:layout(); "
                            + "seen=layout.slots[45].id; callbackLayout=layout; retained=ctx.inventory end}}").call(),
                    GuiClientUiRuntime.components()));
            GuiInventoryRuntime.registry().add(GuiInventoryParser.parse(OWNER, lua.load(
                    "return {key='fixture:inventory',screen='fixture:inventory-content',panel={width=200,height=200},"
                            + "groups={hotbar={x=12,y=178}},preview={visible=false},craftingLabel=false}")
                    .call()));
            GuiInventoryActivation claim = GuiInventoryRuntime.registry().activate(OWNER, "fixture:inventory");
            GuiInventoryRuntime.tick(client);
            require(lua.get("boundLayout") == lua.get("callbackLayout"),
                    "Inventory binding and update recopied the same detached layout publication");
            require(client.thePlayer.inventorySlots.getSlot(36).yDisplayPosition == 142,
                    "Inventory geometry changed during update instead of the displayed-frame boundary");
            render(screen, client);
            require(claim.status().equals("active") && lua.get("seen").tojstring().equals("hotbar/9")
                    && client.thePlayer.inventorySlots.getSlot(36).yDisplayPosition == 178,
                    "Prepared attached inventory content did not become active: " + claim.status() + "/"
                            + claim.reason());
            ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png",
                    new File(output, "inventory-profile-content.png"));
            require((GuiShowcaseRenderTest.capture(320, 240).getRGB(110, 10) & 0xFFFFFF) != 0x202020,
                    "Active inventory document did not paint its authored control");
            require(!lua.load("return pcall(function() return retained:layout() end)").call().toboolean(),
                    "Inventory callback layout access survived its scope");
            screen.width = 180;
            GuiInventoryRuntime.beforeDraw(screen, 0, 0, 0);
            require(!GuiInventoryRuntime.background(screen)
                    && client.thePlayer.inventorySlots.getSlot(36).yDisplayPosition == 142,
                    "Resize painted stale custom geometry before preparation");
            GuiInventoryRuntime.afterDraw(screen);
            GuiInventoryRuntime.tick(client);
            require(claim.status().equals("suspended") && claim.reason().equals("viewport_capacity"),
                    "Too-small viewport did not suspend its inventory profile");
            screen.width = 320;
            GuiInventoryRuntime.tick(client);
            render(screen, client);
            require(claim.status().equals("active"), "Fitting inventory profile did not resume after resize");
            GuiInventoryRuntime.tick(client);
            int layoutBinds = lua.get("layoutBinds").toint();
            int previousPanelX = lua.get("boundLayout").get("panel").get("x").toint();
            screen.width = 360;
            GuiInventoryRuntime.tick(client);
            require(lua.get("layoutBinds").toint() > layoutBinds
                    && lua.get("boundLayout").get("panel").get("x").toint() == previousPanelX + 20,
                    "Inventory resize did not invalidate bindings that observed its detached native layout");
            screen.width = 320;
            GuiInventoryRuntime.tick(client);
            render(screen, client);
            GuiInventoryRuntime.tick(client);
            layoutBinds = lua.get("layoutBinds").toint();
            GuiInventoryRuntime.tick(client);
            require(lua.get("layoutBinds").toint() == layoutBinds,
                    "Unchanged inventory source reevaluated a clean observed binding");
            GuiRenderProfile.measure(client, output, "inventory-content", () -> render(screen, client));
            require(lua.get("layoutBinds").toint() == layoutBinds,
                    "Inventory render profiling reevaluated a prepared Lua binding");
            GuiInventoryRuntime.registry()
                    .add(GuiInventoryParser.parse(OWNER,
                            lua.load("return {key='fixture:priority',priority=1,panel={width=200,height=200},"
                                    + "groups={hotbar={x=12,y=178}},preview={visible=false},craftingLabel=false}")
                                    .call()));
            GuiInventoryActivation higher = GuiInventoryRuntime.registry().activate(OWNER, "fixture:priority");
            GuiInventoryRuntime.tick(client);
            render(screen, client);
            require(higher.status().equals("active") && claim.status().equals("suspended"),
                    "Higher inventory priority did not suspend the existing document");
            higher.close();
            GuiInventoryRuntime.tick(client);
            render(screen, client);
            require(claim.status().equals("active"), "Closing the winner did not restore the retained document");
            client.thePlayer.health = 0;
            GuiInventoryRuntime.beforeDraw(screen, 0, 0, 0);
            require(!GuiInventoryRuntime.background(screen)
                    && client.thePlayer.inventorySlots.getSlot(36).yDisplayPosition == 142,
                    "Dead player retained custom inventory geometry before the native close");
            GuiInventoryRuntime.afterDraw(screen);
            GuiInventoryRuntime.tick(client);
            require(claim.status().equals("suspended") && claim.reason().equals("world_unavailable"),
                    "Unavailable world/player did not suspend its inventory claim");
            client.thePlayer.health = 20;
            GuiInventoryRuntime.tick(client);
            render(screen, client);
            GuiClientUiRuntime.screens()
                    .add(GuiScreenParser.parse(OWNER,
                            lua.load("return {key='fixture:failed-content',root={type='text',text='Invalid'},"
                                    + "on={update=function() error('expected inventory update failure') end}}").call(),
                            GuiClientUiRuntime.components()));
            GuiInventoryRuntime.registry().add(GuiInventoryParser.parse(OWNER, lua.load(
                    "return {key='fixture:failed',screen='fixture:failed-content',priority=2,preview={visible=false}}")
                    .call()));
            GuiInventoryActivation broken = GuiInventoryRuntime.registry().activate(OWNER, "fixture:failed");
            GuiInventoryRuntime.tick(client);
            require(broken.status().equals("refused"), "Failed attached document retained replacement eligibility");
            GuiInventoryRuntime.tick(client);
            render(screen, client);
            require(claim.status().equals("active"), "Failed winner did not restore the next eligible profile");
            broken.close();
            claim.close();
            GuiInventoryRuntime.beforeDraw(screen, 0, 0, 0);
            require(!GuiInventoryRuntime.background(screen)
                    && client.thePlayer.inventorySlots.getSlot(36).yDisplayPosition == 142,
                    "Closed inventory claim retained painting or native hit geometry");
            GuiInventoryRuntime.afterDraw(screen);
            require(((Number) bindings.clients.diagnostics().get("sessions")).intValue() == 0,
                    "Last inventory claim retained native client sessions");
            previewFailure(client);
            GuiHudRuntimeRenderChecks.run(client, output);
            GuiNotificationRuntimeRenderChecks.run(client, output);
            GuiNativeHudRenderChecks.run(client, mappings, output);
            GuiScreenWorldTransitionRenderChecks.run(client, output);
            GuiContainerDocumentRenderChecks.run(client, output);
            GuiContainerIntentRenderChecks.run(client);
            GuiContainerLifetimeRenderChecks.run(client);
            GuiContainerAdvancedRenderChecks.run(client, output);
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Inventory runtime leaked a GL error");
        } finally {
            GuiInventoryRuntime.closed(client.currentScreen);
            ScriptResourceTracker.unload(OWNER);
            client.currentScreen = previousScreen;
            client.theWorld = previousWorld;
            client.thePlayer = previousPlayer;
            installed.set(null, previous);
            bindings.close();
            loaders.close();
        }
        System.out.println("Offscreen inventory runtime passed: attached content, detached scoped layout, paint-time "
                + "geometry, resize suspension/resume, close fallback and session cleanup.");
    }

    private static void render(GuiInventory screen, Minecraft client) {
        GuiShowcaseRenderTest.setupFrame();
        GuiInventoryRuntime.beforeDraw(screen, 0, 0, 0);
        require(GuiInventoryRuntime.background(screen), "Prepared inventory background was unavailable");
        // GuiContainer translates to this origin for native slots and its cursor-stack
        // reads.
        GL11.glPushMatrix();
        GL11.glTranslatef(60, 20, 0);
        try {
            GuiInventoryRuntime.beforeCursor(client.thePlayer.inventory);
            GuiInventoryRuntime.beforeCursor(client.thePlayer.inventory);
            require(GuiInventoryRuntime.foreground(screen), "Prepared inventory foreground was unavailable");
        } finally {
            GL11.glPopMatrix();
        }
        GuiInventoryRuntime.afterDraw(screen);
    }

    @SuppressWarnings("unchecked")
    private static void previewFailure(Minecraft client) throws Exception {
        EntityPlayerSP player = client.thePlayer;
        Field field = RenderManager.class.getDeclaredField("entityRenderMap");
        field.setAccessible(true);
        Map<Class<?>, Render> renderers = (Map<Class<?>, Render>) field.get(RenderManager.instance);
        Render previous = renderers.put(player.getClass(), new Render() {
            public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
                GL11.glPushMatrix();
                GL11.glTranslatef(100, 100, 100);
                throw new IllegalStateException("expected preview renderer failure");
            }
        });
        player.rotationYaw = 13;
        player.rotationPitch = 17;
        player.renderYawOffset = 19;
        player.entityBrightness = 0.4F;
        float camera = RenderManager.instance.playerViewY;
        int depth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        try {
            try {
                GuiInventoryPreview.draw(client, Rect.fromPositionAndSize(21, 15, 60, 60), 50, 50, 320, 240);
                throw new AssertionError("Preview renderer failure was hidden");
            } catch (IllegalStateException expected) {
                require(expected.getMessage().equals("expected preview renderer failure"), "Unexpected preview error");
            }
            require(player.rotationYaw == 13 && player.rotationPitch == 17 && player.renderYawOffset == 19
                    && player.entityBrightness == 0.4F && RenderManager.instance.playerViewY == camera
                    && GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH) == depth,
                    "Failed native preview retained player pose, camera yaw or an unbalanced matrix");
        } finally {
            if (previous == null) {
                renderers.remove(player.getClass());
            } else {
                renderers.put(player.getClass(), previous);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
