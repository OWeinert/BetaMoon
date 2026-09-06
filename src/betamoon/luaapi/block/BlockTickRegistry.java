package betamoon.luaapi.block;

import betamoon.BetaMoonMain;
import betamoon.luaapi.utils.PositionI;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.wrappers.BlockWrapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Stores hot-reloadable Lua behavior for custom block update and display ticks. */
public final class BlockTickRegistry {
    private static final Map DEFINITIONS = new HashMap();

    private BlockTickRegistry() {
    }

    /** Validates and installs the tick definitions declared by the current script. */
    public static synchronized void register(final BlockWrapper block, LuaValue onTick,
        LuaValue onDisplayTick) {
        final Definition definition = Definition.parse(block.blockID, onTick, onDisplayTick);
        if (definition == null) return;
        final Integer key = Integer.valueOf(block.blockID);
        DEFINITIONS.put(key, definition);
        block.setRandomTicks(definition.mode == Mode.RANDOM);
        ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
            public void run() {
                synchronized (BlockTickRegistry.class) {
                    if (DEFINITIONS.get(key) == definition) {
                        DEFINITIONS.remove(key);
                        block.setRandomTicks(false);
                    }
                }
            }
        });
    }

    /** Starts scheduled behavior when a configured block enters the world. */
    public static void onBlockAdded(BlockWrapper block, World world, int x, int y, int z) {
        Definition definition = get(block.blockID);
        if (definition != null && definition.mode.isScheduled() && !world.multiplayerWorld) {
            world.scheduleBlockUpdate(x, y, z, block.blockID, definition.initialDelay);
        }
    }

    /** Runs a gameplay update and queues the next update for repeating modes. */
    public static void update(BlockWrapper block, World world, int x, int y, int z, Random random) {
        Definition definition = get(block.blockID);
        if (definition == null || definition.tickAction == null || world.multiplayerWorld) return;
        definition.invokeTick(world, x, y, z, random);
        if (definition.activeTick && definition.repeatDelay > 0
            && world.getBlockId(x, y, z) == block.blockID) {
            world.scheduleBlockUpdate(x, y, z, block.blockID, definition.repeatDelay);
        }
    }

    /** Runs client-side display attempts using Minecraft's random display callback. */
    public static void display(BlockWrapper block, World world, int x, int y, int z, Random random) {
        Definition definition = get(block.blockID);
        if (definition == null || definition.displayAction == null || !definition.activeDisplay) return;
        for (int i = 0; i < definition.displayAttempts; i++) {
            if (random.nextDouble() <= definition.displayChance) {
                definition.invokeDisplay(world, x, y, z, random);
                if (!definition.activeDisplay) return;
            }
        }
    }

    private static synchronized Definition get(int blockId) {
        return (Definition) DEFINITIONS.get(Integer.valueOf(blockId));
    }

    private enum Mode {
        DEFAULT, RANDOM, SCHEDULED;

        private boolean isScheduled() {
            return this == DEFAULT || this == SCHEDULED;
        }
    }

    private static final class Definition {
        private final int blockId;
        private final String owner;
        private final Mode mode;
        private final LuaValue tickAction;
        private final int initialDelay;
        private final int repeatDelay;
        private final LuaValue displayAction;
        private final double displayChance;
        private final int displayAttempts;
        private boolean activeTick = true;
        private boolean activeDisplay = true;

        private Definition(int blockId, String owner, Mode mode, LuaValue tickAction,
            int initialDelay, int repeatDelay, LuaValue displayAction, double displayChance,
            int displayAttempts) {
            this.blockId = blockId;
            this.owner = owner;
            this.mode = mode;
            this.tickAction = tickAction;
            this.initialDelay = initialDelay;
            this.repeatDelay = repeatDelay;
            this.displayAction = displayAction;
            this.displayChance = displayChance;
            this.displayAttempts = displayAttempts;
        }

        private static Definition parse(int blockId, LuaValue tick, LuaValue display) {
            if (tick.isnil() && display.isnil()) return null;
            String owner = LuaScriptRegistry.getCurrentScriptFile();
            Mode mode = null;
            LuaValue tickAction = null;
            int initialDelay = 0;
            int repeatDelay = 0;
            if (!tick.isnil()) {
                requireTable(tick, "onTick");
                String name = required(tick, "mode", "onTick").checkjstring().toLowerCase();
                if ("default".equals(name)) {
                    mode = Mode.DEFAULT;
                    initialDelay = repeatDelay = 1;
                } else if ("random".equals(name)) {
                    mode = Mode.RANDOM;
                } else if ("scheduled".equals(name)) {
                    mode = Mode.SCHEDULED;
                    LuaValue schedule = required(tick, "schedule", "scheduled onTick");
                    requireTable(schedule, "onTick.schedule");
                    initialDelay = positiveInt(required(schedule, "delay", "onTick.schedule"),
                        "onTick.schedule.delay");
                    LuaValue repeat = schedule.get("repeatEvery");
                    repeatDelay = repeat.isnil() ? 0 : positiveInt(repeat, "onTick.schedule.repeatEvery");
                } else {
                    throw new LuaError("onTick.mode must be 'default', 'random', or 'scheduled'.");
                }
                if (mode != Mode.SCHEDULED && !tick.get("schedule").isnil()) {
                    throw new LuaError("onTick.schedule is only valid when mode is 'scheduled'.");
                }
                tickAction = required(tick, "action", "onTick");
                if (!tickAction.isfunction()) throw new LuaError("onTick.action must be a function.");
            }
            LuaValue displayAction = null;
            double chance = 1.0D;
            int attempts = 1;
            if (!display.isnil()) {
                requireTable(display, "onDisplayTick");
                displayAction = required(display, "action", "onDisplayTick");
                if (!displayAction.isfunction()) throw new LuaError("onDisplayTick.action must be a function.");
                LuaValue chanceValue = display.get("chance");
                if (!chanceValue.isnil()) chance = chanceValue.checkdouble();
                if (Double.isNaN(chance) || Double.isInfinite(chance)
                    || chance < 0.0D || chance > 1.0D) {
                    throw new LuaError("onDisplayTick.chance must be between 0 and 1.");
                }
                LuaValue attemptsValue = display.get("attempts");
                if (!attemptsValue.isnil()) attempts = positiveInt(attemptsValue, "onDisplayTick.attempts");
                if (attempts > 64) throw new LuaError("onDisplayTick.attempts cannot exceed 64.");
            }
            return new Definition(blockId, owner, mode, tickAction, initialDelay, repeatDelay,
                displayAction, chance, attempts);
        }

        private void invokeTick(World world, int x, int y, int z, Random random) {
            if (!activeTick) return;
            try {
                tickAction.call(new TickContext(world, x, y, z, blockId, random, true));
            } catch (Throwable error) {
                activeTick = false;
                report(owner, "onTick", error);
            }
        }

        private void invokeDisplay(World world, int x, int y, int z, Random random) {
            try {
                displayAction.call(new TickContext(world, x, y, z, blockId, random, false));
            } catch (Throwable error) {
                activeDisplay = false;
                report(owner, "onDisplayTick", error);
            }
        }
    }

    /** Lua-facing context shared by gameplay and display tick actions. */
    private static final class TickContext extends LuaTable {
        private final World world;
        private final int x;
        private final int y;
        private final int z;
        private final int blockId;

        private TickContext(World world, int x, int y, int z, int blockId, Random random,
            boolean gameplay) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockId = blockId;
            set("x", x);
            set("y", y);
            set("z", z);
            set("position", new PositionI(x, y, z));
            set("id", blockId);
            set("damage", world.getBlockMetadata(x, y, z));
            set("world", new WorldAccess(world, gameplay));
            set("random", new RandomNumber(random));
            if (gameplay) set("schedule", new Schedule(this));
        }
    }

    /** Small safe world facade used by tick actions. */
    private static final class WorldAccess extends LuaTable {
        private final World world;

        private WorldAccess(World world, boolean gameplay) {
            this.world = world;
            set("getBlock", new GetBlock(this));
            if (gameplay) set("setBlock", new SetBlock(this));
            set("spawnParticle", new SpawnParticle(this));
            set("playSound", new PlaySound(this));
        }
    }

    private static final class GetBlock extends VarArgFunction {
        private final WorldAccess owner;
        private GetBlock(WorldAccess owner) { this.owner = owner; }
        public Varargs invoke(Varargs args) {
            int offset = args.arg1() == owner ? 1 : 0;
            int x = args.arg(1 + offset).checkint();
            int y = args.arg(2 + offset).checkint();
            int z = args.arg(3 + offset).checkint();
            LuaTable result = new LuaTable();
            result.set("id", owner.world.getBlockId(x, y, z));
            result.set("damage", owner.world.getBlockMetadata(x, y, z));
            return result;
        }
    }

    private static final class SetBlock extends VarArgFunction {
        private final WorldAccess owner;
        private SetBlock(WorldAccess owner) { this.owner = owner; }
        public Varargs invoke(Varargs args) {
            int offset = args.arg1() == owner ? 1 : 0;
            int x = args.arg(1 + offset).checkint();
            int y = args.arg(2 + offset).checkint();
            int z = args.arg(3 + offset).checkint();
            int id = resourceId(args.arg(4 + offset));
            LuaValue damage = args.arg(5 + offset);
            boolean changed = damage.isnil() ? owner.world.setBlockWithNotify(x, y, z, id)
                : owner.world.setBlockAndMetadataWithNotify(x, y, z, id, damage.checkint());
            return LuaValue.valueOf(changed);
        }
    }

    private static final class SpawnParticle extends VarArgFunction {
        private final WorldAccess owner;
        private SpawnParticle(WorldAccess owner) { this.owner = owner; }
        public Varargs invoke(Varargs args) {
            int offset = args.arg1() == owner ? 1 : 0;
            String name = args.arg(1 + offset).checkjstring();
            LuaValue def = args.arg(2 + offset);
            requireTable(def, "particle");
            owner.world.spawnParticle(name, requiredNumber(def, "x"), requiredNumber(def, "y"),
                requiredNumber(def, "z"), def.get("velocityX").optdouble(0.0D),
                def.get("velocityY").optdouble(0.0D), def.get("velocityZ").optdouble(0.0D));
            return LuaValue.NIL;
        }
    }

    private static final class PlaySound extends VarArgFunction {
        private final WorldAccess owner;
        private PlaySound(WorldAccess owner) { this.owner = owner; }
        public Varargs invoke(Varargs args) {
            int offset = args.arg1() == owner ? 1 : 0;
            String name = args.arg(1 + offset).checkjstring();
            LuaValue def = args.arg(2 + offset);
            requireTable(def, "sound");
            owner.world.playSoundEffect(requiredNumber(def, "x"), requiredNumber(def, "y"),
                requiredNumber(def, "z"), name, (float) def.get("volume").optdouble(1.0D),
                (float) def.get("pitch").optdouble(1.0D));
            return LuaValue.NIL;
        }
    }

    private static final class Schedule extends VarArgFunction {
        private final TickContext context;
        private Schedule(TickContext context) { this.context = context; }
        public Varargs invoke(Varargs args) {
            int offset = args.arg1() == context ? 1 : 0;
            int delay = positiveInt(args.arg(1 + offset), "tick schedule delay");
            context.world.scheduleBlockUpdate(context.x, context.y, context.z, context.blockId, delay);
            return LuaValue.NIL;
        }
    }

    private static final class RandomNumber extends VarArgFunction {
        private final Random random;
        private RandomNumber(Random random) { this.random = random; }
        public Varargs invoke(Varargs args) { return LuaValue.valueOf(random.nextDouble()); }
    }

    private static int resourceId(LuaValue value) {
        if (value.isnumber()) return value.checkint();
        LuaValue id = value.get("id");
        if (id.isnil()) throw new LuaError("Expected a block reference or numeric block ID.");
        return id.checkint();
    }

    private static void requireTable(LuaValue value, String name) {
        if (!value.istable()) throw new LuaError(name + " must be a table.");
    }

    private static LuaValue required(LuaValue table, String key, String name) {
        LuaValue value = table.get(key);
        if (value.isnil()) throw new LuaError(name + " requires '" + key + "'.");
        return value;
    }

    private static int positiveInt(LuaValue value, String name) {
        if (!value.isint()) throw new LuaError(name + " must be a whole number.");
        int result = value.checkint();
        if (result <= 0) throw new LuaError(name + " must be a positive whole number.");
        return result;
    }

    private static double requiredNumber(LuaValue table, String key) {
        LuaValue value = table.get(key);
        if (value.isnil()) throw new LuaError("Definition requires '" + key + "'.");
        return value.checkdouble();
    }

    private static void report(String owner, String callback, Throwable error) {
        String message = callback + " was disabled after an error: "
            + (error.getMessage() == null ? error.toString() : error.getMessage());
        LuaScriptErrors.add(owner, message);
        BetaMoonMain.LOGGER.warning((owner == null ? "Lua script" : owner) + ": " + message);
    }
}
