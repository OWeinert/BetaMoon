package betamoon.worldgen.structure;

import betamoon.worldgen.BlockPosition;
import betamoon.worldgen.FeatureContext;
import betamoon.worldgen.FeatureResult;
import betamoon.worldgen.PlacementPlan;
import betamoon.worldgen.TerrainSurface;
import betamoon.worldgen.WorldGenLimits;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Compiles and validates all excavation volumes before adding their
 * lowest-priority writes to the plan.
 */
final class ExcavationPlanner {
    private ExcavationPlanner() {
    }

    static String plan(FeatureContext context, BlockPosition origin, StructureTransform transform,
            TerrainPolicy terrain, StructureTerrainMask mask, StructureFeature.Bounds templateBounds,
            PlacementPlan output) {
        ExcavationPolicy policy = terrain.excavation;
        if (policy == null) {
            return null;
        }
        Set<BlockPosition> cells = new LinkedHashSet<BlockPosition>();
        try {
            for (ExcavationPolicy.Volume volume : policy.volumes) {
                addVolume(cells, context, origin, transform, terrain, mask, templateBounds, volume);
                if (cells.size() > policy.maxBlocks) {
                    return FeatureResult.EXCAVATION_TOO_LARGE;
                }
            }
        } catch (IllegalArgumentException error) {
            context.diagnostic("excavationError", error.getMessage());
            return context.failure() == null
                    ? FeatureResult.EXCAVATION_INVALID_VOLUME
                    : FeatureResult.EXCAVATION_UNREADABLE;
        }
        int removed = 0;
        int preserved = 0;
        for (BlockPosition cell : cells) {
            int existing = context.blockId(cell.x, cell.y, cell.z);
            if (existing < 0) {
                return FeatureResult.EXCAVATION_UNREADABLE;
            }
            if (existing == 0) {
                preserved++;
                continue;
            }
            if (context.isFluid(existing) && policy.fluids == TerrainReplacementPolicy.Fluid.PRESERVE) {
                preserved++;
                continue;
            }
            String failure = TerrainMutationPlanner.write(context, output, cell.x, cell.y, cell.z, policy.resultBlock,
                    policy.resultMetadata, policy.replace, policy.fluids, FeatureResult.EXCAVATION_PROTECTED_BLOCK,
                    FeatureResult.EXCAVATION_TILE_COLLISION, FeatureResult.EXCAVATION_FLUID_COLLISION,
                    PlacementPlan.WritePriority.EXCAVATION);
            if (failure != null) {
                context.diagnostic("terrainRejectedPosition", position(cell));
                return failure;
            }
            removed++;
        }
        context.diagnostic("excavationVolumeCells", Integer.valueOf(cells.size()));
        context.diagnostic("excavationRemoved", Integer.valueOf(removed));
        context.diagnostic("excavationPreserved", Integer.valueOf(preserved));
        return null;
    }

    private static void addVolume(Set<BlockPosition> cells, FeatureContext context, BlockPosition origin,
            StructureTransform transform, TerrainPolicy terrain, StructureTerrainMask mask,
            StructureFeature.Bounds templateBounds, ExcavationPolicy.Volume volume) {
        if (volume.shape == ExcavationPolicy.Shape.FOOTPRINT || volume.shape == ExcavationPolicy.Shape.AUTHORED) {
            if (volume.shape == ExcavationPolicy.Shape.AUTHORED && !volume.cells.isEmpty()) {
                for (BlockPosition cell : volume.cells) {
                    BlockPosition local = transform.apply(cell.x, cell.y, cell.z);
                    add(cells, origin.offset(local.x, local.y, local.z));
                }
                return;
            }
            if (volume.shape == ExcavationPolicy.Shape.AUTHORED && volume.name != null) {
                java.util.List<BlockPosition> authored = terrain.excavation.masks.get(volume.name);
                if (authored == null) {
                    throw new IllegalArgumentException("unknown authored excavation mask: " + volume.name);
                }
                for (BlockPosition cell : authored) {
                    BlockPosition local = transform.apply(cell.x, cell.y, cell.z);
                    add(cells, origin.offset(local.x, local.y, local.z));
                }
                return;
            }
            if (volume.shape == ExcavationPolicy.Shape.AUTHORED && volume.name == null && volume.cells.isEmpty()) {
                if (mask.authoredClearance(transform).isEmpty()) {
                    throw new IllegalArgumentException("authored excavation has no cells or clearance markers");
                }
                for (BlockPosition cell : mask.authoredClearance(transform)) {
                    add(cells, origin.offset(cell.x, cell.y, cell.z));
                }
                return;
            }
            TerrainFootprint footprint = TerrainFootprintCompiler.compile(volume.footprint, mask, transform,
                    templateBounds, terrain.masks);
            if (footprint.isEmpty()) {
                throw new IllegalArgumentException("excavation footprint is empty");
            }
            for (TerrainFootprint.Cell column : footprint.cells()) {
                int from = bound(context, origin, column, volume.from, terrain.surface);
                int to = bound(context, origin, column, volume.to, terrain.surface);
                if (from > to || to - from + 1 > 64) {
                    throw new IllegalArgumentException("excavation vertical range is empty or exceeds 64 blocks");
                }
                for (int y = from; y <= to; y++) {
                    add(cells, new BlockPosition(origin.x + column.x, y, origin.z + column.z));
                }
            }
            return;
        }
        if (volume.shape == ExcavationPolicy.Shape.BOX) {
            for (int z = volume.minimum.z; z <= volume.maximum.z; z++) {
                for (int y = volume.minimum.y; y <= volume.maximum.y; y++) {
                    for (int x = volume.minimum.x; x <= volume.maximum.x; x++) {
                        BlockPosition local = transform.apply(x, y, z);
                        add(cells, origin.offset(local.x, local.y, local.z));
                    }
                }
            }
            return;
        }
        int minimumY = volume.shape == ExcavationPolicy.Shape.CYLINDER ? 0 : -volume.radiusY;
        int maximumY = volume.shape == ExcavationPolicy.Shape.CYLINDER ? volume.radiusY - 1 : volume.radiusY;
        for (int z = -volume.radiusZ; z <= volume.radiusZ; z++) {
            for (int y = minimumY; y <= maximumY; y++) {
                for (int x = -volume.radiusX; x <= volume.radiusX; x++) {
                    boolean inside = volume.shape == ExcavationPolicy.Shape.CYLINDER
                            ? VoxelShapeRasterizer.ellipse(x, z, volume.radiusX, volume.radiusZ)
                            : VoxelShapeRasterizer.ellipsoid(x, y, z, volume.radiusX, volume.radiusY, volume.radiusZ);
                    if (!inside) {
                        continue;
                    }
                    BlockPosition local = transform.apply(volume.center.x + x, volume.center.y + y,
                            volume.center.z + z);
                    add(cells, origin.offset(local.x, local.y, local.z));
                }
            }
        }
    }

    private static int bound(FeatureContext context, BlockPosition origin, TerrainFootprint.Cell column,
            ExcavationPolicy.Bound bound, TerrainSurface surface) {
        if (!bound.surface) {
            return origin.y + bound.value;
        }
        TerrainSurface sampled = surface == TerrainSurface.EXACT ? TerrainSurface.SOLID_SURFACE : surface;
        int value = context.surfaceHeight(origin.x + column.x, origin.z + column.z, sampled);
        if (value < 0) {
            throw new IllegalArgumentException("excavation surface is unreadable");
        }
        return value - 1 + bound.value;
    }

    private static void add(Set<BlockPosition> cells, BlockPosition position) {
        if (position.y < WorldGenLimits.MIN_HEIGHT || position.y > WorldGenLimits.MAX_HEIGHT) {
            throw new IllegalArgumentException("excavation leaves world height bounds");
        }
        cells.add(position);
    }

    private static String position(BlockPosition value) {
        return value.x + "," + value.y + "," + value.z;
    }
}
