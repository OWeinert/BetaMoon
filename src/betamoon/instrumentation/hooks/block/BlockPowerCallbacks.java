package betamoon.instrumentation.hooks.block;

import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.BlockDefinition;
import net.minecraft.src.World;

/** Runtime callbacks loaded only after the game classes are available. */
public final class BlockPowerCallbacks {
    private BlockPowerCallbacks() {
    }

    public static int emission(World world, int x, int y, int z, int side) {
        BlockDefinition definition = BlockCallbackRegistry.get(world.getBlockId(x, y, z));
        if (definition == null || !definition.redstone.configured) {
            return 0;
        }
        return definition.redstone.power(world, x, y, z, side, false, definition) ? 1 : 0;
    }

    public static boolean result(boolean original, int emission) {
        return original || emission > 0;
    }
}
