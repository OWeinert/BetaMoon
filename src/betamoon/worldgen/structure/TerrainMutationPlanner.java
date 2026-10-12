package betamoon.worldgen.structure;

import betamoon.worldgen.FeatureContext;
import betamoon.worldgen.FeatureResult;
import betamoon.worldgen.PlacementPlan;
import betamoon.worldgen.TerrainSurface;
import betamoon.worldgen.WorldGenLimits;
import java.util.List;
import net.minecraft.src.Block;

/**
 * Shared protection, replacement, fluid, and deterministic material rules for
 * terrain writes.
 */
final class TerrainMutationPlanner {
    private TerrainMutationPlanner() {
    }

    static String write(FeatureContext context, PlacementPlan output, int x, int y, int z, int blockId, int metadata,
            TerrainReplacementPolicy.Replace replace, TerrainReplacementPolicy.Fluid fluids, String protectedFailure,
            String tileFailure, String fluidFailure, PlacementPlan.WritePriority priority) {
        return write(context, output, x, y, z, blockId, metadata, TerrainReplacementPolicy.Selector.preset(replace),
                fluids, protectedFailure, tileFailure, fluidFailure, priority);
    }

    static String write(FeatureContext context, PlacementPlan output, int x, int y, int z, int blockId, int metadata,
            TerrainReplacementPolicy.Selector replace, TerrainReplacementPolicy.Fluid fluids, String protectedFailure,
            String tileFailure, String fluidFailure, PlacementPlan.WritePriority priority) {
        if (y < WorldGenLimits.MIN_HEIGHT || y > WorldGenLimits.MAX_HEIGHT) {
            return FeatureResult.OUT_OF_BOUNDS;
        }
        int existing = context.blockId(x, y, z);
        if (existing < 0) {
            return context.failure() == null ? FeatureResult.TERRAIN_UNREADABLE : context.failure();
        }
        if (context.hasTileEntity(x, y, z)) {
            return tileFailure;
        }
        if (existing == Block.bedrock.blockID) {
            return protectedFailure;
        }
        if (context.isFluid(existing)) {
            if (fluids == TerrainReplacementPolicy.Fluid.PRESERVE) {
                return null;
            }
            if (fluids == TerrainReplacementPolicy.Fluid.REJECT) {
                return fluidFailure;
            }
        } else if (existing != 0 && !mayReplace(context, existing, replace)) {
            return protectedFailure;
        }
        if (!output.setBlock(x, y, z, blockId, metadata, priority)) {
            return FeatureResult.BUDGET_EXCEEDED.equals(output.failure())
                    ? FeatureResult.TERRAIN_MUTATION_BUDGET
                    : output.failure();
        }
        return null;
    }

    static boolean mayReplace(FeatureContext context, int blockId, TerrainReplacementPolicy.Replace replace) {
        return mayReplace(context, blockId, TerrainReplacementPolicy.Selector.preset(replace));
    }

    static boolean mayReplace(FeatureContext context, int blockId, TerrainReplacementPolicy.Selector replace) {
        return blockId == 0 || replace.permits(context, blockId);
    }

    static Material material(FeatureContext context, TerrainMaterialPolicy policy, TerrainSurface surface, long seed,
            int x, int y, int z, int depth) {
        if (policy.kind == TerrainMaterialPolicy.Kind.FIXED) {
            return new Material(policy.blockId, policy.metadata);
        }
        if (policy.kind == TerrainMaterialPolicy.Kind.PALETTE) {
            int total = 0;
            for (TerrainMaterialPolicy.State state : policy.layers) {
                total += state.weight;
            }
            long mixed = mix(seed, x, y, z, depth);
            int selected = (int) Math.floorMod(mixed, total);
            for (TerrainMaterialPolicy.State state : policy.layers) {
                selected -= state.weight;
                if (selected < 0) {
                    return new Material(state.blockId, state.metadata);
                }
            }
        }
        TerrainSurface sampledSurface = surface == TerrainSurface.EXACT ? TerrainSurface.SOLID_SURFACE : surface;
        int sampled = context.surfaceHeight(x, z, sampledSurface);
        if (sampled >= 0) {
            int sampleY = policy.kind == TerrainMaterialPolicy.Kind.SAMPLE_SUBSURFACE ? sampled - 2 : sampled - 1;
            for (int candidate = Math.min(WorldGenLimits.MAX_HEIGHT, sampleY); candidate >= Math
                    .max(WorldGenLimits.MIN_HEIGHT, sampleY - 8); candidate--) {
                int block = context.blockId(x, candidate, z);
                int metadata = context.metadata(x, candidate, z);
                if (block < 0 || metadata < 0) {
                    break;
                }
                if (context.isTerrainMaterial(block) && !context.isFluid(block)) {
                    return new Material(block, metadata);
                }
            }
        }
        return new Material(Block.dirt.blockID, 0);
    }

    static long mix(long seed, int x, int y, int z, int salt) {
        long value = seed ^ 0x9e3779b97f4a7c15L;
        value ^= (long) x * 0x632be59bd9b4e019L;
        value ^= (long) y * 0x85157af5L;
        value ^= (long) z * 0x94d049bb133111ebL;
        value ^= (long) salt * 0x369dea0f31a53f85L;
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    static int medianSupportY(List<StructureTerrainMask.Support> support) {
        java.util.List<Integer> values = new java.util.ArrayList<Integer>();
        for (StructureTerrainMask.Support cell : support) {
            values.add(Integer.valueOf(cell.y));
        }
        java.util.Collections.sort(values);
        return values.get((values.size() - 1) / 2).intValue();
    }

    static final class Material {
        final int blockId;
        final int metadata;

        Material(int blockId, int metadata) {
            this.blockId = blockId;
            this.metadata = metadata;
        }
    }
}
