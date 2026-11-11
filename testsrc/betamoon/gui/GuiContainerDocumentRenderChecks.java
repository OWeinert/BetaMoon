package betamoon.gui;

import betamoon.gui.interop.GuiResourceBudget;
import betamoon.gui.inventory.GuiInventoryNativeTest;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.luaapi.tileentity.TileEntityApi;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.GuiLuaContainer;
import betamoon.tileentity.LuaTileEntity;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.GuiContainer;
import net.minecraft.src.PlayerController;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.UnexpectedThrowable;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Real native container plus inline retained documents; no game/window/run
 * loop.
 */
final class GuiContainerDocumentRenderChecks {
    private static final String OWNER = "container-document-fixture.lua";
    private static final String OTHER = "container-document-override-fixture.lua";

    static void run(Minecraft parent, File output) throws Exception {
        Field installed = Minecraft.class.getDeclaredField("theMinecraft");
        installed.setAccessible(true);
        Object previous = installed.get(null);
        int sessions = sessions();
        long budget = GuiResourceBudget.allocated();
        int registrations = GuiClientUiRuntime.screens().snapshot().size();
        Client client = new Client();
        client.gameSettings = parent.gameSettings;
        client.renderEngine = parent.renderEngine;
        client.fontRenderer = parent.fontRenderer;
        client.sndManager = parent.sndManager;
        client.playerController = new PlayerController(client);
        client.theWorld = new FixtureWorld();
        client.thePlayer = GuiInventoryNativeTest.player(client.theWorld);
        Globals lua = JsePlatform.standardGlobals();
        LuaTable api = new LuaTable();
        TileEntityApi.attach(api);
        lua.set("betamoon", api);
        GuiLuaContainer screen = null;
        try {
            owner(OWNER);
            ContainerGuiDefinition definition;
            try {
                LuaValue handle = lua.load("local t=betamoon.tileEntities:add{name='inline_document',"
                        + "inventory={slots={input={index=0}}}};"
                        + "local c=betamoon.containers:add{name='inline_document',tileEntity=t,"
                        + "slots={{slot='input',x=150,y=55}},playerInventory={x=8,y=84}};"
                        + "gui=betamoon.containerGuis:add{name='inline_document',container=c,"
                        + "state={count={type='integer',default=0}},"
                        + "on={update=function(ctx) seen=ctx.state:get('count'); stateHandle=ctx.state end},"
                        + "root={type='button',id='increment',width=120,height=20,anchorX=0,anchorY=0,x=8,y=20,"
                        + "bind=function(ctx) binds=(binds or 0)+1;return {text='Count '..ctx.state:get('count')} end,"
                        + "on={activate=function(ctx) ctx.state:set('count',ctx.state:get('count')+1) end}}};return gui")
                        .call();
                definition = ((TileEntityApi.GuiHandle) handle).definition;
            } finally {
                owner(null);
            }
            LuaTileEntity tile = new LuaTileEntity(definition.container.tileEntity.name);
            tile.worldObj = client.theWorld;
            screen = new GuiLuaContainer(client.thePlayer.inventory, tile, definition);
            screen.inventorySlots.windowId = 42;
            client.thePlayer.craftingInventory = screen.inventorySlots;
            client.displayGuiScreen(screen);
            update(screen);
            render(screen);
            require(sessions() == sessions + 1 && GuiClientUiRuntime.screens().snapshot().size() == registrations,
                    "Inline container registered a synthetic global screen or failed to acquire its scoped session");
            int binds = lua.get("binds").toint();
            render(screen);
            render(screen);
            require(lua.get("binds").toint() == binds, "Prepared inline container paint entered Lua bindings");
            click(screen, 84, 61);
            update(screen);
            require(lua.get("seen").toint() == 1 && screen.inventorySlots.windowId == 42
                    && screen.inventorySlots.getSlot(0).xDisplayPosition == 150,
                    "Panel-local shared control input failed or changed the real native backend");
            render(screen);
            ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png",
                    new File(output, "container-inline-document.png"));

            override(lua, "patch=gui:override{changes={layout={width=200,height=166},"
                    + "root={type='button',bind=function() return {width=-1} end}}}");
            update(screen);
            require(panelWidth(screen) == 176 && sessions() == sessions + 1,
                    "Rejected retained candidate replaced legacy panel geometry or leaked native resources");
            click(screen, 84, 61);
            update(screen);
            require(lua.get("seen").toint() == 2, "Rejected candidate disabled the previous usable document");
            override(lua, "patch:remove(); good=gui:override{changes={layout={width=200,height=166},"
                    + "state={count={type='integer',default=0}},"
                    + "on={update=function(ctx) seen=ctx.state:get('count') end},"
                    + "root={type='button',id='increment',width=120,height=20,anchorX=0,anchorY=0,x=8,y=20,"
                    + "text='Refreshed',on={activate=function(ctx) ctx.state:set('count',ctx.state:get('count')+1) end}}}}");
            update(screen);
            update(screen);
            require(panelWidth(screen) == 200 && lua.get("seen").toint() == 2 && sessions() == sessions + 1,
                    "Compatible mixed revision lost local state or failed to publish legacy/document geometry together");
            render(screen);
            click(screen, 72, 61);
            update(screen);
            require(lua.get("seen").toint() == 3, "Refreshed panel-local hit geometry retained the previous origin");
            override(lua, "good:remove()");
            update(screen);
            update(screen);
            require(panelWidth(screen) == 176 && lua.get("seen").toint() == 3,
                    "Removing an override lost retained state or legacy geometry");

            override(lua, OTHER, "foreign=gui:override{changes={layout={width=208,height=166},"
                    + "root={type='button',bind=function() return {width=-1} end}}}");
            update(screen);
            require(panelWidth(screen) == 176 && sessions() == sessions + 1,
                    "Invalid new-owner presentation retired the previous usable inline document");
            override(lua, OTHER, "foreign:remove()");
            update(screen);
            override(lua, OTHER,
                    "foreign=gui:override{changes={layout={width=208,height=166},"
                            + "state={count={type='integer',default=30}},"
                            + "on={update=function(ctx) foreignCount=ctx.state:get('count') end},"
                            + "root={type='button',text='Owned override'}}}");
            update(screen);
            require(panelWidth(screen) == 208 && lua.get("foreignCount").toint() == 30 && sessions() == sessions + 1,
                    "New-owner presentation reused foreign local state or retained the predecessor's native session");
            ScriptResourceTracker.unload(OTHER);
            update(screen);
            update(screen);
            require(panelWidth(screen) == 176 && lua.get("seen").toint() == 0 && sessions() == sessions + 1,
                    "Override owner unload did not restore a fresh base document with the actual native container");

            override(lua, "removed=gui:override{changes={elements={}}}");
            update(screen);
            require(sessions() == sessions && screen.inventorySlots.windowId == 42,
                    "Removing inline content retained native sessions or replaced the container backend");
            require(!lua.load("return pcall(function() return stateHandle:get('count') end)").call().toboolean(),
                    "Container callback state access survived its scope");
            override(lua, "removed:remove()");
            update(screen);
            require(sessions() == sessions + 1, "Restored inline content did not acquire a fresh document lifetime");
            client.displayWidth = 400;
            client.displayHeight = 260;
            screen.setWorldAndResolution(client, 400, 260);
            render(screen);
            click(screen, 124, 71);
            update(screen);
            require(lua.get("seen").toint() == 1 && sessions() == sessions + 1,
                    "Native resize lost inline state or used the previous panel origin for input");
            client.displayWidth = 320;
            client.displayHeight = 240;
            screen.setWorldAndResolution(client, 320, 240);
            render(screen);
        } finally {
            owner(null);
            if (screen != null) {
                screen.onGuiClosed();
            }
            ScriptResourceTracker.unload(OWNER);
            ScriptResourceTracker.unload(OTHER);
            GuiClientUiRuntime.tick(parent);
            installed.set(null, previous);
        }
        require(sessions() == sessions && GuiResourceBudget.allocated() == budget
                && GuiClientUiRuntime.screens().snapshot().size() == registrations,
                "Inline container fixture retained sessions, capacity or global registrations");
        System.out.println("Inline container document passed offscreen: shared retained grammar/input, panel-local "
                + "geometry, no Lua during paint, rejected mixed revision retention, compatible refresh and cleanup.");
    }

    private static void override(Globals lua, String script) throws Exception {
        override(lua, OWNER, script);
    }

    private static void override(Globals lua, String scriptOwner, String script) throws Exception {
        owner(scriptOwner);
        try {
            lua.load(script).call();
        } finally {
            owner(null);
        }
    }

    private static void owner(String owner) throws Exception {
        Method method = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        method.setAccessible(true);
        method.invoke(null, new Object[]{owner});
    }

    private static void update(GuiLuaContainer screen) throws Exception {
        Method method = GuiLuaContainer.class.getDeclaredMethod("preparePresentation");
        method.setAccessible(true);
        method.invoke(screen);
    }

    private static int panelWidth(GuiLuaContainer screen) throws Exception {
        Field field = GuiContainer.class.getDeclaredField("xSize");
        field.setAccessible(true);
        return field.getInt(screen);
    }

    private static void click(GuiLuaContainer screen, int x, int y) throws Exception {
        Method press = GuiLuaContainer.class.getDeclaredMethod("mouseClicked", int.class, int.class, int.class);
        Method release = GuiLuaContainer.class.getDeclaredMethod("mouseMovedOrUp", int.class, int.class, int.class);
        press.setAccessible(true);
        release.setAccessible(true);
        press.invoke(screen, x, y, 0);
        release.invoke(screen, x, y, 0);
    }

    private static void render(GuiLuaContainer screen) throws Exception {
        GuiShowcaseRenderTest.setupFrame();
        Method method = GuiLuaContainer.class.getDeclaredMethod("drawGuiContainerBackgroundLayer", float.class);
        method.setAccessible(true);
        method.invoke(screen, 0f);
    }

    private static int sessions() {
        return ((Number) NativeDiscoveryRuntime.bindingsIfAvailable().clients.diagnostics().get("sessions")).intValue();
    }

    private static final class Client extends Minecraft {
        Client() {
            super(null, null, null, 320, 240, false);
        }

        @Override
        public void displayGuiScreen(GuiScreen screen) {
            if (currentScreen != null) {
                currentScreen.onGuiClosed();
            }
            currentScreen = screen;
            if (screen != null) {
                screen.setWorldAndResolution(this, 320, 240);
            }
        }

        @Override
        public void displayUnexpectedThrowable(UnexpectedThrowable error) {
            throw new AssertionError(error);
        }
    }

    private static final class FixtureWorld extends World {
        FixtureWorld() {
            super(null, "container_document_fixture", new WorldProvider() {
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
