package betamoon.gui;

import betamoon.gui.hud.GuiHudRuntime;
import betamoon.gui.hud.GuiHudActivation;
import betamoon.gui.hud.GuiNotification;
import betamoon.gui.hud.GuiNotificationCorner;
import betamoon.gui.hud.GuiNotificationDefinition;
import betamoon.gui.hud.GuiNotificationOptions;
import betamoon.gui.hud.GuiNotificationRuntime;
import betamoon.gui.screen.GuiActionQueue;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.gui.screen.GuiScreenActivation;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.luaapi.client.ui.GuiScreenParser;
import betamoon.luaapi.client.ui.GuiComponentParser;
import betamoon.luaapi.client.ui.GuiHudParser;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.File;
import java.util.Collections;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiMainMenu;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.World;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Real notification documents in the shared offscreen overlay, without
 * launching Minecraft.
 */
final class GuiNotificationRuntimeRenderChecks {
    private static final String OWNER = "notification-runtime-render-fixture";

    static void run(Minecraft client, File output) throws Exception {
        GuiScreen oldScreen = client.currentScreen;
        World oldWorld = client.theWorld;
        EntityPlayerSP oldPlayer = client.thePlayer;
        try {
            client.currentScreen = new GuiMainMenu();
            client.theWorld = null;
            client.thePlayer = null;
            GuiHudRuntime.tick(client);
            require(GuiHudRuntime.registry().activations().isEmpty(),
                    "Notification-only fixture retained a public HUD");
            Globals lua = JsePlatform.standardGlobals();
            GuiClientUiRuntime.screens().add(GuiScreenParser.parse(OWNER,
                    lua.load("return {key='fixture:notice-content',data={message={type='string',default='Notice'}},"
                            + "root={type='button',id='action',width=140,height=20,anchorX=0,anchorY=0,x=4,y=4,"
                            + "bind=function(view) seenMessage=view.data:get('message'); "
                            + "seenCorner=view.notification:snapshot().corner; return {text=seenMessage} end,"
                            + "on={activate=function() noticeClicks=(noticeClicks or 0)+1 end}},"
                            + "actions={apply={scope='client',handler=function() applied=(applied or 0)+1 end}}}")
                            .call(),
                    GuiClientUiRuntime.components()));
            GuiNotificationDefinition passive = definition("fixture:passive-notice", false);
            GuiNotification notice = show(passive, "Worldless notification", GuiNotificationCorner.TOP_RIGHT,
                    GuiNotificationOptions.Lifetime.CLIENT);
            GuiHudRuntime.tick(client);
            frame(client);
            require(notice.state() == GuiNotification.State.VISIBLE && !notice.inputAvailable()
                    && lua.get("seenMessage").tojstring().equals("Worldless notification")
                    && lua.get("seenCorner").tojstring().equals("top_right"),
                    "Worldless notification failed to use typed screen data and scoped host observations");
            Rect bounds = notice.bounds();
            require(bounds.getTop() >= 40 && bounds.getRight() <= 316,
                    "Notification covered the native achievement reservation or escaped the viewport");
            require(!click(client, notice, false), "Passive notification captured a menu pointer event");
            GuiClientUiRuntime.screens().add(GuiScreenParser.parse(OWNER,
                    lua.load("return {key='fixture:notice-shadow',root={type='text',text='Independent HUD'}}").call(),
                    GuiClientUiRuntime.components()));
            String shadowKey = "betamoon:notification/" + notice.id;
            GuiHudRuntime.registry()
                    .add(GuiHudParser
                            .parse(OWNER,
                                    lua.load("return {key='" + shadowKey
                                            + "',screen='fixture:notice-shadow',visibility={contexts={'menu'}}}")
                                            .call()));
            GuiHudActivation shadow = GuiHudRuntime.registry().activate(OWNER, shadowKey);
            GuiHudRuntime.tick(client);
            frame(client);
            require(notice.instance() != null && GuiHudRuntime.instance(shadow, null) != null
                    && notice.instance() != GuiHudRuntime.instance(shadow, null),
                    "A public HUD key collided with internal notification contribution identity");
            shadow.close();
            GuiScreenActivation originalInstance = notice.instance();
            require(notice.update(options("Updated notification", GuiNotificationCorner.TOP_RIGHT,
                    GuiNotificationOptions.Lifetime.CLIENT)), "Live notification rejected detached data update");
            GuiHudRuntime.tick(client);
            frame(client);
            require(lua.get("seenMessage").tojstring().equals("Updated notification"),
                    "Notification handle update did not reach its existing compiled screen model");
            require(notice.instance() == originalInstance, "Notification data update retired its retained activation");
            // Allow the finite Java enter transition to reach its steady state for the
            // capture.
            Thread.sleep(170);
            frame(client);
            ImageIO.write(GuiShowcaseRenderTest.capture(320, 240), "png",
                    new File(output, "notification-worldless.png"));

            GuiNotification interactive = show(definition("fixture:action-notice", true), "Action notification",
                    GuiNotificationCorner.TOP_LEFT, GuiNotificationOptions.Lifetime.CLIENT);
            GuiHudRuntime.tick(client);
            frame(client);
            require(interactive.inputAvailable() && click(client, interactive, true)
                    && lua.get("noticeClicks").toint() == 1,
                    "Declared notification action region was unavailable in a native menu");
            GuiScreenActivation retained = interactive.instance();
            GuiActionQueue.Submission pending = retained.request("apply", Collections.emptyMap());
            require(pending.ticket != null, "Notification action was not admitted before dismissal");
            interactive.dismiss();
            require(!interactive.inputAvailable() && interactive.instance() == null
                    && !pending.ticket.status().equals("queued"),
                    "Dismissing notification retained input, instance authority or its pending intent");
            GuiHudRuntime.tick(client);
            frame(client);
            require(lua.get("applied").isnil(), "An exiting notification dispatched a previously queued action");

            client.currentScreen = null;
            client.theWorld = oldWorld;
            client.thePlayer = oldPlayer;
            GuiHudRuntime.tick(client);
            GuiNotification local = show(passive, "World scoped", GuiNotificationCorner.TOP_LEFT,
                    GuiNotificationOptions.Lifetime.WORLD);
            GuiHudRuntime.tick(client);
            frame(client);
            require(local.state() == GuiNotification.State.VISIBLE && notice.state() == GuiNotification.State.VISIBLE,
                    "Gameplay notification state: local=" + local.state() + "/" + local.reason() + " at "
                            + local.bounds().getTop() + ", client=" + notice.state() + "/" + notice.reason() + " at "
                            + notice.bounds().getTop());
            client.currentScreen = new GuiMainMenu();
            client.theWorld = null;
            client.thePlayer = null;
            GuiHudRuntime.tick(client);
            frame(client);
            require(local.state() == GuiNotification.State.CANCELLED && notice.state() == GuiNotification.State.VISIBLE,
                    "World replacement retained world ownership or cancelled client-scoped notification data");

            GuiClientUiRuntime.components()
                    .add(GuiComponentParser
                            .parse(OWNER,
                                    lua.load("return {key='fixture:notice-fault',size={width=40,height=20},"
                                            + "rawDraw=function() error('expected notification raw failure') end}")
                                            .call()));
            GuiClientUiRuntime.screens().add(GuiScreenParser.parse(OWNER,
                    lua.load("return {key='fixture:notice-broken',root={component='fixture:notice-fault'}}").call(),
                    GuiClientUiRuntime.components()));
            GuiNotificationDefinition broken = new GuiNotificationDefinition(OWNER, "fixture:broken-notice",
                    "fixture:notice-broken", 160, 40, GuiNotificationCorner.TOP_LEFT, false, Collections.emptySet());
            GuiNotification failing = GuiNotificationRuntime.show(broken,
                    new GuiNotificationOptions(Collections.emptyMap(), 5000, GuiNotificationCorner.TOP_LEFT,
                            GuiNotificationOptions.Urgency.NORMAL, GuiNotificationOptions.Lifetime.CLIENT, null, true,
                            false, false)).notification;
            GuiHudRuntime.tick(client);
            frame(client);
            GuiHudRuntime.tick(client);
            require(failing.state() == GuiNotification.State.FAILED && notice.live(),
                    "Faulting notification retained its own contribution or removed another usable view");
            ScriptResourceTracker.unload(OWNER);
            GuiHudRuntime.tick(client);
            require(GuiNotificationRuntime.queue().size() == 0
                    && ((Number) NativeDiscoveryRuntime.bindingsIfAvailable().clients.diagnostics().get("sessions"))
                            .intValue() == 0,
                    "Final notification owner retained queue entries or native resource sessions");
        } finally {
            ScriptResourceTracker.unload(OWNER);
            GuiHudRuntime.close();
            client.currentScreen = oldScreen;
            client.theWorld = oldWorld;
            client.thePlayer = oldPlayer;
        }
        System.out.println("Offscreen notifications passed: no-HUD worldless presentation, typed data/observations, "
                + "achievement reservation, passive/regional input, update/dismiss intent cancellation, client/world lifetimes and fault cleanup.");
    }

    private static GuiNotificationDefinition definition(String key, boolean interactive) {
        return new GuiNotificationDefinition(OWNER, key, "fixture:notice-content", 160, 40,
                GuiNotificationCorner.TOP_RIGHT, interactive,
                interactive ? Collections.singleton("action") : Collections.emptySet());
    }

    private static GuiNotificationOptions options(String message, GuiNotificationCorner corner,
            GuiNotificationOptions.Lifetime lifetime) {
        return new GuiNotificationOptions(Collections.singletonMap("message", message), 5000, corner,
                GuiNotificationOptions.Urgency.NORMAL, lifetime, null, true, false, false);
    }

    private static GuiNotification show(GuiNotificationDefinition definition, String message,
            GuiNotificationCorner corner, GuiNotificationOptions.Lifetime lifetime) {
        GuiNotification notice = GuiNotificationRuntime.show(definition,
                options(message, corner, lifetime)).notification;
        require(notice != null, "Unexpected notification runtime refusal");
        return notice;
    }

    private static boolean click(Minecraft client, GuiNotification notice, boolean release) {
        int x = (notice.bounds().getLeft() + 30) * client.displayWidth / 320;
        int y = (239 - notice.bounds().getTop() - 10) * client.displayHeight / 240;
        boolean consumed = GuiHudRuntime.mouse(x, y, 0, true, 0, false);
        if (release) {
            GuiHudRuntime.mouse(x, y, 0, false, 0, false);
        }
        return consumed;
    }

    private static void frame(Minecraft client) {
        GuiShowcaseRenderTest.setupFrame();
        GuiHudRuntime.clientOverlay(client, 0, 0, 0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
