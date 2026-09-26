package betamoon.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable compiled sampling, filtering, ordering, and budget contract. */
public final class PlacementDefinition {
    public final WorldGenKey key;
    public final WorldGenKey featureKey;
    public final String resourceOwner;
    public final String owner;
    public final String sourceLocation;
    public final GenerationStage stage;
    public final Set<String> dimensions;
    public final IntRange attempts;
    public final double extraChance;
    public final double probability;
    public final HeightProvider height;
    public final PlacementConditions conditions;
    public final List<WorldGenKey> before;
    public final List<WorldGenKey> after;
    public final int priority;
    public final long salt;
    public final int successLimit;
    public final String horizontal;
    public final int gridSpacing;

    public PlacementDefinition(WorldGenKey key, WorldGenKey featureKey, String resourceOwner, String owner,
            String sourceLocation, GenerationStage stage, Set<String> dimensions, IntRange attempts,
            double extraChance, double probability, HeightProvider height, PlacementConditions conditions,
            List<WorldGenKey> before, List<WorldGenKey> after, int priority, long salt, int successLimit,
            String horizontal, int gridSpacing) {
        this.key = key;
        this.featureKey = featureKey;
        this.resourceOwner = resourceOwner;
        this.owner = owner;
        this.sourceLocation = sourceLocation;
        this.stage = stage;
        this.dimensions = Collections.unmodifiableSet(new HashSet<String>(dimensions));
        this.attempts = attempts;
        this.extraChance = extraChance;
        this.probability = probability;
        this.height = height;
        this.conditions = conditions;
        this.before = Collections.unmodifiableList(new ArrayList<WorldGenKey>(before));
        this.after = Collections.unmodifiableList(new ArrayList<WorldGenKey>(after));
        this.priority = priority;
        this.salt = salt;
        this.successLimit = successLimit;
        this.horizontal = horizontal;
        this.gridSpacing = gridSpacing;
    }

    public int sampleAttempts(java.util.Random random) {
        int count = attempts.sample(random);
        if (extraChance > 0.0D && random.nextDouble() < extraChance) {
            count++;
        }
        return count;
    }

    public BlockPosition sampleOrigin(FeatureContext context, int chunkX, int chunkZ, int attempt) {
        int x;
        int z;
        if ("grid".equals(horizontal)) {
            int cells = Math.max(1, 16 / gridSpacing);
            x = chunkX + (attempt % cells) * gridSpacing + gridSpacing / 2;
            z = chunkZ + (attempt / cells % cells) * gridSpacing + gridSpacing / 2;
        } else {
            x = chunkX + context.random().nextInt(16);
            z = chunkZ + context.random().nextInt(16);
        }
        int y = height.sample(context, x, z);
        return y < 0 ? null : new BlockPosition(x, y, z);
    }
}
