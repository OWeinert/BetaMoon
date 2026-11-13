package betamoon.gui;

import betamoon.gui.hud.GuiHudActivation;
import betamoon.gui.hud.GuiHudNativePart;
import betamoon.gui.hud.GuiHudRuntime;
import betamoon.instrumentation.hooks.gui.GuiHudCallbacks;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.gui.screen.GuiActionQueue;
import betamoon.gui.screen.GuiScreenActivation;
import betamoon.gui.framework.GuiRenderer;
import betamoon.gui.framework.GuiTheme;
import betamoon.client.control.input.ClientInputRuntime;
import betamoon.client.control.input.InputDeviceEvent;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.luaapi.client.ui.GuiComponentParser;
import betamoon.luaapi.client.ui.GuiHudParser;
import betamoon.luaapi.client.ui.GuiScreenParser;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.GuiMainMenu;
import net.minecraft.src.GuiIngameMenu;
import net.minecraft.src.GuiOptions;
import net.minecraft.src.GuiControls;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.World;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.input.Keyboard;

/**
 * Shared HUD runtime in a fake client; native seams are invoked directly, with
 * no game process.
 */
final class GuiHudRuntimeRenderChecks {
    private static final String OWNER = "hud-runtime-render-fixture";

    static void run(Minecraft client, File output) throws Exception {
        GuiScreen previousScreen = client.currentScreen;
        World previousWorld = client.theWorld;
        EntityPlayerSP previousPlayer = client.thePlayer;
        int previousHealth = previousPlayer.health;
        boolean previousHidden = client.gameSettings.hideGUI;
        try {
            client.currentScreen = null;
            Globals lua = JsePlatform.standardGlobals();
            component(lua, "teal", "#00DD88");
            component(lua, "purple", "#AA55EE");
            screen(lua, "fixture:reticle-a", "root={component='fixture:teal'}");
            screen(lua, "fixture:reticle-b", "root={component='fixture:purple'}");
            screen(lua, "fixture:hud-status",
                    "root={type='button',width=120,height=20,anchorX=0,anchorY=0,x=8,y=8,"
                            + "bind=function(ctx) local view=ctx.hud:snapshot(); seenHealth=view.player.health; "
                            + "return {text='Health '..tostring(seenHealth)} end}");
            GuiHudActivation first = activate(lua, "fixture:status", "screen='fixture:hud-status',safeArea=false,"
                    + "nativeParts={crosshair={replace='fixture:reticle-a'},health={hide=true}}");
            GuiHudRuntime.tick(client);
            frame(client, true, true);
            require(first.status().equals("active") && first.instance() != null
                    && lua.get("seenHealth").toint() == previousHealth && pixel() == 0xFF00DD88,
                    "Passive additive HUD, read-only observation or prepared native replacement did not render");
            Object retained = first.instance();
            require(partStatus(first, "crosshair").equals("replaced") && partStatus(first, "health").equals("hidden"),
                    "Per-part diagnostics did not distinguish prepared replacement from independent hiding");
            ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png",
                    new File(output, "hud-native-replacement.png"));
            GuiHudActivation second = activate(lua, "fixture:priority",
                    "priority=1," + "nativeParts={crosshair={replace='fixture:reticle-b'}}");
            client.thePlayer.health = 17;
            GuiHudRuntime.tick(client);
            frame(client, true, true);
            require(second.status().equals("active") && first.status().equals("active") && first.instance() == retained
                    && pixel() == 0xFFAA55EE && lua.get("seenHealth").toint() == 17,
                    "Replacement priority or additive retention failed: first=" + first.status() + "/" + first.reason()
                            + ", second=" + second.status() + "/" + second.reason() + ", retained="
                            + (first.instance() == retained) + ", pixel=" + Integer.toHexString(pixel())
                            + ", observedHealth=" + lua.get("seenHealth"));
            require(partStatus(first, "crosshair").equals("covered")
                    && partStatus(second, "crosshair").equals("replaced"),
                    "Covered replacement diagnostics advertised native ownership");
            second.close();
            frame(client, false, true);
            require(pixel() == 0xFFCC3333, "Closed winner suppressed native fallback before the next logical boundary");
            GuiHudRuntime.tick(client);
            frame(client, true, true);
            require(pixel() == 0xFF00DD88 && first.instance() == retained,
                    "Next eligible replacement did not resume its retained document");

            GuiClientUiRuntime.components()
                    .add(GuiComponentParser
                            .parse(OWNER,
                                    lua.load("return {key='fixture:bad-reticle',size={width=12,height=12},"
                                            + "rawDraw=function() error('expected HUD raw render failure') end}")
                                            .call()));
            screen(lua, "fixture:reticle-failed", "root={component='fixture:bad-reticle'},"
                    + "actions={write={scope='world',handler=function() worldWrites=(worldWrites or 0)+1 end}}");
            GuiHudActivation broken = activate(lua, "fixture:broken",
                    "priority=2," + "nativeParts={crosshair={replace='fixture:reticle-failed'}}");
            GuiHudRuntime.tick(client);
            GuiScreenActivation failedSource = GuiHudRuntime.instance(broken, GuiHudNativePart.CROSSHAIR);
            require(failedSource != null, "Prepared replacement instance was unavailable for owned data/actions");
            GuiActionQueue.Submission queued = failedSource.request("write", Collections.emptyMap());
            require(queued.ticket != null, "Prepared replacement action was not admitted");
            frame(client, false, true);
            require(!broken.status().equals("active") && pixel() == 0xFFCC3333,
                    "Immediately failing raw replacement removed the native part");
            GuiClientUiRuntime.tick(client);
            GuiClientUiRuntime.worldTick(client, client.theWorld);
            require(lua.get("worldWrites").isnil(), "Failed replacement dispatched an already queued world action");
            GuiHudRuntime.tick(client);
            require(broken.status().equals("refused"),
                    "Raw rendering failure was not reported at the next logical boundary");
            require(!queued.ticket.status().equals("queued") && lua.get("worldWrites").isnil(),
                    "Failed replacement retained an executable world intent after logical disposal");
            broken.close();
            GuiHudRuntime.tick(client);
            frame(client, true, true);
            client.gameSettings.hideGUI = true;
            frame(client, false, false);
            require(first.status().equals("suspended"), "Default HUD ignored native hide-GUI policy");
            client.gameSettings.hideGUI = false;
            client.thePlayer.health = 0;
            frame(client, false, false);
            client.thePlayer.health = previousHealth;
            GuiHudRuntime.tick(client);
            frame(client, true, true);
            int displayWidth = client.displayWidth;
            client.displayWidth++;
            frame(client, false, false);
            client.displayWidth = displayWidth;
            GuiHudRuntime.tick(client);
            frame(client, true, true);
            allNativeParts(client, lua, first);
            menuRegression(client, lua, output);

            screen(lua, "fixture:menu-hud", "root={type='button',width=120,height=20,text='Worldless notice'}");
            GuiHudActivation menu = activate(lua, "fixture:menu-notice",
                    "screen='fixture:menu-hud'," + "visibility={contexts={'menu'}}");
            client.currentScreen = new GuiMainMenu();
            client.theWorld = null;
            client.thePlayer = null;
            GuiHudRuntime.tick(client);
            GuiShowcaseRenderTest.setupFrame();
            GuiHudRuntime.clientOverlay(client, 0, 0, 0);
            require(menu.status().equals("active") && first.status().equals("closed"),
                    "Explicit worldless menu HUD did not compose with suspended gameplay claims");
            ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png", new File(output, "hud-menu-overlay.png"));
            menuInteraction(client, lua);
            ScriptResourceTracker.unload(OWNER);
            GuiHudRuntime.tick(client);
            require(((Number) NativeDiscoveryRuntime.bindingsIfAvailable().clients.diagnostics().get("sessions"))
                    .intValue() == 0, "Last HUD owner retained native resource sessions");
        } finally {
            GuiHudRuntime.close();
            ScriptResourceTracker.unload(OWNER);
            client.currentScreen = previousScreen;
            client.theWorld = previousWorld;
            client.thePlayer = previousPlayer;
            previousPlayer.health = previousHealth;
            client.gameSettings.hideGUI = previousHidden;
        }
        System.out.println("Offscreen HUD runtime passed: composing status/reticle documents, read-only snapshots, "
                + "priority/close/raw-fault native fallback, hide-GUI/death/resize, menu visibility and cleanup.");
    }

    private static String partStatus(GuiHudActivation claim, String part) {
        return ((Map<?, ?>) GuiHudRuntime.parts(claim).get(part)).get("status").toString();
    }

    private static void menuRegression(Minecraft client, Globals lua, File output) throws Exception {
        GuiHudActivation nativeClaim = activate(lua, "fixture:menu-native", "nativeParts={health={hide=true}}");
        GuiHudRuntime.tick(client);
        GuiIngameMenu pause = new GuiIngameMenu();
        client.currentScreen = pause;
        pause.setWorldAndResolution(client, 320, 240);
        screen(lua, "fixture:menu-color", "root={component='fixture:teal'}");
        GuiHudActivation additive = activate(lua, "fixture:menu-color-hud",
                "screen='fixture:menu-color',safeArea=false,visibility={contexts={'gameplay','pause'}}");
        GuiHudRuntime.tick(client);
        GuiShowcaseRenderTest.setupFrame();
        GuiHudRuntime.beginNative(client, 0, 0, 0);
        boolean hidden = GuiHudRuntime.nativePart(GuiHudNativePart.HEALTH);
        GuiHudCallbacks.afterNative();
        pause.drawDefaultBackground();
        GuiHudRuntime.clientOverlay(client, 0, 0, 0);
        int color = pixel();
        ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png", new File(output, "hud-pause-regression.png"));
        System.out.println("HUD pause regression: claimRequested=" + nativeClaim.requested() + ", hidden=" + hidden
                + ", pixel=" + Integer.toHexString(color));
        require(nativeClaim.requested() && hidden && color != 0xFF00DD88 && (color & 0x00FFFFFF) != 0,
                "Player HUD must retain its native claim and paint below the real pause-menu gradient");
        GuiHudActivation strict = activate(lua, "fixture:menu-strict",
                "nativeParts={armor={hide=true}},visibility={behindMenus=false}");
        GuiHudActivation foreground = activate(lua, "fixture:menu-foreground",
                "screen='fixture:menu-color',layer='overlay',safeArea=false,visibility={contexts={'pause'}}");
        GuiHudRuntime.tick(client);
        GuiShowcaseRenderTest.setupFrame();
        GuiHudRuntime.beginNative(client, 0, 0, 0);
        require(GuiHudRuntime.nativePart(GuiHudNativePart.HEALTH) && !GuiHudRuntime.nativePart(GuiHudNativePart.ARMOR),
                "Background inheritance ignored explicit opt-out");
        GuiHudCallbacks.afterNative();
        pause.drawDefaultBackground();
        GuiHudRuntime.clientOverlay(client, 0, 0, 0);
        require(pixel() == 0xFF00DD88 && foreground.status().equals("active"),
                "Explicit foreground HUD did not remain above the native menu");
        strict.close();
        foreground.close();
        for (GuiScreen next : new GuiScreen[]{new GuiOptions(pause, client.gameSettings),
                new GuiControls(pause, client.gameSettings), pause}) {
            client.currentScreen = next;
            next.setWorldAndResolution(client, 320, 240);
            GuiHudRuntime.tick(client);
            GuiShowcaseRenderTest.setupFrame();
            GuiHudRuntime.beginNative(client, 0, 0, 0);
            require(nativeClaim.requested() && GuiHudRuntime.nativePart(GuiHudNativePart.HEALTH),
                    "Player HUD native claim did not survive the pause/options/controls chain");
            GuiHudCallbacks.afterNative();
            next.drawDefaultBackground();
            GuiHudRuntime.clientOverlay(client, 0, 0, 0);
            require(pixel() == color && !GuiHudRuntime.inputAvailable(additive),
                    "Background HUD lost native dimming or acquired protected menu input");
        }
        nativeClaim.close();
        additive.close();
        client.currentScreen = null;
        GuiHudRuntime.tick(client);
    }

    private static void allNativeParts(Minecraft client, Globals lua, GuiHudActivation first) {
        first.close();
        GuiHudRuntime.tick(client);
        nativePartsFrame(client, false);
        StringBuilder hidden = new StringBuilder("nativeParts={");
        StringBuilder replaced = new StringBuilder("nativeParts={");
        for (GuiHudNativePart part : GuiHudNativePart.values()) {
            hidden.append(part.name).append("={hide=true},");
            replaced.append(part.name).append("={replace='fixture:reticle-a'},");
        }
        hidden.append('}');
        replaced.append('}');
        GuiHudActivation one = activate(lua, "fixture:hide-all-one", hidden.toString());
        GuiHudActivation two = activate(lua, "fixture:hide-all-two", hidden.toString());
        GuiHudRuntime.tick(client);
        nativePartsFrame(client, true);
        for (GuiHudNativePart part : GuiHudNativePart.values()) {
            require(partStatus(one, part.name).equals("hidden") && partStatus(two, part.name).equals("hidden"),
                    "Composing native hide owners did not report independent applied claims: " + part.name);
        }
        one.close();
        GuiHudRuntime.tick(client);
        nativePartsFrame(client, true);
        two.close();
        nativePartsFrame(client, false);
        GuiHudRuntime.tick(client);
        GuiHudActivation replacement = activate(lua, "fixture:replace-all", replaced.toString());
        GuiHudRuntime.tick(client);
        nativePartsFrame(client, true);
        for (GuiHudNativePart part : GuiHudNativePart.values()) {
            require(partStatus(replacement, part.name).equals("replaced"),
                    "Prepared native part did not independently paint and report readiness: " + part.name);
        }
        replacement.close();
        nativePartsFrame(client, false);
        GuiHudRuntime.tick(client);
    }

    private static void nativePartsFrame(Minecraft client, boolean claimed) {
        GuiShowcaseRenderTest.setupFrame();
        GuiHudRuntime.beginNative(client, 0, 0, 0);
        for (GuiHudNativePart part : GuiHudNativePart.values()) {
            require(GuiHudRuntime.nativePart(part) == claimed,
                    "Native hide/replacement/last-owner fallback differed for " + part.name);
        }
        GuiHudRuntime.endNative();
        GuiHudRuntime.clientOverlay(client, 0, 0, 0);
    }

    private static void component(Globals lua, String key, String color) {
        GuiClientUiRuntime.components()
                .add(GuiComponentParser.parse(OWNER,
                        lua.load("return {key='fixture:" + key + "',size={width=12,height=12},paint=function(view) "
                                + "return {{op='rect',width=12,height=12,color='" + color + "'}} end}").call()));
    }

    private static void menuInteraction(Minecraft client, Globals lua) {
        screen(lua, "fixture:regional-hud",
                "root={type='row',width=248,height=20,anchorX=0,anchorY=0,x=8,y=60,"
                        + "children={{type='button',id='allowed',width=120,height=20,text='Allowed',"
                        + "on={activate=function() allowedClicks=(allowedClicks or 0)+1 end}},"
                        + "{type='button',id='excluded',width=120,height=20,text='Excluded',"
                        + "on={activate=function() excludedClicks=(excludedClicks or 0)+1 end}}}}");
        screen(lua, "fixture:regional-front", "root={type='button',id='front',width=120,height=20,anchorX=0,"
                + "anchorY=0,x=8,y=60,text='Front',on={activate=function() frontClicks=(frontClicks or 0)+1 end}}");
        GuiHudActivation lower = activate(lua, "fixture:regional-lower", "screen='fixture:regional-hud',"
                + "safeArea=false,interactive=true,regions={'allowed'},visibility={contexts={'menu'}}");
        GuiHudActivation upper = activate(lua, "fixture:regional-upper", "screen='fixture:regional-front',priority=1,"
                + "safeArea=false,interactive=true,regions={'front'},visibility={contexts={'menu'}}");
        GuiHudRuntime.tick(client);
        menuFrame(client);
        require(GuiHudRuntime.inputAvailable(lower) && GuiHudRuntime.inputAvailable(upper),
                "Compatible native menu did not enable declared HUD regions");
        clickHud(client, 12, 65, true);
        require(lua.get("frontClicks").toint() == 1 && lua.get("allowedClicks").isnil(),
                "Overlapping HUD input did not follow reverse paint order");
        clickHud(client, 145, 65, false);
        require(lua.get("excludedClicks").isnil(), "Undeclared compiled HUD control received a pointer event");
        upper.close();
        GuiHudRuntime.tick(client);
        menuFrame(client);
        clickHud(client, 12, 65, true);
        require(lua.get("allowedClicks").toint() == 1, "Closing the upper HUD did not restore lower regional input");
        require(GuiHudRuntime.keyboard('\t', Keyboard.KEY_TAB, true, false, false),
                "Compiled HUD regions were missing from keyboard traversal");
        require(GuiHudRuntime.keyboard('\r', Keyboard.KEY_RETURN, true, false, false),
                "Focused compiled HUD control did not receive keyboard activation");
        require(GuiHudRuntime.keyboard('\0', Keyboard.KEY_RETURN, false, false, false),
                "Focused compiled HUD control lost its matching keyboard release");
        require(lua.get("allowedClicks").toint() == 2 && lua.get("excludedClicks").isnil(),
                "Keyboard focus escaped the declared compiled region");
        GuiScreenActivation layer = GuiHudRuntime.instance(lower, null).dialog("fixture:regional-front",
                Collections.emptyMap(), Collections.emptyMap()).instance;
        require(layer != null, "Interactive HUD could not queue an owned layer");
        GuiHudRuntime.tick(client);
        menuFrame(client);
        require(GuiHudRuntime.keyboard('\0', Keyboard.KEY_ESCAPE, true, false, false),
                "HUD modal dismissal leaked Escape to the native menu");
        GuiHudRuntime.tick(client);
        require(!layer.active(), "HUD Escape left its owned modal active");
        menuFrame(client);
        require(GuiHudRuntime.mouse(12 * client.displayWidth / 320, 174 * client.displayHeight / 240, 0, true, 0,
                false), "Interactive HUD could not begin its final gesture");
        lower.close();
        require(ClientInputRuntime.router().accept(InputDeviceEvent.mouseButton(0, false)),
                "Closing the final interactive HUD leaked its physical release to native input");
        GuiHudRuntime.tick(client);
        menuFrame(client);
        clickHud(client, 12, 65, false);
        require(!GuiHudRuntime.keyboard('\t', Keyboard.KEY_TAB, true, false, false),
                "Passive HUD retained keyboard ownership after the final interactive claim closed");
    }

    private static void clickHud(Minecraft client, int x, int y, boolean expected) {
        int nativeX = x * client.displayWidth / 320;
        int nativeY = (239 - y) * client.displayHeight / 240;
        require(GuiHudRuntime.mouse(nativeX, nativeY, 0, true, 0, false) == expected,
                "Unexpected compiled HUD regional pointer ownership");
        GuiHudRuntime.mouse(nativeX, nativeY, 0, false, 0, false);
    }

    private static void menuFrame(Minecraft client) {
        GuiShowcaseRenderTest.setupFrame();
        GuiHudRuntime.clientOverlay(client, 0, 0, 0);
    }

    private static void screen(Globals lua, String key, String fields) {
        GuiClientUiRuntime.screens().add(GuiScreenParser.parse(OWNER,
                lua.load("return {key='" + key + "'," + fields + "}").call(), GuiClientUiRuntime.components()));
    }

    private static GuiHudActivation activate(Globals lua, String key, String fields) {
        GuiHudRuntime.registry()
                .add(GuiHudParser.parse(OWNER, lua.load("return {key='" + key + "'," + fields + "}").call()));
        return GuiHudRuntime.registry().activate(OWNER, key);
    }

    private static void frame(Minecraft client, boolean reticle, boolean health) {
        GuiShowcaseRenderTest.setupFrame();
        GuiHudRuntime.beginNative(client, 0, 0, 0);
        boolean replaced = GuiHudRuntime.nativePart(GuiHudNativePart.CROSSHAIR);
        require(replaced == reticle, "Unexpected crosshair presentation ownership");
        if (!replaced) {
            new GuiRenderer(GuiTheme.DEFAULT).drawRect(154, 114, 166, 126, 0xFFCC3333);
        }
        require(GuiHudRuntime.nativePart(GuiHudNativePart.HEALTH) == health,
                "Native hiding lost its independent owner");
        require(!GuiHudRuntime.nativePart(GuiHudNativePart.ARMOR), "HUD claims suppressed an unrelated native part");
        GuiHudRuntime.endNative();
        GuiHudRuntime.clientOverlay(client, 0, 0, 0);
    }

    private static int pixel() {
        return GuiShowcaseRenderTest.capture(320, 240).getRGB(160, 120);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
