package betamoon.gui.hud;

import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.luaapi.client.ui.GuiHudParser;
import betamoon.luaapi.client.ui.GuiHudLuaAccessTest;
import betamoon.luaapi.client.ui.GuiScopedSnapshot;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Declaration ownership, source-audited atlas semantics and native collision
 * reservations.
 */
public final class GuiHudFoundationTest {
    private static final String OWNER = "hud-foundation-test";

    public static void main(String[] args) throws Exception {
        try {
            declarations();
            nativeAtlas();
            reservations();
            scopedObservations();
            GuiHudInputTest.run();
            GuiHudLuaAccessTest.run();
        } finally {
            ScriptResourceTracker.unload(OWNER);
        }
        System.out.println("HUD foundation passed: passive metadata, strict native claims/regions, owner cleanup, "
                + "activation sequences, semantic atlas classification and safe-area reservations.");
    }

    private static void declarations() {
        GuiHudRegistry registry = new GuiHudRegistry();
        GuiHudDefinition definition = parse("screen='test:content'");
        registry.add(definition);
        require(registry.activations().isEmpty() && !definition.interactive && definition.safeArea
                && definition.contexts.contains(GuiHudDefinition.Context.GAMEPLAY) && !definition.hideGui,
                "HUD registration acquired authority or passive visibility defaults changed");
        GuiHudActivation first = registry.activate(OWNER, definition.key);
        first.close();
        GuiHudActivation second = registry.activate(OWNER, definition.key);
        require(second.sequence > first.sequence && !first.input.active(), "Reactivation reused an ordering lifetime");
        GuiHudDefinition hide = parse("nativeParts={crosshair={hide=true},chat={replace='test:chat'}}");
        require(hide.screen == null && hide.nativeParts.get(GuiHudNativePart.CROSSHAIR).hide
                && hide.nativeParts.get(GuiHudNativePart.CHAT).replace.equals("test:chat"),
                "Native hide and registered-screen replacement metadata were conflated");
        parse("screen='test:content',interactive=true,regions={'button'},visibility={contexts={'chat','menu'}}");
        for (String invalid : new String[]{"unknown=true", "screen='test:content',interactive=true",
                "screen='test:content',regions={'invisible'}", "nativeParts={crosshair={}}",
                "nativeParts={crosshair={hide=true,replace='test:other'}}", "nativeParts={targeting={hide=true}}",
                "screen='test:content',visibility={contexts={'gameplay','gameplay'}}",
                "screen='test:content',visibility={contexts={'unsupported'}}"}) {
            try {
                parse(invalid);
            } catch (LuaError expected) {
                continue;
            }
            throw new AssertionError("Invalid HUD declaration was accepted: " + invalid);
        }
        ScriptResourceTracker.unload(OWNER);
        require(!definition.available() && second.status().equals("closed") && registry.get(definition.key) == null,
                "Owner unload retained HUD metadata, claims or input");
    }

    private static GuiHudDefinition parse(String fields) {
        return GuiHudParser.parse(OWNER,
                JsePlatform.standardGlobals().load("return {key='test:hud'," + fields + "}").call());
    }

    private static void nativeAtlas() {
        require(GuiHudNativePart.texture(0, 0, 182, 22) == GuiHudNativePart.HOTBAR
                && GuiHudNativePart.texture(0, 22, 24, 22) == GuiHudNativePart.HOTBAR
                && GuiHudNativePart.texture(0, 0, 16, 16) == GuiHudNativePart.CROSSHAIR,
                "Native hotbar selection/background or crosshair atlas semantics changed");
        for (int u : new int[]{16, 25, 52, 61, 70, 79}) {
            require(GuiHudNativePart.texture(u, 0, 9, 9) == GuiHudNativePart.HEALTH,
                    "Native heart background/blink/half/full atlas rectangle was missed");
        }
        for (int u : new int[]{16, 25, 34}) {
            require(GuiHudNativePart.texture(u, 9, 9, 9) == GuiHudNativePart.ARMOR,
                    "Native armor atlas rectangle was missed");
        }
        require(GuiHudNativePart.texture(16, 18, 9, 9) == GuiHudNativePart.BREATH
                && GuiHudNativePart.texture(25, 18, 9, 9) == GuiHudNativePart.BREATH,
                "Native full/partial air bubbles were missed");
        require(GuiHudNativePart.texture(16, 19, 9, 9) == null && GuiHudNativePart.texture(0, 0, 256, 256) == null
                && GuiHudNativePart.texture(0, 0, 182, 23) == null,
                "Unrelated native artwork was classified as a suppressible HUD part");
    }

    private static void reservations() {
        for (int width : new int[]{320, 427, 640, 1024}) {
            List<Rect> occupied = Arrays.asList(Rect.fromPositionAndSize(2, 100, 320, 90),
                    Rect.fromPositionAndSize(width / 2 - 94, 198, 188, 42),
                    Rect.fromPositionAndSize(width - 160, 0, 160, 36));
            Rect safe = GuiHudLayout.safeArea(width, 240, occupied);
            require(!safe.isEmpty() && safe.getLeft() >= 4 && safe.getRight() <= width - 4,
                    "Safe HUD bounds left the viewport");
            for (Rect blocked : occupied) {
                require(safe.intersect(blocked).isEmpty(), "Safe HUD content overlaps native/notification chrome");
            }
        }
        require(GuiHudLayout.safeArea(320, 240, Arrays.asList(Rect.fromPositionAndSize(0, 0, 320, 240))).isEmpty(),
                "Fully reserved viewport still admitted a safe HUD region");
    }

    private static void scopedObservations() {
        LuaValue access;
        LuaValue detached;
        try (LuaCallbackScope scope = LuaCallbackScope.binding()) {
            GuiScopedSnapshot publication = new GuiScopedSnapshot(Collections.singletonMap("health", 17));
            access = publication.access(scope, "snapshot");
            detached = ScriptExecution.withOwner("external-component", () -> access.get("snapshot").call());
            require(detached.get("health").toint() == 17,
                    "Reusable component owner could not read its scoped host observation");
            require(publication.access(scope, "snapshot").get("snapshot").call() == detached,
                    "Repeated binding contexts recopied one immutable published snapshot");
            try {
                detached.set("health", 0);
                throw new AssertionError("A component mutated its read-only host snapshot");
            } catch (LuaError expected) {
                // The scoped method returns detached read-only values.
            }
        }
        require(detached.get("health").toint() == 17, "Detached snapshot expired with its callback method");
        try {
            access.get("snapshot").call();
            throw new AssertionError("Retained host observation method outlived its callback");
        } catch (LuaError expected) {
            // Callback methods expire even though their copied result remains readable.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
