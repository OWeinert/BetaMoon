package betamoon.gui;

import betamoon.client.control.input.ClientInputRuntime;
import betamoon.gui.GuiContainerDocumentRenderChecks.Client;
import betamoon.gui.GuiContainerDocumentRenderChecks.FixtureWorld;
import betamoon.gui.interop.GuiResourceBudget;
import betamoon.gui.inventory.GuiInventoryNativeTest;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.gui.screen.GuiScreenActivation;
import betamoon.luaapi.tileentity.TileEntityApi;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.GuiLuaContainer;
import betamoon.tileentity.LuaContainer;
import betamoon.tileentity.LuaTileEntity;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.Block;
import net.minecraft.src.Container;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.ItemStack;
import net.minecraft.src.PlayerController;
import net.minecraft.src.Slot;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.input.Keyboard;

/**
 * Native event bridge and one lifetime for both legacy and retained controls.
 */
final class GuiContainerLifetimeRenderChecks {
    private static final String OWNER = "container-lifetime-fixture.lua";

    static void run(Minecraft parent) throws Exception {
        Field installed = Minecraft.class.getDeclaredField("theMinecraft");
        installed.setAccessible(true);
        Object previous = installed.get(null);
        long capacity = GuiResourceBudget.allocated();
        Client client = new Client();
        client.gameSettings = parent.gameSettings;
        client.renderEngine = parent.renderEngine;
        client.fontRenderer = parent.fontRenderer;
        client.sndManager = parent.sndManager;
        CountingController controller = new CountingController(client);
        client.playerController = controller;
        FixtureWorld world = new FixtureWorld();
        EntityPlayerSP player = GuiInventoryNativeTest.player(world);
        Field playerClient = EntityPlayerSP.class.getDeclaredField("mc");
        playerClient.setAccessible(true);
        playerClient.set(player, client);
        player.username = "Container lifetime fixture";
        client.theWorld = world;
        client.thePlayer = player;
        GuiClientUiRuntime.tick(client);
        Globals lua = JsePlatform.standardGlobals();
        LuaTable api = new LuaTable();
        TileEntityApi.attach(api);
        lua.set("betamoon", api);
        GuiLuaContainer screen = null;
        try (Keys keys = new Keys()) {
            GuiContainerDocumentRenderChecks.owner(OWNER);
            ContainerGuiDefinition definition;
            try {
                definition = ((TileEntityApi.GuiHandle) lua.load("local t=betamoon.tileEntities:add{name='lifetime',"
                        + "data={writes={type='integer',default=0,sync=true}},"
                        + "inventory={slots={input={index=0}}}};"
                        + "local c=betamoon.containers:add{name='lifetime',tileEntity=t,"
                        + "slots={{slot='input',x=150,y=55}},playerInventory={x=8,y=84},"
                        + "state={label={type='string',default='old',maxLength=20}},controls={"
                        + "edit={type='text',bind={state='label'},maxLength=20,"
                        + "onCommit=function(ctx,value) commits=(commits or 0)+1;committed=value;"
                        + "ctx.data:set('writes',ctx.data:get('writes')+1) end},"
                        + "pulse={type='action',onActivate=function(ctx)ctx.data:set('writes',ctx.data:get('writes')+1)end}},"
                        + "onClose=function() closes=(closes or 0)+1 end};"
                        + "return betamoon.containerGuis:add{name='lifetime',container=c,events={'close'},"
                        + "onEvent=function() legacyClosed=(legacyClosed or 0)+1 end,"
                        + "elements={{type='text_box',control='edit',x=8,y=40,width=100},"
                        + "{type='button',control='pulse',x=8,y=8,width=40}},"
                        + "state={clicks={type='integer',default=0}},root={type='overlay',width=176,height=166,anchorX=0,anchorY=0,children={{type='button',id='launch',"
                        + "width=120,height=20,anchorX=0,anchorY=0,x=8,y=20,text='Launch',"
                        + "on={activate=function(ctx)ctx.state:set('clicks',ctx.state:get('clicks')+1);"
                        + "retainedWriteDenied=not pcall(function()ctx.world:setTime(999)end) end}}}}}")
                        .call()).definition;
            } finally {
                GuiContainerDocumentRenderChecks.owner(null);
            }
            LuaTileEntity tile = new LuaTileEntity(definition.container.tileEntity.name);
            tile.worldObj = world;
            world.tile = tile;
            screen = open(client, tile, definition, 91);
            GuiScreenActivation activation = GuiContainerDocumentRenderChecks.activation(screen);
            GuiContainerDocumentRenderChecks.click(screen, 84, 61);
            require(clicks(activation) == 1, "Pointer did not focus the retained keyboard activation fixture");
            keys.send(screen, Keyboard.KEY_RETURN, '\0', true);
            keys.send(screen, Keyboard.KEY_RETURN, '\0', true);
            require(clicks(activation) == 1 && ClientInputRuntime.router().hasGuiKey(Keyboard.KEY_RETURN),
                    "Retained keyboard press activated early or failed to claim its physical key");
            keys.send(screen, Keyboard.KEY_RETURN, '\0', false);
            keys.send(screen, Keyboard.KEY_RETURN, '\0', false);
            require(clicks(activation) == 2, "Native container did not forward exactly one retained key release");
            require(lua.get("retainedWriteDenied").toboolean(), "Retained input gained direct gameplay authority");
            GuiContainerDocumentRenderChecks.click(screen, 92, 50);
            require(tile.getDataInt("writes") == 1, "Legacy control callback lost gameplay mutation authority");
            edit(screen, keys);
            player.inventory.setItemStack(new ItemStack(Block.stone.blockID, 2, 0));
            screen.onGuiClosed();
            screen.onGuiClosed();
            require(lua.get("commits").toint() == 1 && lua.get("committed").tojstring().equals("xold")
                    && lua.get("closes").toint() == 1 && lua.get("legacyClosed").toint() == 1 && controller.closed == 1
                    && player.inventory.getItemStack() == null && tile.getDataInt("writes") == 2,
                    "Normal native close changed legacy commit/callback/cursor cleanup or was not idempotent");
            client.currentScreen = null;

            for (int closeKey : new int[]{Keyboard.KEY_ESCAPE, client.gameSettings.keyBindInventory.keyCode}) {
                lua.load("commits=0;closes=0;legacyClosed=0").call();
                screen = open(client, tile, definition, 93);
                GuiScreenActivation closing = GuiContainerDocumentRenderChecks.activation(screen);
                GuiContainerDocumentRenderChecks.click(screen, 84, 61);
                int writes = tile.getDataInt("writes");
                int closedWindows = controller.closed;
                keys.send(screen, closeKey, '\0', true);
                keys.send(screen, closeKey, '\0', false);
                require(client.currentScreen == null && closing.terminal()
                        && !((LuaContainer) screen.inventorySlots).controls().isActive()
                        && lua.get("closes").toint() == 1 && lua.get("legacyClosed").toint() == 1
                        && lua.get("commits").toint() == 0 && tile.getDataInt("writes") == writes
                        && controller.closed == closedWindows + 1,
                        "Native close key crashed, retained a lifetime or ran duplicate callbacks: " + closeKey);
            }
            require(!LuaScriptErrors.hasErrorFor(OWNER, OWNER), "Container input/close emitted a callback error");

            for (String change : new String[]{"player", "world", "unloaded_tile", "window", "slots", "death",
                    "distance"}) {
                client.theWorld = world;
                client.thePlayer = player;
                player.health = 20;
                player.isDead = false;
                player.posX = 0;
                world.tile = tile;
                lua.load("commits=0;closes=0;legacyClosed=0").call();
                screen = open(client, tile, definition, 92);
                GuiScreenActivation stale = GuiContainerDocumentRenderChecks.activation(screen);
                LuaContainer backend = (LuaContainer) screen.inventorySlots;
                edit(screen, keys);
                int lookups = world.tileLookups;
                final int[] successorCloses = {0};
                Container successor = new Container() {
                    public boolean isUsableByPlayer(EntityPlayer who) {
                        return true;
                    }

                    public void onCraftGuiClosed(EntityPlayer who) {
                        successorCloses[0]++;
                    }
                };
                if (change.equals("world") || change.equals("player")) {
                    if (change.equals("world")) {
                        client.theWorld = new FixtureWorld();
                    }
                    client.thePlayer = GuiInventoryNativeTest.player(client.theWorld);
                    client.thePlayer.craftingInventory = successor;
                } else if (change.equals("unloaded_tile")) {
                    world.tile = null;
                } else if (change.equals("window")) {
                    backend.windowId++;
                } else if (change.equals("slots")) {
                    backend.slots.add(new Slot(player.inventory, 0, 0, 0));
                } else if (change.equals("death")) {
                    player.health = 0;
                } else {
                    player.posX = 100;
                }
                screen.updateScreen();
                require(!backend.controls().isActive() && stale.terminal() && client.currentScreen == null
                        && lua.get("commits").toint() == 0 && lua.get("closes").toint() == 0
                        && lua.get("legacyClosed").toint() == 0,
                        "Expired " + change + " container ran old callbacks or retained shared interaction ownership");
                if (change.equals("world") || change.equals("player")) {
                    require(client.thePlayer.craftingInventory == successor && successorCloses[0] == 0,
                            "Old native close reached a replacement player's inventory");
                }
                if (change.equals("unloaded_tile") || change.equals("distance")) {
                    require(world.tileLookups == lookups, "Expired container queried an unloaded/distant tile");
                }
                GuiContainerDocumentRenderChecks.click(screen, 84, 61);
                require(client.currentScreen == null && lua.get("closes").toint() == 0,
                        "Stale physical input revived an expired container");
            }
        } finally {
            GuiContainerDocumentRenderChecks.owner(null);
            if (screen != null) {
                screen.onGuiClosed();
            }
            ScriptResourceTracker.unload(OWNER);
            GuiClientUiRuntime.tick(parent);
            installed.set(null, previous);
        }
        require(GuiResourceBudget.allocated() == capacity, "Container lifetime fixture retained native capacity");
        System.out.println("Container lifetime/event checks passed: actual key-up activation, GUI key claims, normal "
                + "legacy writes, isolated retained callbacks, native close keys/cursor semantics, seven stale native roots, "
                + "loaded-only checks and replacement inventory safety.");
    }

    private static GuiLuaContainer open(Client client, LuaTileEntity tile, ContainerGuiDefinition definition,
            int window) throws Exception {
        GuiLuaContainer screen = new GuiLuaContainer(client.thePlayer.inventory, tile, definition);
        screen.inventorySlots.windowId = window;
        client.thePlayer.craftingInventory = screen.inventorySlots;
        client.displayGuiScreen(screen);
        GuiContainerDocumentRenderChecks.render(screen);
        return screen;
    }

    private static int clicks(GuiScreenActivation activation) {
        return ((Number) activation.state().get("clicks")).intValue();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void edit(GuiLuaContainer screen, Keys keys) throws Exception {
        GuiContainerDocumentRenderChecks.click(screen, 82, 82);
        keys.send(screen, Keyboard.KEY_X, 'x', true);
        keys.send(screen, Keyboard.KEY_X, '\0', false);
    }

    private static final class CountingController extends PlayerController {
        int closed;

        CountingController(Minecraft client) {
            super(client);
        }

        public void func_20086_a(int window, EntityPlayer player) {
            closed++;
            super.func_20086_a(window, player);
        }
    }

    /** Process-local LWJGL event data; never creates a hardware keyboard/window. */
    static final class Keys implements AutoCloseable {
        final Field created;
        final boolean wasCreated;
        final Object event;
        final Map<Field, Object> saved = new LinkedHashMap<>();
        final ByteBuffer down;
        final Map<Integer, Byte> previous = new LinkedHashMap<>();

        Keys() throws Exception {
            created = Keyboard.class.getDeclaredField("created");
            created.setAccessible(true);
            wasCreated = created.getBoolean(null);
            Field current = Keyboard.class.getDeclaredField("current_event");
            current.setAccessible(true);
            event = current.get(null);
            for (String name : new String[]{"key", "character", "state", "repeat"}) {
                Field field = event.getClass().getDeclaredField(name);
                field.setAccessible(true);
                saved.put(field, field.get(event));
            }
            Field buffer = Keyboard.class.getDeclaredField("keyDownBuffer");
            buffer.setAccessible(true);
            down = (ByteBuffer) buffer.get(null);
            created.setBoolean(null, true);
        }

        void send(GuiLuaContainer screen, int key, char character, boolean pressed) throws Exception {
            for (Field field : saved.keySet()) {
                Object value = field.getName().equals("key")
                        ? key
                        : field.getName().equals("character")
                                ? (int) character
                                : field.getName().equals("state") ? pressed : false;
                field.set(event, value);
            }
            previous.putIfAbsent(key, down.get(key));
            down.put(key, (byte) (pressed ? 1 : 0));
            screen.handleKeyboardInput();
        }

        public void close() throws Exception {
            for (Map.Entry<Field, Object> field : saved.entrySet()) {
                field.getKey().set(event, field.getValue());
            }
            for (Map.Entry<Integer, Byte> key : previous.entrySet()) {
                down.put(key.getKey(), key.getValue());
            }
            created.setBoolean(null, wasCreated);
        }
    }
}
