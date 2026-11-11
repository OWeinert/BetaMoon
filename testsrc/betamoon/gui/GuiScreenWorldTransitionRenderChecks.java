package betamoon.gui;

import betamoon.gui.interop.GuiResourceBudget;
import betamoon.gui.inventory.GuiInventoryNativeTest;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.gui.screen.GuiScreenActivation;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.luaapi.client.ui.GuiScreenParser;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiMainMenu;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.UnexpectedThrowable;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Real managed pages/resources in the pbuffer, with no native window, run loop
 * or game session.
 */
final class GuiScreenWorldTransitionRenderChecks {
    private static final String OWNER = "world-transition-render-fixture";

    static void run(Minecraft parent, File output) throws Exception {
        Field installed = Minecraft.class.getDeclaredField("theMinecraft");
        installed.setAccessible(true);
        Object previous = installed.get(null);
        long budget = GuiResourceBudget.allocated();
        int sessions = sessions();
        Client client = new Client();
        client.gameSettings = parent.gameSettings;
        client.renderEngine = parent.renderEngine;
        client.fontRenderer = parent.fontRenderer;
        client.currentScreen = new GuiMainMenu();
        Globals lua = JsePlatform.standardGlobals();
        try {
            GuiClientUiRuntime.tick(client);
            register(lua, "fixture:transition-menu",
                    "displayName='World entry',worldTransition={replace='fixture:transition-game',"
                            + "state={count=4},data={message='Welcome'}},state={count={type='integer',default=0}},"
                            + "on={close=function() staleClose=(staleClose or 0)+1 end},root={type='button',id='next',text='Menu'}");
            register(lua, "fixture:transition-game", "context='world',state={count={type='integer',default=0}},"
                    + "worldTransition={replace='fixture:transition-game',data={message='New environment'}},"
                    + "data={message={type='string',default=''}},on={open=function(ctx) opens=(opens or 0)+1;"
                    + "seenTime=ctx.world:getTime();seenMessage=ctx.data:get('message') end},"
                    + "root={type='button',id='next',bind=function(ctx) return {text=ctx.data:get('message')} end}");
            GuiScreenActivation source = GuiClientUiRuntime.open(OWNER, "fixture:transition-menu",
                    Collections.emptyMap(), Collections.emptyMap()).instance;
            require(source != null, "Worldless transition source was refused");
            GuiClientUiRuntime.tick(client);
            require(source.active() && sessions() == sessions + 1, "Managed source did not acquire one native session");
            source.state(Collections.singletonMap("count", 77));
            GuiScreen oldScreen = client.currentScreen;
            client.theWorld = new FixtureWorld("transition_a", 1234);
            client.thePlayer = GuiInventoryNativeTest.player(client.theWorld);
            GuiClientUiRuntime.tick(client);
            require(source.terminal() && GuiClientUiRuntime.current() == null && sessions() == sessions,
                    "Old managed resources remained active after root replacement");
            GuiClientUiRuntime.tick(client);
            GuiScreenActivation first = GuiClientUiRuntime.current();
            require(first != null && first != source && first.active() && client.currentScreen != oldScreen
                    && lua.get("seenTime").tolong() == 1234 && lua.get("seenMessage").tojstring().equals("Welcome")
                    && ((Number) first.state().get("count")).intValue() == 4 && lua.get("staleClose").isnil()
                    && sessions() == sessions + 1,
                    "World-entry policy did not prepare fresh context/resources and detached initialization");
            render(client);
            ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png",
                    new File(output, "screen-world-transition.png"));

            first.state(Collections.singletonMap("count", 99));
            client.theWorld = new FixtureWorld("transition_b", 9876);
            client.thePlayer = GuiInventoryNativeTest.player(client.theWorld);
            GuiClientUiRuntime.tick(client);
            GuiClientUiRuntime.tick(client);
            GuiScreenActivation second = GuiClientUiRuntime.current();
            require(second != null && second != first && first.terminal() && lua.get("seenTime").tolong() == 9876
                    && lua.get("seenMessage").tojstring().equals("New environment")
                    && ((Number) second.state().get("count")).intValue() == 0 && sessions() == sessions + 1,
                    "World replacement reused stale page state, roots or native resources");
            client.thePlayer = GuiInventoryNativeTest.player(client.theWorld);
            GuiClientUiRuntime.tick(client);
            GuiClientUiRuntime.tick(client);
            GuiScreenActivation third = GuiClientUiRuntime.current();
            require(third != null && third != second && second.terminal() && lua.get("opens").toint() == 3
                    && sessions() == sessions + 1, "Player replacement retained the old scoped page");
            GuiClientUiRuntime.tick(client);
            require(GuiClientUiRuntime.current() == third && lua.get("opens").toint() == 3,
                    "Stable environment repeated a self-key transition");

            client.thePlayer = GuiInventoryNativeTest.player(client.theWorld);
            GuiClientUiRuntime.tick(client);
            GuiScreen recovery = new GuiScreen() {
            };
            client.displayGuiScreen(recovery);
            GuiClientUiRuntime.tick(client);
            require(client.currentScreen == recovery && GuiClientUiRuntime.current() == null && sessions() == sessions,
                    "Pending transition displaced a native/protected replacement or retained resources");
        } finally {
            ScriptResourceTracker.unload(OWNER);
            GuiClientUiRuntime.tick(client);
            GuiClientUiRuntime.tick(parent);
            installed.set(null, previous);
        }
        require(sessions() == sessions && GuiResourceBudget.allocated() == budget,
                "World-transition fixture retained native sessions or resource capacity");
        System.out.println("Screen world transitions passed offscreen: worldless entry, fresh world/player roots, "
                + "detached state/data, exactly-once self replacement, native precedence and resource cleanup.");
    }

    private static void register(Globals lua, String key, String fields) {
        GuiClientUiRuntime.screens().add(GuiScreenParser.parse(OWNER,
                lua.load("return {key='" + key + "'," + fields + "}").call(), GuiClientUiRuntime.components()));
    }

    private static void render(Client client) {
        GuiShowcaseRenderTest.setupFrame();
        client.currentScreen.drawScreen(0, 0, 0);
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
        FixtureWorld(String name, long time) {
            super(null, name, new WorldProvider() {
            }, 0L);
            getWorldInfo().setWorldTime(time);
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
