package betamoon.instrumentation.hooks.worldgen;

import betamoon.entity.EntityNaturalSpawner;
import betamoon.worldgen.GenerationStage;
import betamoon.worldgen.terrain.NativePopulationRuntime;
import betamoon.worldgen.terrain.WorldGenerationRuntime;
import java.util.Random;
import net.minecraft.src.SpawnerAnimals;
import net.minecraft.src.World;
import net.minecraft.src.WorldGenCactus;
import net.minecraft.src.WorldGenClay;
import net.minecraft.src.WorldGenDeadBush;
import net.minecraft.src.WorldGenDungeons;
import net.minecraft.src.WorldGenFlowers;
import net.minecraft.src.WorldGenLakes;
import net.minecraft.src.WorldGenLiquids;
import net.minecraft.src.WorldGenMinable;
import net.minecraft.src.WorldGenPumpkin;
import net.minecraft.src.WorldGenReed;
import net.minecraft.src.WorldGenTallGrass;
import net.minecraft.src.WorldGenerator;

/**
 * Typed redirects preserve the original receiver and RNG when a category is
 * enabled.
 */
public final class WorldPopulationCallbacks {
    private WorldPopulationCallbacks() {
    }

    public static int spawnAnimals(World world, boolean hostile, boolean peaceful) {
        if (WorldGenerationRuntime.nativeCategoryEnabled(world, "animals")) {
            return SpawnerAnimals.performSpawning(world, hostile, peaceful);
        }
        return WorldGenerationRuntime.stageEnabled(world, GenerationStage.ENTITIES)
                ? EntityNaturalSpawner.spawn(world, hostile, peaceful)
                : 0;
    }

    public static boolean generate(WorldGenLakes generator, World world, Random random, int x, int y, int z) {
        return NativePopulationRuntime.enabled("lakes") && generator.generate(world, random, x, y, z);
    }

    public static boolean generate(WorldGenDungeons generator, World world, Random random, int x, int y, int z) {
        return NativePopulationRuntime.enabled("dungeons") && generator.generate(world, random, x, y, z);
    }

    public static boolean generate(WorldGenClay generator, World world, Random random, int x, int y, int z) {
        return NativePopulationRuntime.enabled("clay") && generator.generate(world, random, x, y, z);
    }

    public static boolean generate(WorldGenMinable generator, World world, Random random, int x, int y, int z) {
        return NativePopulationRuntime.enabled("ores") && generator.generate(world, random, x, y, z);
    }

    public static boolean generate(WorldGenerator generator, World world, Random random, int x, int y, int z) {
        return NativePopulationRuntime.enabled("trees") && generator.generate(world, random, x, y, z);
    }

    public static boolean generate(WorldGenFlowers generator, World world, Random random, int x, int y, int z) {
        return NativePopulationRuntime.enabled("vegetation") && generator.generate(world, random, x, y, z);
    }

    public static boolean generate(WorldGenTallGrass generator, World world, Random random, int x, int y, int z) {
        return NativePopulationRuntime.enabled("vegetation") && generator.generate(world, random, x, y, z);
    }

    public static boolean generate(WorldGenDeadBush generator, World world, Random random, int x, int y, int z) {
        return NativePopulationRuntime.enabled("vegetation") && generator.generate(world, random, x, y, z);
    }

    public static boolean generate(WorldGenReed generator, World world, Random random, int x, int y, int z) {
        return NativePopulationRuntime.enabled("vegetation") && generator.generate(world, random, x, y, z);
    }

    public static boolean generate(WorldGenPumpkin generator, World world, Random random, int x, int y, int z) {
        return NativePopulationRuntime.enabled("vegetation") && generator.generate(world, random, x, y, z);
    }

    public static boolean generate(WorldGenCactus generator, World world, Random random, int x, int y, int z) {
        return NativePopulationRuntime.enabled("vegetation") && generator.generate(world, random, x, y, z);
    }

    public static boolean generate(WorldGenLiquids generator, World world, Random random, int x, int y, int z) {
        return NativePopulationRuntime.enabled("springs") && generator.generate(world, random, x, y, z);
    }

    public static boolean snow(World world, int x, int y, int z, int block) {
        return NativePopulationRuntime.enabled("snow") && world.setBlockWithNotify(x, y, z, block);
    }
}
