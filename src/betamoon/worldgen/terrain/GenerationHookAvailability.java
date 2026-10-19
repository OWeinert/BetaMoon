package betamoon.worldgen.terrain;

import betamoon.instrumentation.api.RequiredNativeHooks;
import net.minecraft.src.Chunk;
import net.minecraft.src.ChunkProviderGenerate;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;

/**
 * Profiles reject unavailable provider and population boundaries before world
 * activation.
 */
public final class GenerationHookAvailability {
    private GenerationHookAvailability() {
    }

    public static void requireReady() {
        for (Class<?> target : new Class<?>[]{World.class, WorldProvider.class, Chunk.class,
                ChunkProviderGenerate.class}) {
            try {
                Class.forName(target.getName(), false, target.getClassLoader());
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException("Required generation target is unavailable: " + target.getName(),
                        exception);
            }
        }
        require(false);
    }

    public static void require(boolean allowPending) {
        RequiredNativeHooks.require("Generator profiles", allowPending, "betamoon:world_generator:prepare",
                "betamoon:world_generator:provider", "betamoon:world_generator:spawn",
                "betamoon:world_generator:spawn_recheck", "betamoon:world_generator:chunk_light");
        for (String operation : new String[]{"WorldGenLakes", "WorldGenDungeons", "WorldGenClay", "WorldGenMinable",
                "WorldGenerator", "WorldGenFlowers", "WorldGenTallGrass", "WorldGenDeadBush", "WorldGenReed",
                "WorldGenPumpkin", "WorldGenCactus", "WorldGenLiquids", "snow", "animals"}) {
            RequiredNativeHooks.require("Generator population", allowPending, "betamoon:world_population:" + operation);
        }
    }
}
