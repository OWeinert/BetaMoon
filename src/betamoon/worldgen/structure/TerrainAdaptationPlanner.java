package betamoon.worldgen.structure;

import betamoon.worldgen.BlockPosition;
import betamoon.worldgen.FeatureContext;
import betamoon.worldgen.FeatureResult;
import betamoon.worldgen.PlacementPlan;
import betamoon.worldgen.TerrainSurface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared footprint, material, protection, and budget planner for foundations
 * and terraces.
 */
final class TerrainAdaptationPlanner {
    private static final int[][] NEIGHBORS = new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private TerrainAdaptationPlanner() {
    }

    static String planFoundation(FeatureContext context, BlockPosition origin, StructureTransform transform,
            List<StructureTerrainMask.Support> support, TerrainPolicy terrain, StructureTerrainMask mask,
            StructureFeature.Bounds templateBounds, PlacementPlan output, long placementSeed) {
        FoundationPolicy policy = terrain.foundation;
        if (policy == null) {
            return null;
        }
        TerrainAdaptationPolicy adaptation = terrain.adaptation;
        TerrainFootprint core;
        try {
            core = TerrainFootprintCompiler.compile(adaptation.footprint, mask, transform, templateBounds,
                    terrain.masks);
        } catch (IllegalArgumentException error) {
            context.diagnostic("foundationError", error.getMessage());
            return FeatureResult.FOUNDATION_INVALID_FOOTPRINT;
        }
        if (core.isEmpty()
                || unsupportedSpan(core, support) > policy.maxUnsupportedSpan && policy.maxUnsupportedSpan > 0) {
            return FeatureResult.FOUNDATION_INVALID_FOOTPRINT;
        }
        Map<TerrainFootprint.Cell, Integer> supportHeights = supportHeights(support);
        int defaultSupportY = TerrainMutationPlanner.medianSupportY(support);
        int supported = 0;
        int coreWrites = 0;
        int maximumDepth = 0;
        for (TerrainFootprint.Cell column : core.cells()) {
            Integer localY = supportHeights.get(column);
            int target = origin.y + (localY == null ? defaultSupportY : localY.intValue());
            int surface = sampleSurface(context, terrain.surface, origin.x + column.x, origin.z + column.z);
            if (surface < 0) {
                return readFailure(context);
            }
            int fittedTarget = target - terrain.verticalOffset;
            if (fittedTarget < surface) {
                return FeatureResult.TERRAIN_SUPPORT;
            }
            int start = Math.min(target, surface + Math.min(terrain.verticalOffset, 0));
            int depth = target - start;
            maximumDepth = Math.max(maximumDepth, depth);
            if (depth > policy.maxDepth) {
                context.diagnostic("terrainRejectedPosition",
                        position(origin.x + column.x, start, origin.z + column.z));
                return FeatureResult.FOUNDATION_TOO_DEEP;
            }
            if (depth == 0 || surface >= target) {
                supported++;
            }
            for (int y = start; y < target; y++) {
                int existing = context.blockId(origin.x + column.x, y, origin.z + column.z);
                if (existing < 0) {
                    return readFailure(context);
                }
                if (context.isFluid(existing) && policy.fluids == TerrainReplacementPolicy.Fluid.PRESERVE) {
                    return FeatureResult.FOUNDATION_FLUID_COLLISION;
                }
                TerrainMaterialPolicy materialPolicy = y == target - 1 ? policy.capMaterial : policy.fillMaterial;
                TerrainMutationPlanner.Material material = TerrainMutationPlanner.material(context, materialPolicy,
                        terrain.surface, placementSeed, origin.x + column.x, y, origin.z + column.z, target - y);
                String failure = TerrainMutationPlanner.write(context, output, origin.x + column.x, y,
                        origin.z + column.z, material.blockId, material.metadata, policy.replace, policy.fluids,
                        FeatureResult.FOUNDATION_PROTECTED_BLOCK, FeatureResult.FOUNDATION_TILE_COLLISION,
                        FeatureResult.FOUNDATION_FLUID_COLLISION, PlacementPlan.WritePriority.TERRAIN_ADAPTATION);
                if (failure != null) {
                    context.diagnostic("terrainRejectedPosition",
                            position(origin.x + column.x, y, origin.z + column.z));
                    return failure;
                }
                coreWrites++;
            }
        }
        double supportRatio = core.isEmpty() ? 0.0D : (double) supported / core.size();
        if (supportRatio < policy.requireSupportRatio) {
            return FeatureResult.TERRAIN_SUPPORT;
        }

        int edgeWrites = planFoundationEdge(context, origin, terrain, policy, core, mask, transform, templateBounds,
                output, placementSeed, defaultSupportY);
        if (edgeWrites < 0) {
            return context.failure() == null ? FeatureResult.FOUNDATION_PROTECTED_BLOCK : context.failure();
        }
        if (coreWrites + edgeWrites > adaptation.maximumWrites) {
            return FeatureResult.TERRAIN_MUTATION_BUDGET;
        }
        context.diagnostic("foundationCoreCells", Integer.valueOf(core.size()));
        context.diagnostic("foundationCoreWrites", Integer.valueOf(coreWrites));
        context.diagnostic("foundationEdgeWrites", Integer.valueOf(edgeWrites));
        context.diagnostic("foundationMaximumDepth", Integer.valueOf(maximumDepth));
        context.diagnostic("foundationBounds", bounds(core, origin));
        return null;
    }

    private static int planFoundationEdge(FeatureContext context, BlockPosition origin, TerrainPolicy terrain,
            FoundationPolicy policy, TerrainFootprint core, StructureTerrainMask mask, StructureTransform transform,
            StructureFeature.Bounds templateBounds, PlacementPlan output, long seed, int supportY) {
        if (policy.edgeType == FoundationPolicy.EdgeType.HARD
                || policy.maxExpansion == 0 && policy.edgeType != FoundationPolicy.EdgeType.AUTHORED) {
            return 0;
        }
        TerrainFootprint edge;
        try {
            if (policy.edgeType == FoundationPolicy.EdgeType.AUTHORED) {
                TerrainFootprintPolicy declaration = terrain.masks.get(policy.edgeMask);
                if (declaration == null) {
                    return -1;
                }
                edge = TerrainFootprintCompiler.compile(declaration, mask, transform, templateBounds, terrain.masks)
                        .difference(core);
            } else {
                edge = core.dilate(policy.maxExpansion).difference(core);
            }
        } catch (IllegalArgumentException error) {
            return -1;
        }
        int writes = 0;
        int target = origin.y + supportY;
        for (TerrainFootprint.Cell column : edge.cells()) {
            int distance = core.distanceFrom(core, column, policy.maxExpansion);
            if (policy.edgeType == FoundationPolicy.EdgeType.NATURAL
                    && (TerrainMutationPlanner.mix(seed, column.x, 0, column.z, distance) & 3L) == 0L) {
                continue;
            }
            int columnTarget = target;
            if (policy.edgeType == FoundationPolicy.EdgeType.BLENDED
                    || policy.edgeType == FoundationPolicy.EdgeType.NATURAL) {
                columnTarget -= Math.max(0, distance - 1) * policy.stepEvery;
            }
            int surface = sampleSurface(context, terrain.surface, origin.x + column.x, origin.z + column.z);
            if (surface < 0) {
                return -1;
            }
            int start = surface;
            if (columnTarget - start > policy.maxDepth) {
                return -1;
            }
            for (int y = start; y < columnTarget; y++) {
                int depth = columnTarget - y;
                if (policy.edgeType == FoundationPolicy.EdgeType.STEPPED
                        && distance > Math.min(policy.maxExpansion, (depth - 1) / policy.stepEvery + 1)) {
                    continue;
                }
                int existing = context.blockId(origin.x + column.x, y, origin.z + column.z);
                if (existing < 0) {
                    return -1;
                }
                if ((context.isFluid(existing) && policy.fluids == TerrainReplacementPolicy.Fluid.PRESERVE)
                        || (context.isStructural(existing) && !context.isReplaceable(existing))) {
                    continue;
                }
                TerrainMutationPlanner.Material material = TerrainMutationPlanner.material(context, policy.edgeMaterial,
                        terrain.surface, seed, origin.x + column.x, y, origin.z + column.z, depth);
                String failure = TerrainMutationPlanner.write(context, output, origin.x + column.x, y,
                        origin.z + column.z, material.blockId, material.metadata, policy.replace, policy.fluids,
                        FeatureResult.FOUNDATION_PROTECTED_BLOCK, FeatureResult.FOUNDATION_TILE_COLLISION,
                        FeatureResult.FOUNDATION_FLUID_COLLISION, PlacementPlan.WritePriority.TERRAIN_ADAPTATION);
                if (failure != null) {
                    context.markFailure(failure);
                    return -1;
                }
                writes++;
            }
        }
        return writes;
    }

    static String planTerrace(FeatureContext context, BlockPosition origin, StructureTransform transform,
            List<StructureTerrainMask.Support> support, TerrainPolicy terrain, StructureTerrainMask mask,
            StructureFeature.Bounds templateBounds, PlacementPlan output, long placementSeed) {
        TerracePolicy policy = terrain.terrace;
        if (policy == null) {
            return null;
        }
        TerrainAdaptationPolicy adaptation = terrain.adaptation;
        TerrainFootprint core;
        TerrainFootprint all;
        try {
            core = TerrainFootprintCompiler.compile(adaptation.footprint, mask, transform, templateBounds,
                    terrain.masks);
            if (policy.transitionType == TerracePolicy.TransitionType.HARD) {
                all = core;
            } else if (policy.transitionType == TerracePolicy.TransitionType.AUTHORED) {
                TerrainFootprintPolicy transition = terrain.masks.get(policy.transitionMask);
                if (transition == null) {
                    return FeatureResult.TERRACE_INVALID_FOOTPRINT;
                }
                all = core.union(
                        TerrainFootprintCompiler.compile(transition, mask, transform, templateBounds, terrain.masks));
            } else {
                all = core.dilate(policy.radius);
            }
            if (policy.compatibility) {
                List<TerrainFootprint.Cell> legacyBlend = new ArrayList<TerrainFootprint.Cell>();
                for (BlockPosition blend : mask.blend(transform)) {
                    legacyBlend.add(new TerrainFootprint.Cell(blend.x, blend.z));
                }
                all = all.union(new TerrainFootprint(legacyBlend));
            }
        } catch (IllegalArgumentException error) {
            context.diagnostic("terraceError", error.getMessage());
            return FeatureResult.TERRACE_INVALID_FOOTPRINT;
        }
        if (core.isEmpty()) {
            return FeatureResult.TERRACE_INVALID_FOOTPRINT;
        }
        int target = origin.y + TerrainMutationPlanner.medianSupportY(support) - 1;
        Map<TerrainFootprint.Cell, Integer> targets = new LinkedHashMap<TerrainFootprint.Cell, Integer>();
        int cuts = 0;
        int fills = 0;
        int writes = 0;
        for (TerrainFootprint.Cell column : all.cells()) {
            int surface = sampleSurface(context, terrain.surface, origin.x + column.x, origin.z + column.z);
            if (surface < 0) {
                return readFailure(context);
            }
            int originalTop = surface - 1;
            int distance = all.distanceFrom(core, column, Math.max(1, policy.radius));
            int columnTarget = target;
            if (distance > 0) {
                int remaining = Math.max(1, policy.radius - distance + 1);
                columnTarget = clamp(target, originalTop - policy.maxStep * remaining,
                        originalTop + policy.maxStep * remaining);
                if (policy.transitionType == TerracePolicy.TransitionType.STEPPED) {
                    int delta = target - originalTop;
                    columnTarget = target
                            - Integer.signum(delta) * Math.min(Math.abs(delta), distance * policy.maxStep);
                }
                if (policy.transitionType == TerracePolicy.TransitionType.NATURAL
                        && (TerrainMutationPlanner.mix(placementSeed, column.x, 0, column.z, distance) & 3L) == 0L) {
                    continue;
                }
            }
            int difference = columnTarget - originalTop;
            if (difference > policy.maxFillDepth) {
                return FeatureResult.TERRACE_FILL_LIMIT;
            }
            if (-difference > policy.maxCutDepth) {
                return FeatureResult.TERRACE_CUT_LIMIT;
            }
            targets.put(column, Integer.valueOf(columnTarget));
        }
        for (Map.Entry<TerrainFootprint.Cell, Integer> entry : targets.entrySet()) {
            for (int[] neighbor : NEIGHBORS) {
                Integer adjacent = targets
                        .get(new TerrainFootprint.Cell(entry.getKey().x + neighbor[0], entry.getKey().z + neighbor[1]));
                if (adjacent != null
                        && Math.abs(entry.getValue().intValue() - adjacent.intValue()) > Math.max(1, policy.maxStep)) {
                    return FeatureResult.TERRACE_GRADE_LIMIT;
                }
            }
        }
        String edgeFailure = validateTerraceEdges(context, origin, terrain, policy, all, targets);
        if (edgeFailure != null) {
            return edgeFailure;
        }
        for (Map.Entry<TerrainFootprint.Cell, Integer> entry : targets.entrySet()) {
            TerrainFootprint.Cell column = entry.getKey();
            int x = origin.x + column.x;
            int z = origin.z + column.z;
            int surface = sampleSurface(context, terrain.surface, x, z);
            if (surface < 0) {
                return readFailure(context);
            }
            int originalTop = surface - 1;
            int columnTarget = entry.getValue().intValue();
            if (columnTarget < originalTop) {
                for (int y = originalTop; y > columnTarget; y--) {
                    int existing = context.blockId(x, y, z);
                    if (existing < 0) {
                        return readFailure(context);
                    }
                    if (context.isFluid(existing) && policy.fluids == TerrainReplacementPolicy.Fluid.PRESERVE) {
                        return FeatureResult.TERRACE_FLUID_COLLISION;
                    }
                    String failure = TerrainMutationPlanner.write(context, output, x, y, z, 0, 0, policy.replace,
                            policy.fluids, FeatureResult.TERRACE_PROTECTED_BLOCK, FeatureResult.TERRACE_TILE_COLLISION,
                            FeatureResult.TERRACE_FLUID_COLLISION, PlacementPlan.WritePriority.TERRAIN_ADAPTATION);
                    if (failure != null) {
                        context.diagnostic("terrainRejectedPosition", position(x, y, z));
                        return failure;
                    }
                    cuts++;
                    writes++;
                }
                TerrainMutationPlanner.Material top = TerrainMutationPlanner.material(context, policy.topMaterial,
                        terrain.surface, placementSeed, x, columnTarget, z, 0);
                String failure = TerrainMutationPlanner.write(context, output, x, columnTarget, z, top.blockId,
                        top.metadata, policy.replace, policy.fluids, FeatureResult.TERRACE_PROTECTED_BLOCK,
                        FeatureResult.TERRACE_TILE_COLLISION, FeatureResult.TERRACE_FLUID_COLLISION,
                        PlacementPlan.WritePriority.TERRAIN_ADAPTATION);
                if (failure != null) {
                    context.diagnostic("terrainRejectedPosition", position(x, columnTarget, z));
                    return failure;
                }
                writes++;
            } else if (columnTarget > originalTop) {
                for (int y = originalTop + 1; y <= columnTarget; y++) {
                    int existing = context.blockId(x, y, z);
                    if (existing < 0) {
                        return readFailure(context);
                    }
                    if (context.isFluid(existing) && policy.fluids == TerrainReplacementPolicy.Fluid.PRESERVE) {
                        return FeatureResult.TERRACE_FLUID_COLLISION;
                    }
                    TerrainMaterialPolicy selected = y == columnTarget ? policy.topMaterial : policy.fillMaterial;
                    TerrainMutationPlanner.Material material = TerrainMutationPlanner.material(context, selected,
                            terrain.surface, placementSeed, x, y, z, columnTarget - y);
                    String failure = TerrainMutationPlanner.write(context, output, x, y, z, material.blockId,
                            material.metadata, policy.replace, policy.fluids, FeatureResult.TERRACE_PROTECTED_BLOCK,
                            FeatureResult.TERRACE_TILE_COLLISION, FeatureResult.TERRACE_FLUID_COLLISION,
                            PlacementPlan.WritePriority.TERRAIN_ADAPTATION);
                    if (failure != null) {
                        context.diagnostic("terrainRejectedPosition", position(x, y, z));
                        return failure;
                    }
                    fills++;
                    writes++;
                }
            }
        }
        context.diagnostic("terraceCoreCells", Integer.valueOf(core.size()));
        context.diagnostic("terraceTransitionCells", Integer.valueOf(all.difference(core).size()));
        context.diagnostic("terraceCutBlocks", Integer.valueOf(cuts));
        context.diagnostic("terraceFillBlocks", Integer.valueOf(fills));
        context.diagnostic("terraceBounds", bounds(all, origin));
        if (writes > adaptation.maximumWrites) {
            return FeatureResult.TERRAIN_MUTATION_BUDGET;
        }
        return null;
    }

    private static String validateTerraceEdges(FeatureContext context, BlockPosition origin, TerrainPolicy terrain,
            TerracePolicy policy, TerrainFootprint writable, Map<TerrainFootprint.Cell, Integer> targets) {
        for (Map.Entry<TerrainFootprint.Cell, Integer> entry : targets.entrySet()) {
            TerrainFootprint.Cell column = entry.getKey();
            int originalSurface = sampleSurface(context, terrain.surface, origin.x + column.x, origin.z + column.z);
            if (originalSurface < 0) {
                return readFailure(context);
            }
            int target = entry.getValue().intValue();
            if (target >= originalSurface - 1) {
                continue;
            }
            for (int[] neighbor : NEIGHBORS) {
                int relativeX = column.x + neighbor[0];
                int relativeZ = column.z + neighbor[1];
                if (writable.contains(relativeX, relativeZ)) {
                    continue;
                }
                for (int y = target + 1; y < originalSurface; y++) {
                    int x = origin.x + relativeX;
                    int z = origin.z + relativeZ;
                    int block = context.blockId(x, y, z);
                    if (block < 0) {
                        return readFailure(context);
                    }
                    if (context.hasTileEntity(x, y, z)) {
                        return FeatureResult.TERRACE_TILE_COLLISION;
                    }
                    if (block == net.minecraft.src.Block.bedrock.blockID) {
                        return FeatureResult.TERRACE_PROTECTED_BLOCK;
                    }
                    if (context.isFluid(block) && policy.fluids == TerrainReplacementPolicy.Fluid.REJECT) {
                        return FeatureResult.TERRACE_FLUID_COLLISION;
                    }
                }
            }
        }
        return null;
    }

    private static Map<TerrainFootprint.Cell, Integer> supportHeights(List<StructureTerrainMask.Support> support) {
        Map<TerrainFootprint.Cell, Integer> result = new LinkedHashMap<TerrainFootprint.Cell, Integer>();
        for (StructureTerrainMask.Support cell : support) {
            result.put(new TerrainFootprint.Cell(cell.x, cell.z), Integer.valueOf(cell.y));
        }
        return result;
    }

    private static int unsupportedSpan(TerrainFootprint footprint, List<StructureTerrainMask.Support> support) {
        Map<TerrainFootprint.Cell, Integer> heights = supportHeights(support);
        int maximum = unsupportedRows(footprint, heights);
        for (int x = footprint.minX; x <= footprint.maxX; x++) {
            int run = 0;
            for (int z = footprint.minZ; z <= footprint.maxZ; z++) {
                if (footprint.contains(x, z) && !heights.containsKey(new TerrainFootprint.Cell(x, z))) {
                    maximum = Math.max(maximum, ++run);
                } else {
                    run = 0;
                }
            }
        }
        return maximum;
    }

    private static int unsupportedRows(TerrainFootprint footprint, Map<TerrainFootprint.Cell, Integer> supportHeights) {
        int maximum = 0;
        for (int z = footprint.minZ; z <= footprint.maxZ; z++) {
            int run = 0;
            for (int x = footprint.minX; x <= footprint.maxX; x++) {
                if (footprint.contains(x, z) && !supportHeights.containsKey(new TerrainFootprint.Cell(x, z))) {
                    maximum = Math.max(maximum, ++run);
                } else {
                    run = 0;
                }
            }
        }
        return maximum;
    }

    private static int sampleSurface(FeatureContext context, TerrainSurface surface, int x, int z) {
        return context.surfaceHeight(x, z, surface == TerrainSurface.EXACT ? TerrainSurface.SOLID_SURFACE : surface);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String readFailure(FeatureContext context) {
        return context.failure() == null ? FeatureResult.TERRAIN_UNREADABLE : context.failure();
    }

    private static String bounds(TerrainFootprint footprint, BlockPosition origin) {
        return position(origin.x + footprint.minX, origin.y, origin.z + footprint.minZ) + ".."
                + position(origin.x + footprint.maxX, origin.y, origin.z + footprint.maxZ);
    }

    private static String position(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
