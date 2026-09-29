package betamoon.worldgen;

import betamoon.worldgen.structure.SitePolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Immutable random-spread regional structure declaration. */
public final class RegionalStructureDefinition {
    public final WorldGenKey key;
    public final WorldGenKey startFeature;
    public final String resourceOwner;
    public final String owner;
    public final String sourceLocation;
    public final Set<String> dimensions;
    public final int spacing;
    public final int separation;
    public final long salt;
    public final String heightType;
    public final int heightValue;
    public final int maxDepth;
    public final int maxPieces;
    public final int maxDistance;
    public final double terminationChance;
    public final boolean entityMarkers;
    public final boolean lootMarkers;
    public final List<PieceChoice> pieces;
    public final SitePolicy site;
    public final int siteSearchAttempts;
    public final int siteSearchRadius;
    public final int connectorVerticalTolerance;

    public RegionalStructureDefinition(WorldGenKey key, WorldGenKey startFeature, String resourceOwner,
            String owner, String sourceLocation, Set<String> dimensions, int spacing, int separation, long salt,
            String heightType, int heightValue, int maxDepth, int maxPieces, int maxDistance,
            double terminationChance, boolean entityMarkers, boolean lootMarkers, List<PieceChoice> pieces) {
        this(key, startFeature, resourceOwner, owner, sourceLocation, dimensions, spacing, separation, salt,
                heightType, heightValue, maxDepth, maxPieces, maxDistance, terminationChance, entityMarkers,
                lootMarkers, pieces, SitePolicy.ANY, 1, 0, 0);
    }

    public RegionalStructureDefinition(WorldGenKey key, WorldGenKey startFeature, String resourceOwner,
            String owner, String sourceLocation, Set<String> dimensions, int spacing, int separation, long salt,
            String heightType, int heightValue, int maxDepth, int maxPieces, int maxDistance,
            double terminationChance, boolean entityMarkers, boolean lootMarkers, List<PieceChoice> pieces,
            SitePolicy site, int siteSearchAttempts, int siteSearchRadius, int connectorVerticalTolerance) {
        this.key = key;
        this.startFeature = startFeature;
        this.resourceOwner = resourceOwner;
        this.owner = owner;
        this.sourceLocation = sourceLocation;
        this.dimensions = Collections.unmodifiableSet(new java.util.LinkedHashSet<String>(dimensions));
        this.spacing = spacing;
        this.separation = separation;
        this.salt = salt;
        this.heightType = heightType;
        this.heightValue = heightValue;
        this.maxDepth = maxDepth;
        this.maxPieces = maxPieces;
        this.maxDistance = maxDistance;
        this.terminationChance = terminationChance;
        this.entityMarkers = entityMarkers;
        this.lootMarkers = lootMarkers;
        this.pieces = Collections.unmodifiableList(new ArrayList<PieceChoice>(pieces));
        this.site = site == null ? SitePolicy.ANY : site;
        this.siteSearchAttempts = siteSearchAttempts;
        this.siteSearchRadius = siteSearchRadius;
        this.connectorVerticalTolerance = connectorVerticalTolerance;
    }

    public String placementSignature() {
        return heightType + '|' + heightValue + '|' + site.signature() + '|' + siteSearchAttempts + '|'
                + siteSearchRadius + '|' + connectorVerticalTolerance;
    }

    public static final class PieceChoice {
        public final String pool;
        public final WorldGenKey feature;
        public final int weight;

        public PieceChoice(String pool, WorldGenKey feature, int weight) {
            this.pool = pool;
            this.feature = feature;
            this.weight = weight;
        }
    }
}
