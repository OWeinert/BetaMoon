package betamoon.luaapi.block;

import betamoon.BetaMoonMain;
import betamoon.luaapi.resource.OverrideManager;
import betamoon.luamodloader.LuaScriptErrors;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.src.Block;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Script-owned display overrides. Property layering supplies the effective
 * callback for each block.
 */
public final class BlockDisplayTickOverrides {
    private static final Map<Integer, Callback> CALLBACKS = new ConcurrentHashMap<Integer, Callback>();

    public static final OverrideManager.PropertyAdapter<Block, BlockDisplayOverrideDefinition> ADAPTER = new OverrideManager.PropertyAdapter<Block, BlockDisplayOverrideDefinition>() {
        public BlockDisplayOverrideDefinition read(Block target) {
            Callback callback = CALLBACKS.get(((Block) target).blockID);
            return callback == null ? null : callback.definition;
        }

        public void write(Block target, BlockDisplayOverrideDefinition value) {
            int id = ((Block) target).blockID;
            if (value == null) {
                CALLBACKS.remove(id);
            } else {
                CALLBACKS.put(id, new Callback((BlockDisplayOverrideDefinition) value));
            }
        }
    };

    private BlockDisplayTickOverrides() {
    }

    /**
     * Called by the world dispatch hook with the original virtual-call arguments.
     */
    public static void display(Block block, World world, int x, int y, int z, Random random) {
        Callback callback = CALLBACKS.get(block.blockID);
        if (callback == null || callback.disabled) {
            block.randomDisplayTick(world, x, y, z, random);
            return;
        }
        BaseCall base = new BaseCall(block, world, x, y, z, random);
        try {
            BlockTickRegistry.invokeDisplayOverride(callback.definition.action, block.blockID, world, x, y, z, random,
                    base);
        } catch (Throwable error) {
            callback.disabled = true;
            String message = "onDisplayTick override disabled after an error: " + error;
            LuaScriptErrors.add(callback.definition.owner, message);
            BetaMoonMain.LOGGER.warning(callback.definition.owner + ": " + message);
            // Keep vanilla effects after a Lua failure, without repeating an already
            // invoked base call.
            if (!base.called) {
                base.callOriginal();
            }
        } finally {
            base.active = false;
        }
    }

    private static final class Callback {
        private final BlockDisplayOverrideDefinition definition;
        private boolean disabled;

        private Callback(BlockDisplayOverrideDefinition definition) {
            this.definition = definition;
        }
    }

    private static final class BaseCall extends VarArgFunction {
        private final Block block;
        private final World world;
        private final int x;
        private final int y;
        private final int z;
        private final Random random;
        private boolean active = true;
        private boolean called;

        private BaseCall(Block block, World world, int x, int y, int z, Random random) {
            this.block = block;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.random = random;
        }

        public Varargs invoke(Varargs arguments) {
            if (!active) {
                throw new LuaError("ctx:base() expired when the callback returned");
            }
            if (called) {
                throw new LuaError("ctx:base() may only be called once per display tick");
            }
            callOriginal();
            return NONE;
        }

        private void callOriginal() {
            called = true;
            block.randomDisplayTick(world, x, y, z, random);
        }
    }
}
