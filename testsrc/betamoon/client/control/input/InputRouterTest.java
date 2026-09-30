package betamoon.client.control.input;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.lwjgl.input.Keyboard;

/** Standalone contracts for named action transitions and context ownership. */
public final class InputRouterTest {
    private InputRouterTest() {
    }

    public static void main(String[] args) {
        verifyBindings();
        verifyTransitionsAndConsumption();
        verifyPriorityFailureAndCapture();
        verifyRebindingAndLifecycle();
        System.out.println("Input router passed: bindings, transitions, contexts, rebinding and lifecycle.");
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
