package betamoon.gui;

import betamoon.gui.GuiContainerDocumentRenderChecks.Client;
import betamoon.gui.GuiContainerDocumentRenderChecks.FixtureWorld;
import betamoon.gui.inventory.GuiInventoryNativeTest;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.luaapi.tileentity.TileEntityApi;
import betamoon.luaapi.client.ui.GuiScreenLuaAccess;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.GuiLuaContainer;
import betamoon.tileentity.LuaContainer;
import betamoon.tileentity.LuaTileEntity;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.PlayerController;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Observations and named domain requests through actual shared container
 * callbacks.
 */
final class GuiContainerIntentRenderChecks {
    private static final String OWNER = "container-intent-fixture.lua";

    static void run(Minecraft parent) throws Exception {
        Field installed = Minecraft.class.getDeclaredField("theMinecraft");
        installed.setAccessible(true);
        Object previous = installed.get(null);
        Client client = new Client();
        client.gameSettings = parent.gameSettings;
        client.renderEngine = parent.renderEngine;
        client.fontRenderer = parent.fontRenderer;
        client.sndManager = parent.sndManager;
        client.playerController = new PlayerController(client);
        FixtureWorld world = new FixtureWorld();
        client.theWorld = world;
        client.thePlayer = GuiInventoryNativeTest.player(world);
        client.thePlayer.username = "Container UI fixture";
        GuiClientUiRuntime.tick(client);
        Globals lua = JsePlatform.standardGlobals();
        LuaTable api = new LuaTable();
        TileEntityApi.attach(api);
        lua.set("betamoon", api);
        GuiLuaContainer screen = null;
        try {
            GuiContainerDocumentRenderChecks.owner(OWNER);
            ContainerGuiDefinition definition;
            try {
                definition = ((TileEntityApi.GuiHandle) lua.load("local t=betamoon.tileEntities:add{name='intents',"
                        + "data={synced={type='integer',default=7,sync=true},"
                        + "secret={type='string',default='hidden'},other={type='integer',default=42}}};"
                        + "local c=betamoon.containers:add{name='intents',tileEntity=t,slots={},"
                        + "playerInventory={x=8,y=84},state={branch={type='boolean',default=true},"
                        + "locked={type='boolean',default=false},number={type='integer',default=0},"
                        + "label={type='string',default='old',maxLength=20},mode={type='string',default='a'},"
                        + "enabled={type='boolean',default=false}},controls={"
                        + "action={type='action',enabledWhen={state='locked',equals=false},onActivate=function(ctx) "
                        + "actions=(actions or 0)+1;ctx.data:set('synced',ctx.data:get('synced')+1) end},"
                        + "number={type='number',bind={state='number'},minimum=0,maximum=10,step=1,"
                        + "beforeChange=function(ctx,v) if v==9 then deniedNested=nestedScreen:request('client');return 'deny' end return 'pass' end},"
                        + "text={type='text',bind={state='label'},maxLength=20},"
                        + "choice={type='choice',bind={state='mode'},values={'a','b'}},"
                        + "toggle={type='toggle',bind={state='enabled'}},"
                        + "secretText={type='text',bind={data='secret'},maxLength=20},"
                        + "custom={type='custom',onInput=function(ctx,input) source=input.source;dx=input.deltaX;return 'handled' end},"
                        + "fault={type='custom',onInput=function() nested=nestedScreen:request('client');error('intent failure') end}}};"
                        + "return betamoon.containerGuis:add{name='intents',container=c,"
                        + "state={count={type='integer',default=0}},actions={"
                        + "client={scope='client',handler=function() clientRan=(clientRan or 0)+1 end},"
                        + "forward={scope='client',handler=function(ctx) generalWrite=pcall(function()ctx.world:setTime(999)end);"
                        + "forwarded=ctx.container:request('action') end}},"
                        + "on={update=function(ctx) layout=ctx.container:layout(); schema=ctx.container:schema();"
                        + "observed=ctx.container:control('number');saved=ctx.container;oldLayout=oldLayout or layout;"
                        + "secretReadable=pcall(function()ctx.container:data('secret')end);"
                        + "secretValue=ctx.container:control('secretText').value end},"
                        + "root={type='button',width=120,height=20,anchorX=0,anchorY=0,x=8,y=20,"
                        + "bind=function(ctx) binds=(binds or 0)+1;boundLayout=ctx.container:layout();"
                        + "if bindingRequest then bindingDenied=not pcall(function()ctx.container:request('action')end) end;"
                        + "local v=ctx.container:session('branch') and ctx.container:data('synced') or ctx.container:session('number');"
                        + "return{text='Value '..v..' / '..ctx.state:get('count')} end,"
                        + "on={activate=function(ctx) ctx.state:set('count',ctx.state:get('count')+1);"
                        + "if burst then tickets={};for i=1,8 do tickets[#tickets+1]=ctx.screen:request('client') end;"
                        + "for i=1,8 do tickets[#tickets+1]=ctx.container:request('action') end;"
                        + "overflow,overflowReason=ctx.container:request('action');"
                        + "elseif forward then ticket=ctx.screen:request('forward');"
                        + "elseif command then local ok=pcall(function()ticket,reason=ctx.container:request(command,options)end);"
                        + "requestValid=ok end end}}}").call()).definition;
            } finally {
                GuiContainerDocumentRenderChecks.owner(null);
            }
            LuaTileEntity tile = new LuaTileEntity(definition.container.tileEntity.name);
            tile.worldObj = world;
            world.tile = tile;
            screen = new GuiLuaContainer(client.thePlayer.inventory, tile, definition);
            screen.inventorySlots.windowId = 81;
            client.thePlayer.craftingInventory = screen.inventorySlots;
            client.displayGuiScreen(screen);
            lua.set("nestedScreen",
                    GuiScreenLuaAccess.instance(OWNER, GuiContainerDocumentRenderChecks.activation(screen), null,
                            GuiClientUiRuntime.screens()::getRequired));
            update(screen);
            update(screen);
            GuiContainerDocumentRenderChecks.render(screen);
            require(lua.get("boundLayout") == lua.get("layout") && !lua.get("secretReadable").toboolean()
                    && lua.get("secretValue").isnil(),
                    "Container observations copied incoherently or exposed unsynchronized data");
            require(!lua.load("return pcall(function()saved:layout()end)").call().toboolean()
                    && !lua.load("return pcall(function()oldLayout.panel.width=1 end)").call().toboolean(),
                    "Scoped container facade survived its callback or published mutable geometry");
            LuaContainer backend = (LuaContainer) screen.inventorySlots;
            int binds = lua.get("binds").toint();
            update(screen);
            backend.controls().session().set("number", 2);
            tile.setSyncedData("synced", 7);
            update(screen);
            require(lua.get("binds").toint() == binds, "Idle/equal/unread container writes reevaluated a binding");
            tile.setSyncedData("synced", 8);
            update(screen);
            require(lua.get("binds").toint() == binds + 1, "Observed synchronized data did not notify its binding");
            backend.controls().session().set("branch", false);
            update(screen);
            binds = lua.get("binds").toint();
            tile.setSyncedData("synced", 9);
            update(screen);
            require(lua.get("binds").toint() == binds,
                    "Conditional source replacement retained the old data dependency");
            backend.controls().session().set("number", 3);
            update(screen);
            require(lua.get("binds").toint() == binds + 1, "Conditional session source did not become observed");
            lua.set("bindingRequest", LuaValue.TRUE);
            backend.controls().session().set("number", 4);
            update(screen);
            require(lua.get("bindingDenied").toboolean() && GuiClientUiRuntime.actions().pending() == 0,
                    "Read-only binding admitted an authoritative control intent");

            client.isGamePaused = true;
            request(lua, screen, "number", "{value=5.7}");
            require(status(lua).equals("queued")
                    && ((Number) backend.controls().session().get("number")).intValue() == 4,
                    "Control request executed inside its invoking callback");
            GuiClientUiRuntime.worldTick(client, world);
            require(status(lua).equals("queued"), "Named control intent used the world scheduler");
            drain(client);
            require(status(lua).equals("succeeded") && result(lua, "accepted").toboolean()
                    && ((Number) backend.controls().session().get("number")).intValue() == 6,
                    "Paused client-boundary control request bypassed existing number normalization");
            request(lua, screen, "number", "{value=9}");
            drain(client);
            require(!result(lua, "accepted").toboolean() && result(lua, "value").toint() == 6,
                    "Control denial was reported as an accepted mutation");
            require(lua.load("return deniedNested:status()").call().tojstring().equals("cancelled")
                    && lua.get("clientRan").isnil(), "Denied domain callback leaked nested UI work");
            request(lua, screen, "text", "{value='updated'}");
            drain(client);
            require(backend.controls().session().get("label").equals("updated"),
                    "Text intent missed the commit backend");
            request(lua, screen, "secretText", "{value='changed hidden'}");
            drain(client);
            require(result(lua, "value").isnil() && tile.getDataValue("secret").equals("changed hidden"),
                    "Command result exposed an unsynchronized tile value or skipped domain commit");
            request(lua, screen, "choice", "{direction=1}");
            drain(client);
            require(backend.controls().session().get("mode").equals("b"), "Choice intent missed the cycle backend");
            request(lua, screen, "toggle", "nil");
            drain(client);
            require(Boolean.TRUE.equals(backend.controls().session().get("enabled")),
                    "Toggle intent missed native session state");
            request(lua, screen, "custom", "{input={phase='drag',deltaX=3}}");
            drain(client);
            require(lua.get("source").tojstring().equals("ui") && lua.get("dx").toint() == 3,
                    "Custom request forged a native input source or lost detached input");
            request(lua, screen, "custom", "{input={source='mouse'}}");
            require(!lua.get("requestValid").toboolean(), "Custom request accepted a forged native source");
            request(lua, screen, "number", "{value=0/0}");
            require(!lua.get("requestValid").toboolean(), "Non-finite command payload was admitted");
            request(lua, screen, "missing", "nil");
            require(lua.get("ticket").isnil() && lua.get("reason").tojstring().equals("unknown_control"),
                    "Unknown named control was accepted");
            backend.controls().session().set("locked", true);
            request(lua, screen, "action", "nil");
            require(lua.get("ticket").isnil() && lua.get("reason").tojstring().equals("control_disabled"),
                    "Disabled control was admitted");
            backend.controls().session().set("locked", false);
            client.isGamePaused = false;
            lua.load("command=nil;forward=true").call();
            click(screen);
            GuiClientUiRuntime.dispatchClientActions(client);
            require(!lua.get("generalWrite").toboolean() && lua.get("actions").isnil(),
                    "Client handler gained general world authority or dispatched a nested intent synchronously");
            drain(client);
            require(lua.get("actions").toint() == 1,
                    "Client callback could not submit a finite validated domain intent");
            lua.load("forward=false;burst=true").call();
            click(screen);
            require(lua.get("tickets").length() == 16 && lua.get("overflow").isnil()
                    && lua.get("overflowReason").tojstring().equals("action_queue_full"),
                    "Backend intent wrappers multiplied the originating activation's shared quota");
            drain(client);
            require(lua.get("clientRan").toint() == 8 && lua.get("actions").toint() == 9,
                    "Mixed declared/domain requests did not share the existing fair dispatcher");
            lua.set("burst", LuaValue.FALSE);
            request(lua, screen, "fault", "nil");
            drain(client);
            require(!result(lua, "accepted").toboolean()
                    && lua.load("return nested:status()").call().tojstring().equals("cancelled")
                    && lua.get("clientRan").toint() == 8, "Failing domain callback leaked nested deferred UI actions");

            request(lua, screen, "action", "nil");
            backend.windowId++;
            drain(client);
            require(!status(lua).equals("succeeded") && lua.get("actions").toint() == 9,
                    "Pending request reached a different native window");
            backend.windowId--;
            request(lua, screen, "action", "nil");
            world.tile = null;
            drain(client);
            require(!status(lua).equals("succeeded") && lua.get("actions").toint() == 9,
                    "Pending request reached a removed/replaced tile");
            world.tile = tile;
            request(lua, screen, "action", "nil");
            EntityPlayerSP player = client.thePlayer;
            client.thePlayer = GuiInventoryNativeTest.player(world);
            client.thePlayer.craftingInventory = backend;
            drain(client);
            require(!status(lua).equals("succeeded") && lua.get("actions").toint() == 9,
                    "Pending request survived native player replacement");
            client.thePlayer = player;
            request(lua, screen, "action", "nil");
            screen.onGuiClosed();
            screen = null;
            client.currentScreen = null;
            drain(client);
            require(status(lua).equals("cancelled") && lua.get("actions").toint() == 9,
                    "Native close retained a pending domain control request");
            screen = new GuiLuaContainer(client.thePlayer.inventory, tile, definition);
            screen.inventorySlots.windowId = 82;
            client.thePlayer.craftingInventory = screen.inventorySlots;
            client.displayGuiScreen(screen);
            request(lua, screen, "action", "nil");
            ScriptResourceTracker.unload(OWNER);
            drain(client);
            require(status(lua).equals("cancelled") && lua.get("actions").toint() == 9,
                    "Owner unload retained a pending named-control request");
        } finally {
            GuiContainerDocumentRenderChecks.owner(null);
            if (screen != null) {
                screen.onGuiClosed();
            }
            ScriptResourceTracker.unload(OWNER);
            GuiClientUiRuntime.tick(parent);
            installed.set(null, previous);
        }
        require(GuiClientUiRuntime.actions().pending() == 0, "Container intent fixture retained queued work");
        System.out.println("Container intent/source checks passed: detached observed scopes, conditional/equal writes, "
                + "paused backend dispatch, shared quotas, domain validation, callback outcomes and native stale-root cleanup.");
    }

    private static void request(Globals lua, GuiLuaContainer screen, String name, String options) throws Exception {
        lua.load("command='" + name + "'; options=" + options + ";ticket=nil;reason=nil").call();
        click(screen);
    }

    private static void click(GuiLuaContainer screen) throws Exception {
        GuiContainerDocumentRenderChecks.render(screen);
        GuiContainerDocumentRenderChecks.click(screen, 84, 61);
    }

    private static void update(GuiLuaContainer screen) throws Exception {
        GuiContainerDocumentRenderChecks.update(screen);
    }

    private static void drain(Client client) {
        for (int index = 0; index < 64 && GuiClientUiRuntime.actions().pending() > 0; index++) {
            GuiClientUiRuntime.dispatchClientActions(client);
        }
        require(GuiClientUiRuntime.actions().pending() == 0, "Shared queue did not drain within the bounded fixture");
    }

    private static String status(Globals lua) {
        return lua.load("return ticket:status()").call().tojstring();
    }

    private static LuaValue result(Globals lua, String field) {
        return lua.load("return ticket:result()." + field).call();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
