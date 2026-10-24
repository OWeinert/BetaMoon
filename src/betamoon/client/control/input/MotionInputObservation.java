package betamoon.client.control.input;

import betamoon.entity.modifier.EntityMovementRule;
import betamoon.entity.modifier.EntityResolvedRules;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.NativeFieldAccess;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.GameSettings;
import net.minecraft.src.MovementInputFromOptions;

/**
 * Routed physical edges and logical movement, independent of sneak-reduced
 * native axes.
 */
public final class MotionInputObservation {
    private static final String[] ACTIONS = {"forward", "back", "left", "right", "jump", "sneak"};
    private static final int MAX_EDGES = 1024;
    private static final Field KEYS = NativeFieldAccess.field(MovementInputFromOptions.class,
            new FieldRef(new ClassRef("net/minecraft/src/MovementInputFromOptions"), "movementKeyStates", "[Z"));
    private static final boolean[] HELD = new boolean[6];
    private static final List<Map<String, Object>> EDGES = new ArrayList<>();
    private static int[] bindings;
    private static Object world;
    private static EntityPlayerSP player;
    private static Object screen;
    private static boolean focused;
    private static boolean available;
    private static long sequence;
    private static PlayerControllerRuntime.Movement logical = PlayerControllerRuntime.Movement.ZERO;

    private MotionInputObservation() {
    }

    public static void begin(Minecraft minecraft, boolean hasFocus) {
        EntityPlayerSP nextPlayer = minecraft == null ? null : minecraft.thePlayer;
        Object nextWorld = minecraft == null ? null : minecraft.theWorld;
        Object nextScreen = minecraft == null ? null : minecraft.currentScreen;
        GameSettings settings = minecraft == null ? null : minecraft.gameSettings;
        int[] nextBindings = settings == null
                ? null
                : new int[]{settings.keyBindForward.keyCode, settings.keyBindBack.keyCode, settings.keyBindLeft.keyCode,
                        settings.keyBindRight.keyCode, settings.keyBindJump.keyCode, settings.keyBindSneak.keyCode};
        if (world != nextWorld || player != nextPlayer || screen != nextScreen || focused != hasFocus
                || !Arrays.equals(bindings, nextBindings)) {
            Arrays.fill(HELD, false);
            EDGES.clear();
            logical = PlayerControllerRuntime.Movement.ZERO;
        }
        world = nextWorld;
        player = nextPlayer;
        screen = nextScreen;
        focused = hasFocus;
        bindings = nextBindings;
        available = player != null && world != null && screen == null && focused && bindings != null;
    }

    public static void accept(int keyCode, boolean pressed, boolean routed) {
        if (!available || bindings == null) {
            return;
        }
        int matched = -1;
        for (int i = 0; i < bindings.length; i++) {
            if (bindings[i] == keyCode) {
                matched = i;
            }
        }
        if (matched < 0 || pressed && !routed || HELD[matched] == pressed) {
            return;
        }
        HELD[matched] = pressed;
        if (EDGES.size() == MAX_EDGES) {
            EDGES.clear();
            Arrays.fill(HELD, false);
            available = false;
            return;
        }
        Map<String, Object> transition = new LinkedHashMap<>();
        transition.put("action", ACTIONS[matched]);
        transition.put("phase", pressed ? "pressed" : "released");
        transition.put("sequence", ++sequence);
        EDGES.add(Collections.unmodifiableMap(transition));
    }

    public static PlayerControllerRuntime.Movement nativeLogical(MovementInputFromOptions input) {
        boolean[] keys = (boolean[]) NativeFieldAccess.get(KEYS, input);
        if (keys.length < 6) {
            throw new IllegalStateException("Unsupported native movement key-state layout");
        }
        return new PlayerControllerRuntime.Movement((keys[0] ? 1 : 0) - (keys[1] ? 1 : 0),
                (keys[2] ? 1 : 0) - (keys[3] ? 1 : 0), keys[4], keys[5]);
    }

    public static void resolved(EntityPlayerSP target, PlayerControllerRuntime.Movement value) {
        if (target == player) {
            logical = value;
        }
    }

    public static void applySneak(EntityPlayerSP target, EntityResolvedRules rules) {
        if (target != player || !target.movementInput.sneak || rules.get(EntityMovementRule.SNEAK_SLOWDOWN) == null) {
            return;
        }
        float scale = (float) rules.numberValue(EntityMovementRule.SNEAK_SLOWDOWN, 0.3);
        target.movementInput.moveForward = available ? logical.forward * scale : 0;
        target.movementInput.moveStrafe = available ? logical.strafe * scale : 0;
    }

    public static Map<String, Object> take(EntityPlayerSP target) {
        boolean current = target == player && available;
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("available", current);
        snapshot.put("forward", current ? logical.forward : 0);
        snapshot.put("strafe", current ? logical.strafe : 0);
        snapshot.put("jump", current && logical.jump);
        snapshot.put("sneak", current && logical.sneak);
        Map<String, Object> actions = new LinkedHashMap<>();
        for (int i = 0; i < ACTIONS.length; i++) {
            Map<String, Object> state = new LinkedHashMap<>();
            boolean pressed = false;
            boolean released = false;
            for (Map<String, Object> edge : EDGES) {
                if (ACTIONS[i].equals(edge.get("action"))) {
                    pressed |= "pressed".equals(edge.get("phase"));
                    released |= "released".equals(edge.get("phase"));
                }
            }
            state.put("held", current && HELD[i]);
            state.put("pressed", current && pressed);
            state.put("released", current && released);
            actions.put(ACTIONS[i], Collections.unmodifiableMap(state));
        }
        snapshot.put("actions", Collections.unmodifiableMap(actions));
        snapshot.put("transitions",
                current ? Collections.unmodifiableList(new ArrayList<>(EDGES)) : Collections.emptyList());
        EDGES.clear();
        return Collections.unmodifiableMap(snapshot);
    }
}
