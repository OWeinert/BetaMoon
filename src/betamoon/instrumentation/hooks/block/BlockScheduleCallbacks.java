package betamoon.instrumentation.hooks.block;

import betamoon.luaapi.block.BlockTickRegistry;
import net.minecraft.src.Chunk;
import net.minecraft.src.World;

public final class BlockScheduleCallbacks {
    private BlockScheduleCallbacks() {
    }

    public static int entering() {
        return 0;
    }

    public static void loaded(Chunk chunk) {
        BlockTickRegistry.chunkLoaded(chunk);
    }

    public static void unloaded(Chunk chunk) {
        BlockTickRegistry.chunkUnloaded(chunk);
    }

    public static int retry(World world) {
        BlockTickRegistry.retryWaitingSchedules(world);
        return 0;
    }

    public static void afterRetry() {
    }
}
