package betamoon.client.control.input;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.GameSettings;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.MovementInputFromOptions;
import net.minecraft.src.Session;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;

/** Stage priority, default denial, failure fallback and lifecycle behavior. */
public final class PlayerControllerRuntimeTest {
    private PlayerControllerRuntimeTest() {
    }

    public static void main(String[] args) {
        World world = new TestWorld();
        EntityPlayerSP player = new EntityPlayerSP(null, world, new Session("test", ""), 0);
        List<String> failures = new ArrayList<String>();
        PlayerControllerRuntime runtime = new PlayerControllerRuntime(
                (registration, stage, error) -> failures.add(registration.owner() + ":" + stage.luaName()));
        runtime.beginCycle(world, player, null, true);

        EnumMap<PlayerControllerRuntime.Stage, PlayerControllerRuntime.Mode> lowModes = modes();
        lowModes.put(PlayerControllerRuntime.Stage.MOVEMENT, PlayerControllerRuntime.Mode.MANUAL);
        lowModes.put(PlayerControllerRuntime.Stage.WORLD_ACTIONS, PlayerControllerRuntime.Mode.MANUAL);
        PlayerControllerRuntime.Registration low = runtime.register(definition("test:low", lowModes), "low.lua");
        PlayerControllerRuntime.Activation lowActive = low.activate(player, 0);

        EnumMap<PlayerControllerRuntime.Stage, PlayerControllerRuntime.Mode> highModes = modes();
        highModes.put(PlayerControllerRuntime.Stage.MOVEMENT, PlayerControllerRuntime.Mode.MODIFY);
        highModes.put(PlayerControllerRuntime.Stage.LOOK, PlayerControllerRuntime.Mode.DISABLED);
        PlayerControllerRuntime.Registration high = runtime.register(definition("test:high", highModes), "high.lua");
        high.on(PlayerControllerRuntime.Stage.MOVEMENT, event -> new PlayerControllerRuntime.Decision(
                new PlayerControllerRuntime.Movement(0.5F, -0.5F, false, false), null, true));
        PlayerControllerRuntime.Activation highActive = high.activate(player, 20);

        MovementInputFromOptions input = new MovementInputFromOptions(new GameSettings());
        input.moveForward = 1;
        runtime.movement(input, player);
        require(input.moveForward == 0.5F && input.moveStrafe == -0.5F,
                "Higher priority modify stage replaces native movement");
        require(!runtime.applyLook(player, 10, 5), "Look is not claimed until the following input cycle");
        runtime.beginCycle(world, player, null, true);
        require(runtime.applyLook(player, 10, 5), "Look can be claimed independently from movement");
        require(!runtime.allowWorldAction(player, 0, false)
                && !runtime.allowWorldAction(player, 0, true),
                "Manual world actions deny both discrete clicks and held breaking");

        lowActive.submit(new PlayerControllerRuntime.Movement(1, 0, false, false), null, "attack", true);
        runtime.beginCycle(world, player, null, true);
        require(runtime.allowWorldAction(player, 0, false)
                && !runtime.allowWorldAction(player, 0, true),
                "An attack pass cannot also enable held breaking");
        highActive.close();
        input.moveForward = 0;
        runtime.movement(input, player);
        require(input.moveForward == 1, "Closing one stage owner reveals lower manual intent");
        runtime.beginCycle(world, player, null, true);
        input.moveForward = 1;
        runtime.movement(input, player);
        require(input.moveForward == 0 && !runtime.allowWorldAction(player, 0, true),
                "Manual movement and action submissions do not remain stuck");

        PlayerControllerRuntime.Registration failing = runtime.register(definition("test:failing", highModes),
                "failing.lua");
        failing.on(PlayerControllerRuntime.Stage.MOVEMENT, event -> {
            throw new IllegalStateException("expected");
        });
        PlayerControllerRuntime.Activation failedActive = failing.activate(player, 100);
        input.moveForward = 1;
        runtime.movement(input, player);
        require(input.moveForward == 0 && failedActive.isActive()
                && failures.size() == 1 && "failing.lua:movement".equals(failures.get(0)),
                "A failed stage yields to lower control without closing unrelated stages");
        runtime.beginCycle(world, player, new Object(), true);
        require(!failedActive.isActive() && !lowActive.isActive(), "Opening a screen closes every activation");
        input.moveForward = 0.75F;
        runtime.movement(input, player);
        require(input.moveForward == 0.75F && runtime.allowWorldAction(player, 0, true),
                "No active controller is native equivalent");
        runtime.beginCycle(world, player, null, true);
        PlayerControllerRuntime.Activation focusActive = low.activate(player, 0);
        runtime.beginCycle(world, player, null, false);
        require(!focusActive.isActive(), "Focus loss closes manual control");
        runtime.beginCycle(world, player, null, true);
        PlayerControllerRuntime.Activation deathActive = low.activate(player, 0);
        player.health = 0;
        runtime.beginCycle(world, player, null, true);
        require(!deathActive.isActive(), "Player death closes manual control");
        System.out.println("Player controllers passed: priority, stage isolation, click safety and lifecycle.");
    }

    private static EnumMap<PlayerControllerRuntime.Stage, PlayerControllerRuntime.Mode> modes() {
        return new EnumMap<PlayerControllerRuntime.Stage, PlayerControllerRuntime.Mode>(
                PlayerControllerRuntime.Stage.class);
    }

    private static PlayerControllerRuntime.Definition definition(String key,
            EnumMap<PlayerControllerRuntime.Stage, PlayerControllerRuntime.Mode> modes) {
        return new PlayerControllerRuntime.Definition(
                ContentKey.parseForType(key, ContentType.INPUT_CONTROLLER), modes);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestWorld extends World {
        private TestWorld() {
            super(null, "controller_test", new WorldProvider() {
            }, 0L);
        }

        @Override
        protected IChunkProvider getChunkProvider() {
            return null;
        }
    }
}
