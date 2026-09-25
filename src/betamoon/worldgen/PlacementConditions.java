package betamoon.worldgen;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.src.Block;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Material;

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

    public PlacementConditions(BlockSet ground, boolean requireSky, Boolean requireAir, Boolean requireWater,
            Boolean requireLava, int minLight, int maxLight, Set<String> includeBiomes, Set<String> excludeBiomes) {
        this.ground = ground;
        this.requireSky = requireSky;
        this.requireAir = requireAir;
        this.requireWater = requireWater;
        this.requireLava = requireLava;
        this.minLight = minLight;
        this.maxLight = maxLight;
        this.includeBiomes = immutable(includeBiomes);
        this.excludeBiomes = immutable(excludeBiomes);
    }

    public static PlacementConditions any() {
        return new PlacementConditions(null, false, null, null, null, 0, 15,
                Collections.<String>emptySet(), Collections.<String>emptySet());
    }

    public String rejection(FeatureContext context, BlockPosition origin) {
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
        Material material = block == 0 || Block.blocksList[block] == null ? Material.air
                : Block.blocksList[block].blockMaterial;
        if (requireWater != null && requireWater.booleanValue() != (material == Material.water)) {
            return FeatureResult.BLOCKED;
        }
        if (requireLava != null && requireLava.booleanValue() != (material == Material.lava)) {
            return FeatureResult.BLOCKED;
        }
        if (requireSky && !context.canSeeSky(origin.x, origin.y, origin.z)) {
            return FeatureResult.BLOCKED;
        }
        int light = context.world().getBlockLightValue(origin.x, origin.y, origin.z);
        if (light < minLight || light > maxLight) {
            return FeatureResult.BLOCKED;
        }
        BiomeGenBase biome = context.world().getWorldChunkManager().getBiomeGenAt(origin.x, origin.z);
        if (!includeBiomes.isEmpty() && !BiomeGenRegistry.matchesSelectors(biome, includeBiomes)) {
            return FeatureResult.BLOCKED;
        }
        if (BiomeGenRegistry.matchesSelectors(biome, excludeBiomes)) {
            return FeatureResult.BLOCKED;
        }
        return null;
    }

    private static Set<String> immutable(Set<String> source) {
        return Collections.unmodifiableSet(new HashSet<String>(source));
    }
}
