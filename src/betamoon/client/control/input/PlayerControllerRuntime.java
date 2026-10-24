package betamoon.client.control.input;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.MovementInputFromOptions;

/**
 * Per-stage player control ownership. Native behavior is untouched without an
 * active layer.
 */
public final class PlayerControllerRuntime {
    public static final int MAX_CONTROLLERS = 256;
    public static final int MAX_ACTIVATIONS = 256;
    public enum Stage {
        MOVEMENT("movement"), LOOK("look"), WORLD_ACTIONS("worldActions"), INVENTORY("inventory");

        private final String luaName;

        Stage(String luaName) {
            this.luaName = luaName;
        }

        public String luaName() {
            return luaName;
        }

        public static Stage parse(String name) {
            for (Stage stage : values()) {
                if (stage.luaName.equals(name)) {
                    return stage;
                }
            }
            throw new IllegalArgumentException("Unknown controller stage: " + name);
        }
    }

    public enum Mode {
        NATIVE, MODIFY, MANUAL, DISABLED;

        public static Mode parse(String name) {
            for (Mode mode : values()) {
                if (mode.name().equalsIgnoreCase(name)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unknown controller mode: " + name);
        }

        public String luaName() {
            return name().toLowerCase(Locale.ENGLISH);
        }
    }

    public interface FailureHandler {
        void failed(Registration registration, Stage stage, Throwable error);
    }

    public interface Handler {
        Decision apply(Snapshot snapshot);
    }

    public static final class Movement {
        public static final Movement ZERO = new Movement(0, 0, false, false);
        public final float forward;
        public final float strafe;
        public final boolean jump;
        public final boolean sneak;

        public Movement(float forward, float strafe, boolean jump, boolean sneak) {
            if (!Float.isFinite(forward) || !Float.isFinite(strafe) || Math.abs(forward) > 1 || Math.abs(strafe) > 1) {
                throw new IllegalArgumentException("Movement forward and strafe must be finite in -1..1");
            }
            this.forward = forward;
            this.strafe = strafe;
            this.jump = jump;
            this.sneak = sneak;
        }

        public static Movement nativeState(MovementInputFromOptions input) {
            return new Movement(input.moveForward, input.moveStrafe, input.jump, input.sneak);
        }

        public void apply(MovementInputFromOptions input) {
            input.moveForward = forward;
            input.moveStrafe = strafe;
            input.jump = jump;
            input.sneak = sneak;
        }
    }

    public static final class Facing {
        public final float yaw;
        public final float pitch;

        public Facing(float yaw, float pitch) {
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(pitch) > 90) {
                throw new IllegalArgumentException("Facing must have finite yaw and pitch in -90..90");
            }
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public void apply(EntityPlayerSP player) {
            player.rotationYaw = yaw;
            player.rotationPitch = pitch;
            player.prevRotationYaw = yaw;
            player.prevRotationPitch = pitch;
        }
    }

    public static final class Snapshot {
        public final Stage stage;
        public final EntityPlayerSP player;
        public final long tick;
        public final Movement nativeMovement;
        public final Facing nativeFacing;
        public final String action;
        public final int button;
        public final boolean held;

        private Snapshot(Stage stage, EntityPlayerSP player, long tick, Movement movement, Facing facing, String action,
                int button, boolean held) {
            this.stage = stage;
            this.player = player;
            this.tick = tick;
            this.nativeMovement = movement;
            this.nativeFacing = facing;
            this.action = action;
            this.button = button;
            this.held = held;
        }
    }

    public static final class Decision {
        public static final Decision PASS = new Decision(null, null, true);
        public static final Decision DENY = new Decision(null, null, false);
        public final Movement movement;
        public final Facing facing;
        public final boolean pass;

        public Decision(Movement movement, Facing facing, boolean pass) {
            this.movement = movement;
            this.facing = facing;
            this.pass = pass;
        }
    }

    public static final class Definition {
        public final ContentKey key;
        private final EnumMap<Stage, Mode> modes = new EnumMap<Stage, Mode>(Stage.class);

        public Definition(ContentKey key, Map<Stage, Mode> declaredModes) {
            if (key == null || !ContentType.INPUT_CONTROLLER.equals(key.type()) || declaredModes == null) {
                throw new IllegalArgumentException("Controller key and stages are required");
            }
            this.key = key;
            for (Stage stage : Stage.values()) {
                Mode mode = declaredModes.get(stage);
                modes.put(stage, mode == null ? Mode.NATIVE : mode);
            }
        }

        public Mode mode(Stage stage) {
            return modes.get(stage);
        }
    }

    public final class Registration implements AutoCloseable {
        private final Definition definition;
        private final String owner;
        private final EnumMap<Stage, Handler> handlers = new EnumMap<Stage, Handler>(Stage.class);
        private boolean active = true;

        private Registration(Definition definition, String owner) {
            this.definition = definition;
            this.owner = owner;
        }

        public Definition definition() {
            return definition;
        }

        public String owner() {
            return owner;
        }

        public boolean isActive() {
            return active;
        }

        public void on(Stage stage, Handler handler) {
            requireActive();
            if (definition.mode(stage) == Mode.NATIVE || definition.mode(stage) == Mode.DISABLED) {
                throw new IllegalArgumentException("Controller stage " + stage.luaName() + " has no callback mode");
            }
            if (handler == null) {
                handlers.remove(stage);
            } else {
                handlers.put(stage, handler);
            }
        }

        public Activation activate(EntityPlayerSP player, int priority) {
            requireActive();
            if (player == null || player.worldObj == null || player.isDead || player.health <= 0) {
                throw new IllegalArgumentException("Controller requires a live local player");
            }
            if (PlayerControllerRuntime.this.player != player || world != player.worldObj || screen != null
                    || !focused) {
                throw new IllegalStateException("Controller activation requires focused gameplay");
            }
            if (priority < InputRouter.MIN_PRIORITY || priority > InputRouter.MAX_PRIORITY) {
                throw new IllegalArgumentException("Controller priority is outside -10000..10000");
            }
            if (activations.size() >= MAX_ACTIVATIONS) {
                throw new IllegalStateException("Controller activation limit reached: " + MAX_ACTIVATIONS);
            }
            Activation activation = new Activation(this, player, priority, ++sequence);
            activations.add(activation);
            Collections.sort(activations, ORDER);
            return activation;
        }

        private void requireActive() {
            if (!active) {
                throw new IllegalStateException("Controller is no longer registered");
            }
        }

        @Override
        public void close() {
            if (active) {
                active = false;
                for (Activation activation : new ArrayList<Activation>(activations)) {
                    if (activation.registration == this) {
                        activation.close();
                    }
                }
                registrations.remove(definition.key);
            }
        }
    }

    public final class Activation implements AutoCloseable {
        private final Registration registration;
        private final EntityPlayerSP player;
        private final Object world;
        private final int priority;
        private final long sequence;
        private final EnumSet<Stage> failedStages = EnumSet.noneOf(Stage.class);
        private Movement submittedMovement;
        private Facing submittedFacing;
        private String submittedActionKind;
        private Boolean submittedAction;
        private long submittedCycle = -1;
        private boolean active = true;

        private Activation(Registration registration, EntityPlayerSP player, int priority, long sequence) {
            this.registration = registration;
            this.player = player;
            this.world = player.worldObj;
            this.priority = priority;
            this.sequence = sequence;
        }

        public boolean isActive() {
            return active;
        }

        public void submit(Movement movement, Facing facing, String actionKind, Boolean passAction) {
            if (!active) {
                throw new IllegalStateException("Controller activation is closed");
            }
            if (actionKind == null != (passAction == null)) {
                throw new IllegalArgumentException("Controller action kind and result must be supplied together");
            }
            if (actionKind != null && !"attack".equals(actionKind) && !"use".equals(actionKind)
                    && !"heldBreaking".equals(actionKind) && !"open".equals(actionKind) && !"drop".equals(actionKind)
                    && !"hotbar".equals(actionKind) && !"hotbarWheel".equals(actionKind)) {
                throw new IllegalArgumentException("Unknown controller action kind: " + actionKind);
            }
            submittedMovement = movement;
            submittedFacing = facing;
            submittedActionKind = actionKind;
            submittedAction = passAction;
            submittedCycle = cycle + 1;
        }

        public void face(Facing facing) {
            if (!active) {
                throw new IllegalStateException("Controller activation is closed");
            }
            if (submittedCycle != cycle + 1) {
                submittedMovement = null;
                submittedActionKind = null;
                submittedAction = null;
            }
            submittedFacing = facing;
            submittedCycle = cycle + 1;
        }

        public int priority() {
            return priority;
        }

        public long tick() {
            return cycle;
        }

        public Definition definition() {
            return registration.definition;
        }

        @Override
        public void close() {
            if (active) {
                active = false;
                submittedMovement = null;
                submittedFacing = null;
                submittedActionKind = null;
                submittedAction = null;
                activations.remove(this);
            }
        }
    }

    private static final Comparator<Activation> ORDER = new Comparator<Activation>() {
        public int compare(Activation left, Activation right) {
            int priority = Integer.compare(right.priority, left.priority);
            return priority == 0 ? Long.compare(right.sequence, left.sequence) : priority;
        }
    };

    private final Map<ContentKey, Registration> registrations = new LinkedHashMap<ContentKey, Registration>();
    private final List<Activation> activations = new ArrayList<Activation>();
    private final FailureHandler failures;
    private long sequence;
    private long cycle;
    private Object world;
    private Object player;
    private Object screen;
    private boolean focused = true;
    private Facing tickFacing;
    private Activation facingOwner;
    private boolean suppressNativeLook;
    private Movement logicalMovement;

    public PlayerControllerRuntime(FailureHandler failures) {
        this.failures = failures;
    }

    public Registration register(Definition definition, String owner) {
        if (definition == null || owner == null || owner.trim().isEmpty()) {
            throw new IllegalArgumentException("Controller definition and owner are required");
        }
        if (registrations.size() >= MAX_CONTROLLERS || registrations.containsKey(definition.key)) {
            throw new IllegalStateException("Controller limit or duplicate key: " + definition.key);
        }
        Registration result = new Registration(definition, owner);
        registrations.put(definition.key, result);
        return result;
    }

    public Registration get(ContentKey key) {
        return registrations.get(key);
    }

    public void beginCycle(Object currentWorld, Object currentPlayer, Object currentScreen, boolean hasFocus) {
        cycle++;
        boolean changed = world != currentWorld || player != currentPlayer || screen != currentScreen
                || focused != hasFocus;
        world = currentWorld;
        player = currentPlayer;
        screen = currentScreen;
        focused = hasFocus;
        tickFacing = null;
        facingOwner = null;
        suppressNativeLook = false;
        if (changed || !hasFocus || currentScreen != null || currentPlayer == null
                || currentPlayer instanceof EntityPlayerSP
                        && (((EntityPlayerSP) currentPlayer).isDead || ((EntityPlayerSP) currentPlayer).health <= 0)) {
            for (Activation activation : new ArrayList<Activation>(activations)) {
                activation.close();
            }
        }
        for (Activation activation : new ArrayList<Activation>(activations)) {
            if (activation.submittedCycle < cycle) {
                activation.submittedMovement = null;
                activation.submittedFacing = null;
                activation.submittedActionKind = null;
                activation.submittedAction = null;
            }
        }
        resolveLookForTick();
    }

    private Activation winner(Stage stage) {
        for (Activation activation : activations) {
            if (activation.active && activation.player == player && activation.world == world
                    && !activation.failedStages.contains(stage)
                    && activation.registration.definition.mode(stage) != Mode.NATIVE) {
                return activation;
            }
        }
        return null;
    }

    private Decision invoke(Activation activation, Stage stage, Snapshot snapshot) {
        Handler handler = activation.registration.handlers.get(stage);
        if (handler == null) {
            return Decision.PASS;
        }
        try {
            Decision result = handler.apply(snapshot);
            return result == null ? Decision.PASS : result;
        } catch (Throwable error) {
            if (failures != null) {
                failures.failed(activation.registration, stage, error);
            }
            activation.failedStages.add(stage);
            if (stage == Stage.MOVEMENT) {
                activation.submittedMovement = null;
            } else if (stage == Stage.LOOK) {
                activation.submittedFacing = null;
            } else {
                activation.submittedActionKind = null;
                activation.submittedAction = null;
            }
            return Decision.PASS;
        }
    }

    public void movement(MovementInputFromOptions input, EntityPlayerSP localPlayer) {
        logicalMovement = null;
        if (localPlayer == null || input == null) {
            return;
        }
        Activation winner = winner(Stage.MOVEMENT);
        if (winner == null) {
            return;
        }
        Mode mode = winner.registration.definition.mode(Stage.MOVEMENT);
        if (mode == Mode.DISABLED) {
            logicalMovement = Movement.ZERO;
            Movement.ZERO.apply(input);
        } else if (mode == Mode.MANUAL) {
            Movement movement = winner.submittedCycle == cycle ? winner.submittedMovement : null;
            if (winner.registration.handlers.containsKey(Stage.MOVEMENT)) {
                Decision decision = invoke(winner, Stage.MOVEMENT, new Snapshot(Stage.MOVEMENT, localPlayer, cycle,
                        Movement.nativeState(input), null, null, -1, false));
                if (winner.failedStages.contains(Stage.MOVEMENT)) {
                    movement(input, localPlayer);
                    return;
                }
                if (decision.movement != null) {
                    movement = decision.movement;
                }
            }
            if (movement == null) {
                logicalMovement = Movement.ZERO;
                Movement.ZERO.apply(input);
            } else if (movement.sneak) {
                logicalMovement = movement;
                new Movement(movement.forward * 0.3F, movement.strafe * 0.3F, movement.jump, true).apply(input);
            } else {
                logicalMovement = movement;
                movement.apply(input);
            }
        } else {
            Movement nativeMovement = Movement.nativeState(input);
            Decision decision = invoke(winner, Stage.MOVEMENT,
                    new Snapshot(Stage.MOVEMENT, localPlayer, cycle, nativeMovement, null, null, -1, false));
            if (winner.failedStages.contains(Stage.MOVEMENT)) {
                movement(input, localPlayer);
            } else if (decision.movement != null) {
                logicalMovement = decision.movement;
                decision.movement.apply(input);
            } else if (!decision.pass) {
                logicalMovement = Movement.ZERO;
                Movement.ZERO.apply(input);
            }
        }
    }

    public Movement logicalMovement(Movement nativeLogical) {
        return logicalMovement == null ? nativeLogical : logicalMovement;
    }

    public boolean ownsLook(EntityPlayerSP localPlayer) {
        Activation active = winner(Stage.LOOK);
        return active != null && active.player == localPlayer;
    }

    private void resolveLookForTick() {
        Activation winner = winner(Stage.LOOK);
        if (winner == null) {
            return;
        }
        Mode mode = winner.registration.definition.mode(Stage.LOOK);
        if (mode == Mode.DISABLED) {
            facingOwner = winner;
            suppressNativeLook = true;
            return;
        }
        if (mode == Mode.MANUAL) {
            facingOwner = winner;
            suppressNativeLook = true;
            tickFacing = winner.submittedCycle == cycle ? winner.submittedFacing : null;
            if (winner.registration.handlers.containsKey(Stage.LOOK)) {
                Decision decision = invoke(winner, Stage.LOOK, new Snapshot(Stage.LOOK, winner.player, cycle, null,
                        new Facing(winner.player.rotationYaw, winner.player.rotationPitch), null, -1, false));
                if (winner.failedStages.contains(Stage.LOOK)) {
                    resolveLookForTick();
                    return;
                }
                if (decision.facing != null) {
                    tickFacing = decision.facing;
                }
            }
            return;
        }
        Facing nativeFacing = new Facing(winner.player.rotationYaw, winner.player.rotationPitch);
        Decision decision = invoke(winner, Stage.LOOK,
                new Snapshot(Stage.LOOK, winner.player, cycle, null, nativeFacing, null, -1, false));
        if (!winner.failedStages.contains(Stage.LOOK)) {
            facingOwner = winner;
            tickFacing = decision.facing;
            suppressNativeLook = decision.facing != null || !decision.pass;
        } else {
            resolveLookForTick();
        }
    }

    public boolean applyLook(EntityPlayerSP localPlayer, float yawDelta, float pitchDelta) {
        Activation winner = winner(Stage.LOOK);
        if (winner == null || winner != facingOwner || winner.player != localPlayer) {
            return false;
        }
        if (tickFacing != null) {
            tickFacing.apply(localPlayer);
        } else if (!suppressNativeLook) {
            return false;
        }
        return true;
    }

    public boolean allowWorldAction(EntityPlayerSP localPlayer, int button, boolean held) {
        Activation winner = winner(Stage.WORLD_ACTIONS);
        if (winner == null || winner.player != localPlayer) {
            return true;
        }
        Mode mode = winner.registration.definition.mode(Stage.WORLD_ACTIONS);
        if (mode == Mode.DISABLED) {
            return false;
        }
        if (mode == Mode.MANUAL) {
            String action = held ? "heldBreaking" : button == 0 ? "attack" : "use";
            boolean submitted = winner.submittedCycle == cycle && action.equals(winner.submittedActionKind)
                    && Boolean.TRUE.equals(winner.submittedAction);
            if (!winner.registration.handlers.containsKey(Stage.WORLD_ACTIONS)) {
                return submitted;
            }
            Decision decision = invoke(winner, Stage.WORLD_ACTIONS,
                    new Snapshot(Stage.WORLD_ACTIONS, localPlayer, cycle, null, null, action, button, held));
            return winner.failedStages.contains(Stage.WORLD_ACTIONS)
                    ? allowWorldAction(localPlayer, button, held)
                    : submitted || decision.pass;
        }
        Decision decision = invoke(winner, Stage.WORLD_ACTIONS, new Snapshot(Stage.WORLD_ACTIONS, localPlayer, cycle,
                null, null, held ? "heldBreaking" : button == 0 ? "attack" : "use", button, held));
        return winner.failedStages.contains(Stage.WORLD_ACTIONS)
                ? allowWorldAction(localPlayer, button, held)
                : decision.pass;
    }

    public boolean allowInventory(EntityPlayerSP localPlayer, String action, int code) {
        Activation winner = winner(Stage.INVENTORY);
        if (winner == null || winner.player != localPlayer) {
            return true;
        }
        Mode mode = winner.registration.definition.mode(Stage.INVENTORY);
        if (mode == Mode.DISABLED) {
            return false;
        }
        if (mode == Mode.MANUAL) {
            boolean submitted = winner.submittedCycle == cycle && action.equals(winner.submittedActionKind)
                    && Boolean.TRUE.equals(winner.submittedAction);
            if (!winner.registration.handlers.containsKey(Stage.INVENTORY)) {
                return submitted;
            }
            Decision decision = invoke(winner, Stage.INVENTORY,
                    new Snapshot(Stage.INVENTORY, localPlayer, cycle, null, null, action, code, false));
            return winner.failedStages.contains(Stage.INVENTORY)
                    ? allowInventory(localPlayer, action, code)
                    : submitted || decision.pass;
        }
        Decision decision = invoke(winner, Stage.INVENTORY,
                new Snapshot(Stage.INVENTORY, localPlayer, cycle, null, null, action, code, false));
        return winner.failedStages.contains(Stage.INVENTORY)
                ? allowInventory(localPlayer, action, code)
                : decision.pass;
    }

    public void unloadOwner(String owner) {
        for (Registration registration : new ArrayList<Registration>(registrations.values())) {
            if (registration.owner.equals(owner)) {
                registration.close();
            }
        }
    }

    public void clear() {
        for (Registration registration : new ArrayList<Registration>(registrations.values())) {
            registration.close();
        }
    }
}
