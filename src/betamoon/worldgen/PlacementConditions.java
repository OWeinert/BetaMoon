package betamoon.worldgen;

import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import betamoon.worldgen.spatial.SpatialSelector;
import betamoon.worldgen.structure.SitePolicy;
import betamoon.worldgen.terrain.WorldGenerationRuntime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.src.Block;

/** Immutable predicates evaluated once per sampled placement origin. */
public final class PlacementConditions {
    public final BlockSet ground;
    public final boolean requireSky;
    public final Boolean requireAir;
    public final Boolean requireWater;
    public final Boolean requireLava;
    public final int minLight;
    public final int maxLight;
    public final Set<String> includeBiomes;
    public final Set<String> excludeBiomes;
    public final SitePolicy site;
    public final SpatialSelector spatial;
    public final String fluid;

    public PlacementConditions(BlockSet ground, boolean requireSky, Boolean requireAir, Boolean requireWater,
            Boolean requireLava, int minLight, int maxLight, Set<String> includeBiomes, Set<String> excludeBiomes) {
        this(ground, requireSky, requireAir, requireWater, requireLava, minLight, maxLight, includeBiomes,
                excludeBiomes, SitePolicy.ANY);
    }

    public PlacementConditions(BlockSet ground, boolean requireSky, Boolean requireAir, Boolean requireWater,
            Boolean requireLava, int minLight, int maxLight, Set<String> includeBiomes, Set<String> excludeBiomes,
            SitePolicy site) {
        this(ground, requireSky, requireAir, requireWater, requireLava, minLight, maxLight, includeBiomes,
                excludeBiomes, site, null);
    }

    public PlacementConditions(BlockSet ground, boolean requireSky, Boolean requireAir, Boolean requireWater,
            Boolean requireLava, int minLight, int maxLight, Set<String> includeBiomes, Set<String> excludeBiomes,
            SitePolicy site, SpatialSelector spatial) {
        this(ground, requireSky, requireAir, requireWater, requireLava, minLight, maxLight, includeBiomes,
                excludeBiomes, site, spatial, null);
    }

    public PlacementConditions(BlockSet ground, boolean requireSky, Boolean requireAir, Boolean requireWater,
            Boolean requireLava, int minLight, int maxLight, Set<String> includeBiomes, Set<String> excludeBiomes,
            SitePolicy site, SpatialSelector spatial, String fluid) {
        this.fluid = fluid;
        this.ground = ground;
        this.requireSky = requireSky;
        this.requireAir = requireAir;
        this.requireWater = requireWater;
        this.requireLava = requireLava;
        this.minLight = minLight;
        this.maxLight = maxLight;
        this.includeBiomes = immutable(includeBiomes);
        this.excludeBiomes = immutable(excludeBiomes);
        this.site = site == null ? SitePolicy.ANY : site;
        this.spatial = spatial;
    }

    public static PlacementConditions any() {
        return new PlacementConditions(null, false, null, null, null, 0, 15, Collections.<String>emptySet(),
                Collections.<String>emptySet());
    }

    public String rejection(FeatureContext context, BlockPosition origin) {
        if (spatial != null
                && spatial.weight(WorldGenerationRuntime.fieldContext(context.world()), origin.x, origin.z) <= 0) {
            return "spatial";
        }
        if (ground != null) {
            int block = context.blockId(origin.x, origin.y - 1, origin.z);
            if (block < 0) {
                return context.failure();
            }
            if (!ground.contains(block)) {
                return FeatureResult.INVALID_GROUND;
            }
        }
        int block = context.blockId(origin.x, origin.y, origin.z);
        if (block < 0) {
            return context.failure();
        }
        if (requireAir != null && requireAir.booleanValue() != (block == 0)) {
            return FeatureResult.BLOCKED;
        }
        boolean water = block == Block.waterMoving.blockID || block == Block.waterStill.blockID;
        boolean lava = block == Block.lavaMoving.blockID || block == Block.lavaStill.blockID;
        if (requireWater != null && requireWater.booleanValue() != water) {
            return FeatureResult.BLOCKED;
        }
        if (requireLava != null && requireLava.booleanValue() != lava) {
            return FeatureResult.BLOCKED;
        }
        if (fluid != null) {
            FluidRegistration registration = FluidRegistry.instance().getByBlockId(block);
            if (registration == null || !fluid.equals(registration.key().toString())) {
                return FeatureResult.BLOCKED;
            }
        }
        if (requireSky && !context.canSeeSky(origin.x, origin.y, origin.z)) {
            return FeatureResult.BLOCKED;
        }
        int light = context.world().getBlockLightValue(origin.x, origin.y, origin.z);
        if (light < minLight || light > maxLight) {
            return FeatureResult.BLOCKED;
        }
        if (!includeBiomes.isEmpty()
                && !WorldGenerationRuntime.biomeMatches(context.world(), origin.x, origin.z, includeBiomes)) {
            return FeatureResult.BLOCKED;
        }
        if (!excludeBiomes.isEmpty()
                && WorldGenerationRuntime.biomeMatches(context.world(), origin.x, origin.z, excludeBiomes)) {
            return FeatureResult.BLOCKED;
        }
        return null;
    }

    private static Set<String> immutable(Set<String> source) {
        return Collections.unmodifiableSet(new HashSet<String>(source));
    }
}
