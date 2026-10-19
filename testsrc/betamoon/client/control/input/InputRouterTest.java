package betamoon.client.control.input;

import betamoon.gui.InputControlsGuiTest;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import org.lwjgl.input.Keyboard;

/** Standalone contracts for named action transitions and context ownership. */
public final class InputRouterTest {
    private InputRouterTest() {
    }

    public static void main(String[] args) throws Exception {
        verifyBindings();
        verifyTransitionsAndConsumption();
        verifyPriorityFailureAndCapture();
        verifyRebindingAndLifecycle();
        verifyToggleAndPreferences();
        verifyWheelToggle();
        verifyControllerContracts();
        PlayerControllerRuntimeTest.main(new String[0]);
        InputControlsGuiTest.main(new String[0]);
        System.out
                .println("Input router passed: bindings, transitions, contexts, preferences and controller contracts.");
    }

    private static void verifyBindings() {
        require("ctrl+shift+key.r".equals(InputBinding.parse("SHIFT+ctrl+key.r").toString()),
                "Bindings normalize modifiers deterministically");
        require(InputBinding.parse("mouse.left").code() == 0, "Named left mouse button maps to LWJGL button zero");
        require(InputBinding.parse("mouse.button4").code() == 3, "Numbered buttons use user-facing one-based names");
        expectFailure(() -> InputBinding.parse("key.not_a_real_key"), "Unknown keys are rejected");
        expectFailure(() -> InputBinding.parse("ctrl+shift"), "Modifier-only bindings are rejected");
        expectFailure(() -> InputBinding.parse("mouse.button0"), "Invalid numbered mouse buttons are rejected");
        require(InputBinding.parse("mouse.left").conflictsWith(InputBinding.parse("mouse.button1")),
                "Mouse aliases conflict by physical code");
        require(InputBinding.parse("key.r").conflictsWith(InputBinding.parse("ctrl+key.r")),
                "Modifier subsets that can activate together are conflicts");
        require(!InputBinding.parse("mouse.wheel_up").conflictsWith(InputBinding.parse("mouse.wheel_down")),
                "Opposite wheel directions remain independent");
        expectFailure(() -> new InputMapDefinition(ContentKey.parseForType("test:conflict", ContentType.INPUT_MAP),
                Arrays.asList(
                        new InputActionDefinition("first", Collections.singletonList(InputBinding.parse("mouse.left"))),
                        new InputActionDefinition("second",
                                Collections.singletonList(InputBinding.parse("mouse.button1"))))),
                "Overlapping defaults are rejected before runtime ordering can hide them");
    }

    private static void verifyTransitionsAndConsumption() {
        List<String> events = new ArrayList<String>();
        InputRouter router = new InputRouter(null);
        InputMapRegistration map = router.register(definition(), "controls.lua");
        InputContext context = map.activate(10, EnumSet.noneOf(InputFamily.class));
        context.on("jump", event -> {
            events.add(event.phase().luaName());
            return InputDisposition.HANDLED;
        });

        Object world = new Object();
        Object player = new Object();
        router.beginCycle(world, player, null, true);
        require(router.accept(InputDeviceEvent.key(Keyboard.KEY_J, true)), "A handled press consumes native input");
        InputActionState pressed = map.state("jump");
        require(pressed.pressed() && pressed.held() && !pressed.released() && pressed.amount() == 1,
                "A first press publishes exact action state");
        require(router.accept(InputDeviceEvent.key(Keyboard.KEY_J, true)),
                "A repeated event remains consumed without another semantic transition");
        require(events.equals(Collections.singletonList("pressed")), "Held key repeats do not redispatch Lua");

        router.beginCycle(world, player, null, true);
        InputActionState held = map.state("jump");
        require(!held.pressed() && held.held() && !held.released(), "Held state survives with transient flags cleared");
        require(router.accept(InputDeviceEvent.key(Keyboard.KEY_J, false)), "A handled release remains consumed");
        InputActionState released = map.state("jump");
        require(!released.pressed() && !released.held() && released.released(), "Release is visible for one cycle");
        require(events.equals(Arrays.asList("pressed", "released")), "Callbacks receive one press and one release");

        router.accept(InputDeviceEvent.mouseWheel(-120));
        InputActionState wheel = map.state("zoom");
        require(wheel.pressed() && wheel.released() && !wheel.held() && wheel.amount() == -120,
                "Wheel input is a signed pulse, not a sticky held action");
    }

    private static void verifyPriorityFailureAndCapture() {
        verifyCrossMapPriority();
        List<String> order = new ArrayList<String>();
        List<String> failures = new ArrayList<String>();
        InputRouter router = new InputRouter(
                (context, action, error) -> failures.add(action + ":" + error.getMessage()));
        InputMapRegistration map = router.register(definition(), "priority.lua");
        InputContext low = map.activate(0, EnumSet.noneOf(InputFamily.class));
        low.on("jump", event -> {
            order.add("low");
            return InputDisposition.HANDLED;
        });
        InputContext older = map.activate(20, EnumSet.noneOf(InputFamily.class));
        older.on("jump", event -> {
            order.add("older");
            return InputDisposition.PASS;
        });
        InputContext newer = map.activate(20, EnumSet.noneOf(InputFamily.class));
        newer.on("jump", event -> {
            order.add("newer");
            throw new IllegalStateException("expected");
        });

        router.beginCycle(new Object(), new Object(), null, true);
        require(router.accept(InputDeviceEvent.key(Keyboard.KEY_J, true)),
                "A lower fallback may handle a failed layer");
        require(order.equals(Arrays.asList("newer", "older", "low")),
                "Contexts resolve priority-first and newest-first at equal priority");
        require(!newer.isActive() && failures.equals(Collections.singletonList("jump:expected")),
                "A failing context is disabled and diagnosed without disabling fallbacks");

        InputContext capture = map.activate(30, EnumSet.of(InputFamily.NAMED_ACTIONS, InputFamily.LOOK));
        router.beginCycle(new Object(), new Object(), null, true);
        require(router.accept(InputDeviceEvent.key(Keyboard.KEY_J, true)),
                "A declared named-action capture consumes even without a listener");
        require(router.captures(InputFamily.LOOK), "Native family capture is visible to controller hooks");
        capture.close();
        require(!router.captures(InputFamily.LOOK), "Closing a context releases its native captures immediately");
    }

    private static void verifyCrossMapPriority() {
        List<String> order = new ArrayList<String>();
        InputRouter router = new InputRouter(null);
        InputMapRegistration registeredFirst = router.register(definition("test:first"), "first.lua");
        InputMapRegistration registeredSecond = router.register(definition("test:second"), "second.lua");
        InputContext low = registeredFirst.activate(0, EnumSet.noneOf(InputFamily.class));
        low.on("jump", event -> {
            order.add("low");
            return InputDisposition.HANDLED;
        });
        InputContext high = registeredSecond.activate(100, EnumSet.noneOf(InputFamily.class));
        high.on("jump", event -> {
            order.add("high");
            return InputDisposition.HANDLED;
        });
        router.beginCycle(new Object(), new Object(), null, true);
        router.accept(InputDeviceEvent.key(Keyboard.KEY_J, true));
        require(order.equals(Collections.singletonList("high")),
                "Global context priority outranks input-map registration order");
        require(registeredFirst.state("jump").held() && registeredSecond.state("jump").held(),
                "All semantic states update before a high-priority context consumes dispatch");
    }

    private static void verifyRebindingAndLifecycle() {
        List<String> events = new ArrayList<String>();
        InputRouter router = new InputRouter(null);
        InputMapRegistration map = router.register(definition(), "reloadable.lua");
        InputContext context = map.activate(0, EnumSet.noneOf(InputFamily.class));
        context.on("jump", event -> {
            events.add(event.phase().luaName());
            return InputDisposition.PASS;
        });
        context.on("rotate", event -> InputDisposition.PASS);

        expectFailure(() -> map.rebind("jump", Collections.singletonList(InputBinding.parse("ctrl+key.r")), false),
                "Rebinding rejects conflicts by default");
        map.rebind("jump", Collections.singletonList(InputBinding.parse("key.k")), false);
        router.beginCycle(new Object(), new Object(), null, true);
        require(!router.accept(InputDeviceEvent.key(Keyboard.KEY_J, true)), "Old defaults stop matching after rebind");
        router.accept(InputDeviceEvent.key(Keyboard.KEY_K, true));
        require(map.state("jump").held(), "Rebound keys drive action state");

        Object world = new Object();
        Object player = new Object();
        router.beginCycle(world, player, null, true);
        require(map.state("jump").released(), "World replacement synthesizes release before clearing state");
        map.resetBinding("jump");
        require("key.j".equals(map.bindings("jump").get(0).toString()), "Reset restores immutable defaults");

        router.unloadOwner("reloadable.lua");
        require(!map.isActive() && !context.isActive() && router.registrationCount() == 0 && router.contextCount() == 0,
                "Owner unload closes maps and contexts without stale layers");
    }

    private static void verifyToggleAndPreferences() throws Exception {
        File file = Files.createTempDirectory("betamoon-input-test").resolve("inputs.json").toFile();
        InputPreferenceStore preferences = new InputPreferenceStore(file);
        InputRouter router = new InputRouter(null, preferences);
        InputActionDefinition toggle = new InputActionDefinition("sprint",
                Collections.singletonList(InputBinding.parse("key.j")), "Sprint", "Movement", true,
                EnumSet.of(InputActivationMode.HOLD, InputActivationMode.TOGGLE), InputActivationMode.HOLD);
        InputMapDefinition definition = new InputMapDefinition(
                ContentKey.parseForType("test:preferences", ContentType.INPUT_MAP), Collections.singletonList(toggle));
        InputMapRegistration map = router.register(definition, "test.lua");
        map.setMode("sprint", InputActivationMode.TOGGLE);
        map.setPreferredBindings("sprint", Collections.singletonList(InputBinding.parse("key.k")), false);
        Object world = new Object();
        Object player = new Object();
        router.beginCycle(world, player, null, true);
        router.accept(InputDeviceEvent.key(Keyboard.KEY_K, true));
        require(map.state("sprint").held() && map.state("sprint").active() && map.state("sprint").activated(),
                "Toggle press activates logical state");
        router.accept(InputDeviceEvent.key(Keyboard.KEY_K, true));
        require(map.state("sprint").active() && !map.state("sprint").deactivated(), "Repeat does not flip toggle");
        router.beginCycle(world, player, null, true);
        router.accept(InputDeviceEvent.key(Keyboard.KEY_K, false));
        require(!map.state("sprint").held() && map.state("sprint").active(), "Physical release leaves toggle active");
        router.beginCycle(world, player, null, true);
        router.accept(InputDeviceEvent.key(Keyboard.KEY_K, true));
        require(!map.state("sprint").active() && map.state("sprint").deactivated(), "Second press deactivates toggle");
        router.accept(InputDeviceEvent.key(Keyboard.KEY_K, false));
        router.beginCycle(world, player, null, true);
        router.accept(InputDeviceEvent.key(Keyboard.KEY_K, true));
        require(map.state("sprint").active(), "A later press reactivates toggle");
        map.setPreferredBindings("sprint", Collections.<InputBinding>emptyList(), false);
        require(!map.state("sprint").active() && map.state("sprint").deactivated(),
                "Unbinding a toggled action synthesizes logical deactivation");
        map.setPreferredBindings("sprint", Collections.singletonList(InputBinding.parse("key.k")), false);
        router.beginCycle(world, player, new Object(), true);
        require(!map.state("sprint").held() && !map.state("sprint").active(),
                "Screen change clears physical and logical state");
        router.beginCycle(world, player, null, true);
        router.accept(InputDeviceEvent.key(Keyboard.KEY_K, true));
        require(map.state("sprint").active(), "A new press works after a lifecycle reset");
        map.close();

        InputRouter reloaded = new InputRouter(null, new InputPreferenceStore(file));
        InputMapRegistration restored = reloaded.register(definition, "test.lua");
        require(restored.mode("sprint") == InputActivationMode.TOGGLE
                && "key.k".equals(restored.bindings("sprint").get(0).toString()),
                "Mode and binding survive new router registration");
        restored.rebind("sprint", Collections.singletonList(InputBinding.parse("key.r")), false);
        restored.resetBinding("sprint");
        require("key.j".equals(restored.bindings("sprint").get(0).toString()),
                "Session reset keeps released declaration-default behavior");
        restored.setPreferredBindings("sprint", Collections.<InputBinding>emptyList(), false);
        require(restored.bindings("sprint").isEmpty(), "Empty saved override unbinds an action");
        restored.resetAllPreferences();
        require(restored.mode("sprint") == InputActivationMode.HOLD
                && "key.j".equals(restored.bindings("sprint").get(0).toString()),
                "Reset all returns to current declaration defaults");
        require(new InputPreferenceStore(file).count() == 0, "Reset all removes persisted records");
        InputPreferenceStore retained = new InputPreferenceStore(file);
        retained.setMode(definition.key(), "sprint", InputActivationMode.TOGGLE);
        Files.write(file.toPath(), "{broken".getBytes(StandardCharsets.UTF_8));
        retained.load();
        require(retained.get(definition.key(), "sprint").mode() == InputActivationMode.TOGGLE,
                "Malformed preference files retain the last usable in-memory snapshot");
    }

    private static void verifyWheelToggle() {
        InputRouter router = new InputRouter(null);
        InputActionDefinition action = new InputActionDefinition("switch",
                Collections.singletonList(InputBinding.parse("mouse.wheel_up")), null, null, true,
                EnumSet.of(InputActivationMode.HOLD, InputActivationMode.TOGGLE), InputActivationMode.TOGGLE);
        InputMapRegistration map = router
                .register(new InputMapDefinition(ContentKey.parseForType("test:wheel_toggle", ContentType.INPUT_MAP),
                        Collections.singletonList(action)), "wheel.lua");
        Object world = new Object();
        Object player = new Object();
        router.beginCycle(world, player, null, true);
        router.accept(InputDeviceEvent.mouseWheel(120));
        InputActionState enabled = map.state("switch");
        require(enabled.active() && enabled.activated() && !enabled.deactivated() && enabled.pressed()
                && enabled.released() && !enabled.held(),
                "A matching wheel pulse activates a toggle without a held state");
        router.beginCycle(world, player, null, true);
        require(map.state("switch").active() && !map.state("switch").activated(),
                "Wheel toggle activation persists into later cycles");
        router.accept(InputDeviceEvent.mouseWheel(-120));
        require(map.state("switch").active(), "The opposite wheel direction does not switch the action");
        router.accept(InputDeviceEvent.mouseWheel(120));
        InputActionState disabled = map.state("switch");
        require(!disabled.active() && disabled.deactivated() && !disabled.activated(),
                "The next matching wheel pulse deactivates a toggle");
        map.close();
    }

    private static void verifyControllerContracts() {
        expectFailure(() -> new PlayerControllerRuntime.Movement(Float.NaN, 0, false, false),
                "Nonfinite manual movement is rejected");
        expectFailure(() -> new PlayerControllerRuntime.Facing(0, 91), "Out-of-range facing is rejected");
        require(PlayerControllerRuntime.Stage.parse("worldActions") == PlayerControllerRuntime.Stage.WORLD_ACTIONS,
                "Controller stage names are stable");
    }

    private static InputMapDefinition definition() {
        return definition("test:gameplay");
    }

    private static InputMapDefinition definition(String key) {
        return new InputMapDefinition(ContentKey.parseForType(key, ContentType.INPUT_MAP), Arrays.asList(
                new InputActionDefinition("jump", Collections.singletonList(InputBinding.parse("key.j"))),
                new InputActionDefinition("rotate", Collections.singletonList(InputBinding.parse("ctrl+key.r"))),
                new InputActionDefinition("zoom", Collections.singletonList(InputBinding.parse("mouse.wheel")))));
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
