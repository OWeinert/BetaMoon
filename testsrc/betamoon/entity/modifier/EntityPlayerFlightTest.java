package betamoon.entity.modifier;

import betamoon.client.control.input.ClientInputRuntime;
import betamoon.client.control.input.MotionInputObservation;
import betamoon.client.control.input.PlayerControllerRuntime;
import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import betamoon.entity.motion.NativeEntityFields;
import betamoon.luaapi.player.PlayersApi;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.player.LocalPlayerAccess;
import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.Achievement;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.GameSettings;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.MovementInputFromOptions;
import net.minecraft.src.Session;
import net.minecraft.src.StatBase;
import net.minecraft.src.StatFileWriter;
import net.minecraft.src.StatList;
import net.minecraft.src.UnexpectedThrowable;
import net.minecraft.src.World;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;
import sun.misc.Unsafe;

/**
 * A Lua-authored flight acceptance run through actual transformed local-player
 * updates.
 */
final class EntityPlayerFlightTest {
    private static final String OWNER = "player-flight-test.lua";

    private EntityPlayerFlightTest() {
    }

    static void verify() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        TestMinecraft minecraft = (TestMinecraft) unsafe.allocateInstance(TestMinecraft.class);
        TestStats stats = (TestStats) unsafe.allocateInstance(TestStats.class);
        stats.counts = new HashMap<>();
        minecraft.statFileWriter = stats;
        minecraft.gameSettings = new GameSettings();
        EntityMotionApiTest.FixtureWorld world = new EntityMotionApiTest.FixtureWorld();
        minecraft.theWorld = world;
        LocalPlayerAccess.initialize(new LocalPlayerAccess.Provider() {
            public EntityPlayer player() {
                return minecraft.thePlayer;
            }

            public World world() {
                return minecraft.theWorld;
            }
        });
        Globals lua = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        PlayersApi.attach(module);
        lua.set("betamoon", module);
        execute(lua, "flying=true; attaches=0; detaches=0; observations=0; "
                + "flight=betamoon.players:modifyLocal{key='test:modifier/flight',"
                + "onAttach=function(ctx) attaches=attaches+1; assert(ctx.state.steps==nil); "
                + "ctx.state.steps=0; generation=ctx.generation end,"
                + "onDetach=function(ctx,event) detaches=detaches+1; assert(ctx.entity==nil); reason=event.reason end,"
                + "onMotion=function(ctx) ctx.state.steps=ctx.state.steps+1; input=ctx.input; "
                + "assert(not pcall(function() ctx.entity:setVelocity(0,0,0) end)); "
                + "assert(not pcall(function() flight:setEnabled(false) end)); "
                + "assert(not pcall(function() betamoon.players:withLocal(function(player) "
                + "player:setVelocity(0,0,0) end) end)); " + "if not flying then return nil end; "
                + "local vertical=ctx.input.jump and 0.3 or (ctx.input.sneak and -0.3 or 0); "
                + "return {mode='manual',velocity={x=ctx.input.strafe*0.4,y=vertical,z=ctx.input.forward*0.4},"
                + "fallDistance=0,rules={physics={trackFallDistance=false},damage={fallMultiplier=0},"
                + "movement={sneakSlowdown=1,sneakEdgeGuard=false,sneakBodyAdjustment=false}}} end,"
                + "onAfterMotion=function(ctx,result) observations=observations+1; last=result end}; "
                + "assert(flight:getStatus().state=='awaiting_player')");
        EntityPlayerSP player = player(minecraft, world);
        tick(minecraft);
        double startY = player.posY;
        for (int i = 0; i < 10; i++) {
            tick(minecraft);
        }
        near(player.posY, startY);
        near(player.motionY, 0);
        execute(lua, "assert(attaches==1 and observations==11 and input.available)");
        key(minecraft, minecraft.gameSettings.keyBindForward.keyCode, true, true);
        tick(minecraft);
        double forwardZ = player.posZ;
        execute(lua, "assert(input.forward==1 and input.actions.forward.pressed and input.actions.forward.held); "
                + "assert(#input.transitions==1 and input.transitions[1].action=='forward')");
        tick(minecraft);
        near(player.posZ, forwardZ + 0.4);
        execute(lua, "assert(not input.actions.forward.pressed and #input.transitions==0)");
        key(minecraft, minecraft.gameSettings.keyBindForward.keyCode, false, true);
        key(minecraft, minecraft.gameSettings.keyBindJump.keyCode, true, true);
        tick(minecraft);
        near(player.posY, startY + 0.3);
        require(stats.counts.get(StatList.jumpStat) == null, "Manual ascent consumed a native jump statistic");
        key(minecraft, minecraft.gameSettings.keyBindJump.keyCode, false, true);
        key(minecraft, minecraft.gameSettings.keyBindSneak.keyCode, true, true);
        key(minecraft, minecraft.gameSettings.keyBindForward.keyCode, true, true);
        tick(minecraft);
        near(player.posY, startY);
        near(player.movementInput.moveForward, 1);
        require(player.ySize == 0, "Flight sneak unexpectedly adjusted the body");
        key(minecraft, minecraft.gameSettings.keyBindSneak.keyCode, false, true);
        key(minecraft, minecraft.gameSettings.keyBindForward.keyCode, false, true);
        key(minecraft, minecraft.gameSettings.keyBindJump.keyCode, true, false);
        tick(minecraft);
        execute(lua, "assert(not input.actions.jump.pressed and not input.jump)");
        tick(minecraft, false);
        execute(lua, "assert(not input.available and not input.actions.forward.held and #input.transitions==0)");
        int oldJump = minecraft.gameSettings.keyBindJump.keyCode;
        minecraft.gameSettings.keyBindJump.keyCode = 44;
        tick(minecraft);
        key(minecraft, oldJump, true, true);
        tick(minecraft);
        execute(lua, "assert(not input.jump and not input.actions.jump.pressed)");
        key(minecraft, 44, true, true);
        tick(minecraft);
        execute(lua, "assert(input.jump and input.actions.jump.pressed)");
        key(minecraft, 44, false, true);
        minecraft.gameSettings.keyBindJump.keyCode = oldJump;
        minecraft.currentScreen = new GuiScreen();
        tick(minecraft);
        execute(lua, "assert(not input.available and input.forward==0 and #input.transitions==0)");
        minecraft.currentScreen = null;
        tick(minecraft);
        execute(lua, "flying=false");
        tick(minecraft);
        require(player.motionY < 0, "Yielding flight did not restore gravity");
        execute(lua, "flight:setEnabled(false)");
        double falling = player.posY;
        tick(minecraft);
        require(player.posY < falling, "Disabling flight did not restore native falling");
        execute(lua, "flying=true; flight:setEnabled(true)");
        tick(minecraft);
        near(player.motionY, 0);
        NativeEntityFields.fallDistance(player, 30);
        tick(minecraft);
        near(NativeEntityFields.fallDistance(player), 0);
        long generation = lua.get("generation").tolong();
        player.health = 0;
        player = player(minecraft, world);
        tick(minecraft);
        execute(lua, "assert(attaches==2 and detaches==1 and reason=='death')");
        require(lua.get("generation").tolong() != generation, "Respawn reused binding generation");
        EntityMotionApiTest.FixtureWorld nextWorld = new EntityMotionApiTest.FixtureWorld();
        minecraft.theWorld = nextWorld;
        player = player(minecraft, nextWorld);
        tick(minecraft);
        execute(lua, "assert(attaches==3 and detaches==2 and reason=='world_changed')");
        execute(lua, "flight:update{onMotion=function() error('flight producer fixture') end}");
        tick(minecraft);
        require(player.motionY < 0, "Failed flight producer did not restore native gravity in the same step");
        execute(lua, "assert(flight:getStatus().state=='failed'); "
                + "flight:update{onMotion=function() return {mode='manual',velocity={x=0,y=0,z=0}} end}");
        tick(minecraft);
        near(player.motionY, 0);
        execute(lua, "assert(attaches==4 and flight:getStatus().applied)");
        nextWorld.multiplayerWorld = true;
        EntityModifierRuntime.synchronize(nextWorld, player);
        execute(lua, "assert(flight:getStatus().state=='unsupported_environment' and not flight:getStatus().applied)");
        nextWorld.multiplayerWorld = false;
        EntityModifierRuntime.synchronize(nextWorld, player);
        ScriptResourceTracker.unload(OWNER);
        tick(minecraft);
        require(player.motionY < 0, "Owner unload did not restore native gravity");
        require(!EntityModifierRuntime.hasSources(player), "Owner unload retained player source");
        nativeJumpAndLook(minecraft, lua, stats);
        ClientInputRuntime.beginCycle(null);
    }

    private static void nativeJumpAndLook(Minecraft minecraft, Globals lua, TestStats stats) {
        execute(lua, "rule=betamoon.players:modifyLocal{key='test:modifier/jump',movement={jumpMultiplier=0},"
                + "onForces=function(ctx) preparedJump=ctx.movement.velocity.y end}");
        EntityPlayerSP player = minecraft.thePlayer;
        ground(player);
        key(minecraft, minecraft.gameSettings.keyBindJump.keyCode, true, true);
        tick(minecraft);
        require(stats.counts.get(StatList.jumpStat) == 1, "Permitted zero jump lost native bookkeeping");
        execute(lua, "rule:update{movement={jumpEnabled=false}}");
        ground(player);
        tick(minecraft);
        require(stats.counts.get(StatList.jumpStat) == 1, "Vetoed jump consumed native bookkeeping");
        execute(lua, "rule:update{movement={jumpEnabled=true,jumpMultiplier=2}}");
        ground(player);
        tick(minecraft);
        require(stats.counts.get(StatList.jumpStat) == 2, "Scaled jump consumed bookkeeping more than once");
        near(lua.get("preparedJump").todouble(), (double) 0.42F * 2);
        near(player.motionY, ((double) 0.42F * 2 - 0.08) * (double) 0.98F);
        key(minecraft, minecraft.gameSettings.keyBindJump.keyCode, false, true);
        execute(lua,
                "rule:close(); look=betamoon.players:modifyLocal{key='test:modifier/look',"
                        + "onMotion=function() return {mode='manual',velocity={x=0,y=0,z=0},rotation={yaw=60}} end,"
                        + "onAfterMotion=function(ctx,result) lookResult=result end}");
        EnumMap<PlayerControllerRuntime.Stage, PlayerControllerRuntime.Mode> modes = new EnumMap<>(
                PlayerControllerRuntime.Stage.class);
        modes.put(PlayerControllerRuntime.Stage.LOOK, PlayerControllerRuntime.Mode.DISABLED);
        PlayerControllerRuntime.Registration control = ClientInputRuntime.controllers()
                .register(new PlayerControllerRuntime.Definition(
                        ContentKey.parseForType("test:input_controller/look", ContentType.INPUT_CONTROLLER), modes),
                        OWNER);
        control.activate(player, 0);
        tick(minecraft);
        execute(lua, "assert(look:getStatus().applied and lookResult.blockedReason=='look_owned' "
                + "and lookResult.sourceKey==nil)");
        control.close();
        tick(minecraft);
        near(player.rotationYaw, 60);
        execute(lua, "assert(lookResult.sourceKey=='test:modifier/look'); look:close()");
        ScriptResourceTracker.unload(OWNER);
    }

    private static void ground(EntityPlayerSP player) {
        player.ySize = 0;
        player.setPosition(8, 64 + player.yOffset, 8);
        player.motionX = player.motionY = player.motionZ = 0;
        player.onGround = true;
    }

    private static EntityPlayerSP player(Minecraft minecraft, World world) {
        EntityPlayerSP player = new EntityPlayerSP(minecraft, world, new Session("motion-fixture", ""), 0);
        player.movementInput = new MovementInputFromOptions(minecraft.gameSettings);
        player.setPosition(8, 80, 8);
        minecraft.thePlayer = player;
        return player;
    }

    private static void tick(Minecraft minecraft) {
        tick(minecraft, true);
    }

    private static void tick(Minecraft minecraft, boolean focused) {
        MotionInputObservation.begin(minecraft, focused);
        ClientInputRuntime.router().beginCycle(minecraft.theWorld, minecraft.thePlayer, minecraft.currentScreen,
                focused);
        ClientInputRuntime.controllers().beginCycle(minecraft.theWorld, minecraft.thePlayer, minecraft.currentScreen,
                focused);
        minecraft.thePlayer.onLivingUpdate();
    }

    private static void key(Minecraft minecraft, int key, boolean pressed, boolean routed) {
        MotionInputObservation.accept(key, pressed, routed);
        if (routed || !pressed) {
            minecraft.thePlayer.handleKeyPress(key, pressed);
        }
    }

    private static void execute(Globals lua, String code) {
        ScriptExecution.withOwner(OWNER, () -> lua.load(code).call());
    }

    private static void near(double actual, double expected) {
        require(Math.abs(actual - expected) < 0.00001, actual + " != " + expected);
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static final class TestMinecraft extends Minecraft {
        private TestMinecraft() {
            super(null, null, null, 1, 1, false);
        }

        public void displayUnexpectedThrowable(UnexpectedThrowable throwable) {
            throw new AssertionError("Unexpected client crash");
        }
    }

    private static final class TestStats extends StatFileWriter {
        private Map<StatBase, Integer> counts;
        // Unsafe allocation avoids the native statistics synchronizer and file I/O in
        // this headless fixture.
        private TestStats() {
            super(null, null);
        }

        public boolean hasAchievementUnlocked(Achievement achievement) {
            return true;
        }

        public void readStat(StatBase stat, int count) {
            Integer previous = counts.get(stat);
            counts.put(stat, (previous == null ? 0 : previous) + count);
        }
    }
}
