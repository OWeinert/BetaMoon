package betamoon.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Immutable compiled reusable feature and its publication metadata. */
public final class FeatureDefinition {
    public final WorldGenKey key;
    public final String type;
    public final String resourceOwner;
    public final String owner;
    public final String sourceLocation;
    public final WorldFeature feature;
    public final List<WorldGenKey> dependencies;
    public final int maxBlocks;
    public final int maxRadius;
    public final Set<String> tags;

    public FeatureDefinition(WorldGenKey key, String type, String resourceOwner, String owner, String sourceLocation,
            WorldFeature feature, List<WorldGenKey> dependencies, int maxBlocks, int maxRadius) {
        this(key, type, resourceOwner, owner, sourceLocation, feature, dependencies, maxBlocks, maxRadius,
                Collections.<String>emptySet());
    }

    public FeatureDefinition(WorldGenKey key, String type, String resourceOwner, String owner, String sourceLocation,
            WorldFeature feature, List<WorldGenKey> dependencies, int maxBlocks, int maxRadius, Set<String> tags) {
        this.key = key;
        this.type = type;
        this.resourceOwner = resourceOwner;
        this.owner = owner;
        this.sourceLocation = sourceLocation;
        this.feature = feature;
        this.dependencies = Collections.unmodifiableList(new ArrayList<WorldGenKey>(dependencies));
        this.maxBlocks = maxBlocks;
        this.maxRadius = maxRadius;
        this.tags = Collections.unmodifiableSet(new TreeSet<String>(tags));
    }

    public FeatureDefinition withTags(Set<String> tags) {
        return new FeatureDefinition(key, type, resourceOwner, owner, sourceLocation, feature, dependencies, maxBlocks,
                maxRadius, tags);
    }
}
