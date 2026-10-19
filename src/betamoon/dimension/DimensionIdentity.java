package betamoon.dimension;

import net.minecraft.src.World;

/**
 * Shared live identity resolver for world snapshots, generation and gameplay
 * consumers.
 */
public final class DimensionIdentity {
    private DimensionIdentity() {
    }

    public static DimensionKey key(World world) {
        if (world == null || world.worldProvider == null) {
            throw new IllegalArgumentException("A live world is required for dimension identity");
        }
        return world.worldProvider instanceof DimensionWorldProvider
                ? ((DimensionWorldProvider) world.worldProvider).binding.definition.key
                : DimensionKey.nativeKey(world.worldProvider.worldType);
    }

    public static int numericId(World world) {
        return world.worldProvider.worldType;
    }

    public static boolean isCustom(World world) {
        return world != null && world.worldProvider instanceof DimensionWorldProvider;
    }

    public static double coordinateScale(DimensionKey key) {
        if (key.isNative()) {
            return key.equals(DimensionKey.NETHER) ? 8 : 1;
        }
        DimensionDefinition definition = DimensionRegistry.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown dimension: " + key);
        }
        return definition.coordinateScale;
    }
}
