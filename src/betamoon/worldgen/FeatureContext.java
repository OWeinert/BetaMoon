package betamoon.worldgen;

import java.util.Random;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.src.World;

/** Bounded world reads and deterministic random state for one feature attempt. */
public final class FeatureContext {
    private final World world;
    private final Random random;
    private final WorldGenKey featureKey;
    private final int maxReads;
    private final FeatureResolver resolver;
    private final Set<WorldGenKey> activeFeatures = new HashSet<WorldGenKey>();
    private int reads;
    private String failure;

    public FeatureContext(World world, Random random, WorldGenKey featureKey, int maxReads, FeatureResolver resolver) {
        this.world = world;
        this.random = random;
        this.featureKey = featureKey;
        this.maxReads = maxReads;
        this.resolver = resolver;
    }

    public Random random() {
        return random;
    }

    public WorldGenKey featureKey() {
        return featureKey;
    }

    public int blockId(int x, int y, int z) {
        if (!readable(x, y, z)) {
            return -1;
        }
        return world.getBlockId(x, y, z);
    }

    public int metadata(int x, int y, int z) {
        if (!readable(x, y, z)) {
            return -1;
        }
        return world.getBlockMetadata(x, y, z);
    }

    public int surfaceHeight(int x, int z) {
        if (!readable(x, 64, z)) {
            return -1;
        }
        return world.getHeightValue(x, z);
    }

    public boolean canSeeSky(int x, int y, int z) {
        return readable(x, y, z) && world.canBlockSeeTheSky(x, y, z);
    }

    public String failure() {
        return failure;
    }

    public FeatureResult plan(WorldGenKey key, BlockPosition origin, PlacementPlan output) {
        if (resolver == null || !activeFeatures.add(key)) {
            return FeatureResult.rejected(FeatureResult.RUNTIME_ERROR);
        }
        try {
            FeatureDefinition definition = resolver.find(key);
            return definition == null ? FeatureResult.rejected(FeatureResult.RUNTIME_ERROR)
                    : definition.feature.plan(this, origin, output);
        } finally {
            activeFeatures.remove(key);
        }
    }

    World world() {
        return world;
    }

    private boolean readable(int x, int y, int z) {
        if (y < WorldGenLimits.MIN_HEIGHT || y > WorldGenLimits.MAX_HEIGHT) {
            failure = FeatureResult.OUT_OF_BOUNDS;
            return false;
        }
        if (++reads > maxReads) {
            failure = FeatureResult.BUDGET_EXCEEDED;
            return false;
        }
        if (!world.blockExists(x, y, z)) {
            failure = FeatureResult.UNLOADED_CHUNK;
            return false;
        }
        return true;
    }

    public interface FeatureResolver {
        FeatureDefinition find(WorldGenKey key);
    }
}
