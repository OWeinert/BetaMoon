package betamoon.worldgen;

import betamoon.worldgen.structure.SitePolicy;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;

/** Bounded world reads and deterministic random state for one feature attempt. */
public final class FeatureContext {
    private final World world;
    private final Random random;
    private final WorldGenKey featureKey;
    private final int maxReads;
    private final FeatureResolver resolver;
    private final SitePolicy sitePolicy;
    private final Map<Long, TerrainColumn> terrainColumns = new LinkedHashMap<Long, TerrainColumn>();
    private FeatureOptions options;
    private final Set<WorldGenKey> activeFeatures = new HashSet<WorldGenKey>();
    private int reads;
    private String failure;
    private final Map<String, Object> diagnostics = new LinkedHashMap<String, Object>();

    public FeatureContext(World world, Random random, WorldGenKey featureKey, int maxReads, FeatureResolver resolver) {
        this(world, random, featureKey, maxReads, resolver, FeatureOptions.DEFAULT, SitePolicy.ANY);
    }

    public FeatureContext(World world, Random random, WorldGenKey featureKey, int maxReads, FeatureResolver resolver,
            FeatureOptions options) {
        this(world, random, featureKey, maxReads, resolver, options, SitePolicy.ANY);
    }

    public FeatureContext(World world, Random random, WorldGenKey featureKey, int maxReads, FeatureResolver resolver,
            FeatureOptions options, SitePolicy sitePolicy) {
        this.world = world;
        this.random = random;
        this.featureKey = featureKey;
        this.maxReads = maxReads;
        this.resolver = resolver;
        this.options = options == null ? FeatureOptions.DEFAULT : options;
        this.sitePolicy = sitePolicy == null ? SitePolicy.ANY : sitePolicy;
    }

    public Random random() {
        return random;
    }

    public WorldGenKey featureKey() {
        return featureKey;
    }

    public FeatureOptions options() {
        return options;
    }

    public SitePolicy sitePolicy() {
        return sitePolicy;
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
        return surfaceHeight(x, z, TerrainSurface.WORLD_SURFACE);
    }

    public int surfaceHeight(int x, int z, TerrainSurface surface) {
        if (surface == TerrainSurface.EXACT) {
            throw new IllegalArgumentException("exact does not sample a terrain column");
        }
        return terrainColumn(x, z).sample(surface);
    }

    int rawSurfaceHeight(int x, int z) {
        if (!readable(x, 64, z)) {
            return -1;
        }
        return world.getHeightValue(x, z);
    }

    public int caveFloor(int x, int z, int minY, int maxY, int minimumClearance) {
        int clearance = 0;
        for (int y = Math.min(maxY, WorldGenLimits.MAX_HEIGHT); y >= Math.max(minY, 1); y--) {
            int block = blockId(x, y, z);
            if (block < 0) {
                return -1;
            }
            if (block == 0) {
                clearance++;
                continue;
            }
            if (clearance >= minimumClearance && TerrainColumn.isStructural(block)) {
                return y + 1;
            }
            clearance = 0;
        }
        return -1;
    }

    public boolean isReplaceable(int blockId) {
        return TerrainColumn.isReplaceable(blockId);
    }

    public boolean isStructural(int blockId) {
        return TerrainColumn.isStructural(blockId);
    }

    public boolean isFluid(int blockId) {
        return TerrainColumn.isFluid(blockId);
    }

    public boolean isWater(int blockId) {
        return TerrainColumn.isWater(blockId);
    }

    public boolean isLava(int blockId) {
        return TerrainColumn.isLava(blockId);
    }

    public boolean canSeeSky(int x, int y, int z) {
        return readable(x, y, z) && world.canBlockSeeTheSky(x, y, z);
    }

    public boolean hasTileEntity(int x, int y, int z) {
        if (!readable(x, y, z)) {
            return false;
        }
        TileEntity tile = world.getBlockTileEntity(x, y, z);
        return tile != null;
    }

    public String failure() {
        return failure;
    }

    public void markFailure(String reason) {
        if (failure == null) {
            failure = reason;
        }
    }

    public void diagnostic(String key, Object value) {
        if (key != null && value != null) {
            diagnostics.put(key, value);
        }
    }

    public Map<String, Object> diagnostics() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, Object>(diagnostics));
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

    public FeatureResult plan(WorldGenKey key, BlockPosition origin, PlacementPlan output,
            FeatureOptions defaultOptions) {
        FeatureOptions previous = options;
        options = new FeatureOptions(previous.rotation == null ? defaultOptions.rotation : previous.rotation,
                previous.mirror == null ? defaultOptions.mirror : previous.mirror);
        try {
            return plan(key, origin, output);
        } finally {
            options = previous;
        }
    }

    World world() {
        return world;
    }

    private TerrainColumn terrainColumn(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        TerrainColumn result = terrainColumns.get(Long.valueOf(key));
        if (result == null) {
            result = new TerrainColumn(this, x, z);
            terrainColumns.put(Long.valueOf(key), result);
        }
        return result;
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
