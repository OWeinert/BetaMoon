package betamoon.instrumentation.hooks.worldgen;

import betamoon.worldgen.terrain.WorldGenerationRuntime;
import net.minecraft.src.Chunk;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.ISaveHandler;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;

/** Typed generation boundaries, independent of Lua callback dispatch. */
public final class WorldGeneratorCallbacks {
    private WorldGeneratorCallbacks() {
    }

    public static void completed(World world) {
        WorldGenerationRuntime.requireSelectedProvider(world);
    }

    public static int entering() {
        return 0;
    }

    public static int initialSpawn(World world) {
        return WorldGenerationRuntime.initialSpawn(world);
    }

    public static int spawnRecheck(World world) {
        return WorldGenerationRuntime.skipNativeSpawnRecheck(world);
    }

    public static void completedSpawnRecheck() {
    }

    public static void chunkLoaded(Chunk chunk) {
        WorldGenerationRuntime.chunkLoaded(chunk);
    }

    public static int prepare(World world, ISaveHandler saveHandler) {
        WorldGenerationRuntime.prepare(world, saveHandler);
        return 0;
    }

    public static IChunkProvider select(WorldProvider provider, IChunkProvider nativeProvider) {
        return WorldGenerationRuntime.select(provider, nativeProvider);
    }

    public static void spawnSelected(World world) {
        WorldGenerationRuntime.recordNativeSpawn(world);
    }
}
