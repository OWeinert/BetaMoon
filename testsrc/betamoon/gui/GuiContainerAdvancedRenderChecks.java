package betamoon.gui;

import betamoon.gui.GuiContainerDocumentRenderChecks.Client;
import betamoon.gui.GuiContainerDocumentRenderChecks.FixtureWorld;
import betamoon.gui.GuiContainerLifetimeRenderChecks.Keys;
import betamoon.gui.collection.GuiCollectionView;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.interop.GuiResourceBudget;
import betamoon.gui.inventory.GuiInventoryNativeTest;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.screen.GuiAttachedDocument;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.gui.screen.GuiScreenActivation;
import betamoon.gui.widget.GuiStyledView;
import betamoon.gui.widget.GuiTextAreaView;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.luaapi.client.ui.GuiUiApi;
import betamoon.luaapi.tileentity.TileEntityApi;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.GuiLuaContainer;
import betamoon.tileentity.LuaTileEntity;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.src.Block;
import net.minecraft.src.ItemStack;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.PlayerController;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Combined acceptance on the real native container adapter, with no game loop.
 */
final class GuiContainerAdvancedRenderChecks {
    private static final String OWNER = "container-advanced-fixture.lua";

    static void run(Minecraft parent, File output) throws Exception {
        Field installed = Minecraft.class.getDeclaredField("theMinecraft");
        installed.setAccessible(true);
        Object previous = installed.get(null);
        long capacity = GuiResourceBudget.allocated();
        Client client = new Client();
        client.gameSettings = parent.gameSettings;
        client.renderEngine = parent.renderEngine;
        client.fontRenderer = parent.fontRenderer;
        client.sndManager = parent.sndManager;
        ClickController clicks = new ClickController(client);
        client.playerController = clicks;
        FixtureWorld world = new FixtureWorld();
        client.theWorld = world;
        client.thePlayer = GuiInventoryNativeTest.player(world);
        client.thePlayer.username = "Advanced container fixture";
        GuiClientUiRuntime.tick(client);
        Globals lua = JsePlatform.standardGlobals();
        LuaTable api = new LuaTable();
        TileEntityApi.attach(api);
        GuiUiApi.attach(api, OWNER);
        lua.set("betamoon", api);
        GuiLuaContainer screen = null;
        try (Keys keys = new Keys()) {
            ContainerGuiDefinition definition;
            GuiContainerDocumentRenderChecks.owner(OWNER);
            try {
                String source = new String(Files.readAllBytes(Paths.get("testsrc/betamoon/gui/container-advanced.lua")),
                        StandardCharsets.UTF_8);
                definition = ((TileEntityApi.GuiHandle) lua.load(source, OWNER).call()).definition;
            } finally {
                GuiContainerDocumentRenderChecks.owner(null);
            }
            LuaTileEntity tile = new LuaTileEntity(definition.container.tileEntity.name);
            tile.worldObj = world;
            world.tile = tile;
            tile.setInventorySlotContents(0, new ItemStack(Block.stone.blockID, 10, 0));
            screen = open(client, tile, definition, 101);
            GuiAttachedDocument document = attached(screen);
            GuiScreenActivation activation = document.controller().current();
            require(activation != null && document.usable(), "Advanced container did not prepare");
            GuiCollectionView rows = (GuiCollectionView) element(document, activation, "rows");
            require(rows.collection().model().records.size() == 4096 && rows.collection().realizedRows() <= 5
                    && lua.get("rowCalls").toint() <= 5, "Native container did not virtualize its collection");
            GuiStyledView row = (GuiStyledView) rows.collection().rowElement("r1");
            click(screen, row);
            keys.send(screen, Keyboard.KEY_X, 'x', true);
            keys.send(screen, Keyboard.KEY_X, '\0', false);
            String draft = row.text();
            rows.collection().setScrollOffset(18000);
            prepare(screen);
            rows.collection().scrollToStart();
            prepare(screen);
            require(((GuiStyledView) rows.collection().rowElement("r1")).text().equals(draft) && draft.contains("x")
                    && rows.collection().realizedRows() <= 5,
                    "Virtualization discarded the shared container row editor draft");

            GuiElement first = element(document, activation, "first");
            click(screen, first);
            click(screen, first);
            click(screen, element(document, activation, "second"));
            GuiClientUiRuntime.dispatchClientActions(client);
            require(lua.get("left").toint() == 2 && lua.get("right").toint() == 1 && lua.get("starts").toint() == 3,
                    "Container component state/events or validated domain intents were not independent");
            GuiTextAreaView notes = (GuiTextAreaView) element(document, activation, "notes");
            click(screen, notes);
            keys.send(screen, Keyboard.KEY_X, 'x', true);
            keys.send(screen, Keyboard.KEY_X, '\0', false);
            String notesDraft = notes.text();
            prepare(screen);
            int prepared = lua.get("preparedCalls").toint();
            int templates = lua.get("templateCalls").toint();
            int rowCalls = lua.get("rowCalls").toint();
            int raw = lua.get("rawCalls").toint();
            for (int frame = 0; frame < 10; frame++) {
                GuiContainerDocumentRenderChecks.render(screen);
            }
            require(lua.get("preparedCalls").toint() == prepared && lua.get("templateCalls").toint() == templates
                    && lua.get("rowCalls").toint() == rowCalls && lua.get("rawCalls").toint() == raw + 10,
                    "Prepared container drawing entered Lua factories, or raw drawing did not execute explicitly");
            lua.load("skin('#486050')").call();
            prepare(screen);
            require(element(document, activation, "notes") instanceof GuiTextAreaView
                    && ((GuiTextAreaView) element(document, activation, "notes")).text().equals(notesDraft)
                    && ((Number) activation.state().get("visits")).intValue() == 3,
                    "Compatible container theme refresh lost editor or root state");
            click(screen, element(document, activation, "first"));
            require(lua.get("left").toint() == 3, "Theme refresh reset component-local state");
            GuiClientUiRuntime.dispatchClientActions(client);

            // The shared document leaves native integer slot geometry and gestures
            // authoritative.
            nativeClick(screen, 32 + 240, 4 + 126, 0);
            require(client.thePlayer.inventory.getItemStack().stackSize == 10 && tile.getStackInSlot(0) == null,
                    "Shared container masked native left-click pickup");
            nativeClick(screen, 32 + 16, 4 + 158, 1);
            require(client.thePlayer.inventory.getItemStack().stackSize == 9
                    && screen.inventorySlots.getSlot(1).getStack().stackSize == 1,
                    "Shared container changed native right-click split");
            nativeClick(screen, 32 + 240, 4 + 126, 0);
            keys.send(screen, Keyboard.KEY_LSHIFT, '\0', true);
            nativeClick(screen, 32 + 240, 4 + 126, 0);
            keys.send(screen, Keyboard.KEY_LSHIFT, '\0', false);
            require(client.thePlayer.inventory.getItemStack() == null && tile.getStackInSlot(0) == null
                    && inventoryCount(client) == 10 && clicks.count == 4
                    && screen.inventorySlots.getSlot(0).xDisplayPosition == 232,
                    "Shared container changed native shift transfer, slot geometry or gesture count");

            lua.load("openHelp=true").call();
            click(screen, element(document, activation, "first"));
            prepare(screen);
            GuiScreenActivation help = dialog(document);
            require(help != null && help.active(), "Container did not open its anchored popover");
            click(screen, tree(help).element(help.plan().elementId("nested")));
            prepare(screen);
            require(document.scene().hasModalLayer(), "Nested container popover did not share modal routing");
            keys.send(screen, Keyboard.KEY_ESCAPE, '\0', true);
            keys.send(screen, Keyboard.KEY_ESCAPE, '\0', false);
            prepare(screen);
            require(help.active(), "Escape closed the outer container popover before its child");
            require(document.scene().getFocusedElement() == tree(help).element(help.plan().elementId("nested")),
                    "Nested popover did not restore focus to its container opener");
            int nativeCount = clicks.count;
            nativeClick(screen, 32 + 240, 4 + 126, 0);
            prepare(screen);
            require(!help.active() && clicks.count == nativeCount,
                    "Outside popover gesture clicked through into the native inventory");
            require(within(document.scene().getFocusedElement(), element(document, activation, "first")),
                    "Outer popover did not restore container component focus");
            GuiShowcaseRenderTest.setupFrame();
            screen.drawScreen(-1, -1, 0);
            ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png",
                    new File(output, "container-shared-advanced.png"));

            lua.load("openHelp=false").call();
            click(screen, element(document, activation, "first"));
            int starts = lua.get("starts").toint();
            lua.load("fault=true").call();
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            GuiContainerDocumentRenderChecks.render(screen);
            require(!document.usable() && GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == program
                    && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == framebuffer
                    && GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) == framebuffer
                    && GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) == scissor && GL11.glGetError() == GL11.GL_NO_ERROR,
                    "Container raw failure retained input authority or leaked graphics state");
            prepare(screen);
            GuiClientUiRuntime.dispatchClientActions(client);
            require(lua.get("starts").toint() == starts, "Failed container document executed queued actions");
            require(activation.terminal(), "Failed container activation survived its update boundary");
            drainDisposals(capacity);
            require(GuiResourceBudget.allocated() == capacity, "Failed container document retained resources: "
                    + GuiResourceBudget.allocated() + " / " + capacity);
            require(client.currentScreen == screen, "Failed document closed the native backend");
            nativeClick(screen, 48, 162, 0);
            require(client.thePlayer.inventory.getItemStack() != null,
                    "Retiring a failed document disabled the surviving native container");
            nativeClick(screen, 48, 162, 0);
            screen.onGuiClosed();
            client.currentScreen = null;
            screen = null;
            lua.load("fault=false").call();
            screen = open(client, tile, definition, 102);
            document = attached(screen);
            activation = document.controller().current();
            require(((Number) activation.state().get("visits")).intValue() == 0,
                    "A second native container inherited the previous document state");
            click(screen, element(document, activation, "first"));
            require(lua.get("left").toint() == 1, "A second native container inherited component state");
        } finally {
            GuiContainerDocumentRenderChecks.owner(null);
            if (screen != null) {
                screen.onGuiClosed();
            }
            ScriptResourceTracker.unload(OWNER);
            drainDisposals(capacity);
            GuiClientUiRuntime.tick(parent);
            installed.set(null, previous);
        }
        require(GuiResourceBudget.allocated() == capacity, "Advanced container fixture retained native resources");
        System.out.println("Advanced container acceptance passed: components, prepared themes, 4096 keyed rows, "
                + "editors, nested popovers, raw resources/failure, independent instances and real native slot gestures.");
    }

    private static GuiLuaContainer open(Client client, LuaTileEntity tile, ContainerGuiDefinition definition,
            int window) throws Exception {
        GuiLuaContainer screen = new GuiLuaContainer(client.thePlayer.inventory, tile, definition);
        screen.inventorySlots.windowId = window;
        client.thePlayer.craftingInventory = screen.inventorySlots;
        client.displayGuiScreen(screen);
        prepare(screen);
        return screen;
    }

    private static int inventoryCount(Client client) {
        int count = 0;
        for (ItemStack stack : client.thePlayer.inventory.mainInventory) {
            if (stack != null) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    private static void drainDisposals(long baseline) {
        // Production disposal runs in bounded native client observation slices.
        for (int boundary = 0; boundary < 16 && GuiResourceBudget.allocated() != baseline; boundary++) {
            NativeDiscoveryRuntime.bindingsIfAvailable().clients.observe();
        }
    }

    private static void prepare(GuiLuaContainer screen) throws Exception {
        GuiContainerDocumentRenderChecks.update(screen);
        GuiContainerDocumentRenderChecks.render(screen);
    }

    private static GuiAttachedDocument attached(GuiLuaContainer screen) throws Exception {
        Field retained = GuiLuaContainer.class.getDeclaredField("retainedDocument");
        retained.setAccessible(true);
        Object host = retained.get(screen);
        require(host != null, "Container retained document was rejected");
        Field document = host.getClass().getDeclaredField("document");
        document.setAccessible(true);
        return (GuiAttachedDocument) document.get(host);
    }

    private static GuiElement element(GuiAttachedDocument document, GuiScreenActivation activation, String id) {
        return document.mount().document().element(activation.plan().elementId(id));
    }

    private static GuiPresentationTree tree(GuiScreenActivation activation) throws Exception {
        Field page = GuiScreenActivation.class.getDeclaredField("page");
        page.setAccessible(true);
        Object nativePage = page.get(activation);
        Method tree = nativePage.getClass().getDeclaredMethod("tree");
        tree.setAccessible(true);
        return (GuiPresentationTree) tree.invoke(nativePage);
    }

    private static GuiScreenActivation dialog(GuiAttachedDocument document) throws Exception {
        Field dialogs = document.controller().getClass().getDeclaredField("dialogs");
        dialogs.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Long, GuiScreenActivation> values = (Map<Long, GuiScreenActivation>) dialogs.get(document.controller());
        return values.isEmpty() ? null : values.values().iterator().next();
    }

    private static void click(GuiLuaContainer screen, GuiElement element) throws Exception {
        nativeClick(screen, element.getLeft() + element.getWidth() / 2, element.getTop() + element.getHeight() / 2, 0);
    }

    private static void nativeClick(GuiLuaContainer screen, int x, int y, int button) throws Exception {
        Method press = GuiLuaContainer.class.getDeclaredMethod("mouseClicked", int.class, int.class, int.class);
        Method release = GuiLuaContainer.class.getDeclaredMethod("mouseMovedOrUp", int.class, int.class, int.class);
        press.setAccessible(true);
        release.setAccessible(true);
        press.invoke(screen, x, y, button);
        release.invoke(screen, x, y, button);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static boolean within(GuiElement focused, GuiElement component) {
        for (GuiElement element = focused; element != null; element = element.getParent()) {
            if (element == component) {
                return true;
            }
        }
        return false;
    }

    private static final class ClickController extends PlayerController {
        int count;

        ClickController(Minecraft client) {
            super(client);
        }

        public ItemStack func_27174_a(int window, int slot, int button, boolean shift, EntityPlayer player) {
            count++;
            return super.func_27174_a(window, slot, button, shift, player);
        }
    }
}
