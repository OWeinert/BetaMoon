package betamoon.worldgen.structure;

import betamoon.worldgen.BlockPosition;
import betamoon.worldgen.FeatureContext;
import betamoon.worldgen.FeatureResult;
import betamoon.worldgen.PlacementPlan;
import betamoon.worldgen.TerrainSurface;
import betamoon.worldgen.WorldGenLimits;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared bounded planner for structure fitting, site validation, and terrain
 * mutations.
 */
final class StructureTerrainPlanner {
    private StructureTerrainPlanner() {
    }

    static Result prepare(FeatureContext context, BlockPosition requestedOrigin, StructureTransform transform,
            TerrainPolicy terrain, SitePolicy site, StructureTerrainMask mask, StructureFeature.Bounds relativeBounds,
            PlacementPlan output, long placementSeed) {
        List<StructureTerrainMask.Support> support = mask.support(transform);
        context.diagnostic("terrainMode", terrain.mode.luaName());
        context.diagnostic("terrainSurface", terrain.surface.getName());
        context.diagnostic("siteType", site.type.luaName());
        context.diagnostic("supportColumns", Integer.valueOf(support.size()));
        context.diagnostic("requestedAnchorY", Integer.valueOf(requestedOrigin.y));
        if (support.isEmpty()
                && (terrain.mode != TerrainPolicy.Mode.EXACT || terrain.excavation == null || site.active())) {
            return Result.rejected(FeatureResult.TERRAIN_SUPPORT);
        }

        BlockPosition resolved = terrain.mode == TerrainPolicy.Mode.EXACT || terrain.mode == TerrainPolicy.Mode.CONFORM
                ? requestedOrigin.offset(0, terrain.verticalOffset, 0)
                : requestedOrigin;
        Map<Column, Integer> sampledOrigins = Collections.emptyMap();
        if (terrain.mode != TerrainPolicy.Mode.EXACT && terrain.mode != TerrainPolicy.Mode.CONFORM) {
            sampledOrigins = sampleOrigins(context, requestedOrigin, support, terrain.surface);
            if (sampledOrigins == null) {
                return Result.rejected(readFailure(context));
            }
            String shapeFailure = validateShape(sampledOrigins, terrain);
            if (shapeFailure != null) {
                return Result.rejected(shapeFailure);
            }
            int y = anchor(sampledOrigins, terrain, requestedOrigin) + terrain.verticalOffset;
            int minimum = Integer.MAX_VALUE;
            int maximum = Integer.MIN_VALUE;
            for (Integer candidate : sampledOrigins.values()) {
                minimum = Math.min(minimum, candidate.intValue());
                maximum = Math.max(maximum, candidate.intValue());
            }
            context.diagnostic("sampledMinimumY", Integer.valueOf(minimum));
            context.diagnostic("sampledMaximumY", Integer.valueOf(maximum));
            if (y < WorldGenLimits.MIN_HEIGHT || y > WorldGenLimits.MAX_HEIGHT) {
                return Result.rejected(FeatureResult.OUT_OF_BOUNDS);
            }
            resolved = new BlockPosition(requestedOrigin.x, y, requestedOrigin.z);
            int supported = 0;
            for (Integer candidate : sampledOrigins.values()) {
                if (candidate.intValue() == y - terrain.verticalOffset) {
                    supported++;
                }
            }
            if ((double) supported / sampledOrigins.size() < terrain.requireSupportRatio) {
                context.diagnostic("supportRatio", Double.valueOf((double) supported / sampledOrigins.size()));
                return Result.rejected(FeatureResult.TERRAIN_SUPPORT);
            }
            context.diagnostic("supportRatio", Double.valueOf((double) supported / sampledOrigins.size()));
        }
        if (resolved.y < WorldGenLimits.MIN_HEIGHT || resolved.y > WorldGenLimits.MAX_HEIGHT) {
            return Result.rejected(FeatureResult.OUT_OF_BOUNDS);
        }

        String siteFailure = validateSite(context, resolved, transform, support, mask, relativeBounds, terrain, site);
        if (siteFailure != null) {
            return Result.rejected(siteFailure);
        }

        String excavationFailure = ExcavationPlanner.plan(context, resolved, transform, terrain, mask, relativeBounds,
                output);
        if (excavationFailure != null) {
            return Result.rejected(excavationFailure);
        }
        Set<BlockPosition> excavated = new LinkedHashSet<BlockPosition>();
        for (PlacementPlan.PlannedBlock block : output.plannedBlocks()) {
            if (output.hasPriority(block.position, PlacementPlan.WritePriority.EXCAVATION)) {
                excavated.add(block.position);
            }
        }

        if (terrain.foundation != null) {
            String failure = TerrainAdaptationPlanner.planFoundation(context, resolved, transform, support, terrain,
                    mask, relativeBounds, output, placementSeed);
            if (failure != null) {
                return Result.rejected(failure);
            }
        } else if (terrain.terrace != null) {
            String failure = TerrainAdaptationPlanner.planTerrace(context, resolved, transform, support, terrain, mask,
                    relativeBounds, output, placementSeed);
            if (failure != null) {
                return Result.rejected(failure);
            }
        }
        int adaptationOverlap = 0;
        for (BlockPosition position : excavated) {
            if (output.hasPriority(position, PlacementPlan.WritePriority.TERRAIN_ADAPTATION)) {
                adaptationOverlap++;
            }
        }
        context.diagnostic("excavationAdaptationOverlaps", Integer.valueOf(adaptationOverlap));
        context.diagnostic("terrainWritesPlanned", Integer.valueOf(output.size()));

        Map<Column, Integer> conformOffsets = terrain.conform != null
                ? conformOffsets(context, resolved, transform, terrain, mask, support, relativeBounds)
                : Collections.<Column, Integer>emptyMap();
        if (conformOffsets == null) {
            return Result.rejected(readFailure(context));
        }
        context.diagnostic("resolvedAnchorY", Integer.valueOf(resolved.y));
        context.diagnostic("terrainBlocksPlanned", Integer.valueOf(output.size()));
        return Result.accepted(resolved, conformOffsets);
    }

    private static Map<Column, Integer> sampleOrigins(FeatureContext context, BlockPosition origin,
            List<StructureTerrainMask.Support> support, TerrainSurface surface) {
        Map<Column, Integer> result = new LinkedHashMap<Column, Integer>();
        for (StructureTerrainMask.Support cell : support) {
            int height = context.surfaceHeight(origin.x + cell.x, origin.z + cell.z, surface);
            if (height < 0) {
                return null;
            }
            result.put(new Column(cell.x, cell.z), Integer.valueOf(height - cell.y));
        }
        return result;
    }

    private static String validateShape(Map<Column, Integer> heights, TerrainPolicy terrain) {
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (Integer height : heights.values()) {
            minimum = Math.min(minimum, height.intValue());
            maximum = Math.max(maximum, height.intValue());
        }
        if (maximum - minimum > terrain.maxSlope) {
            return FeatureResult.TERRAIN_SLOPE;
        }
        for (Map.Entry<Column, Integer> entry : heights.entrySet()) {
            for (int[] offset : NEIGHBORS) {
                Integer neighbor = heights.get(new Column(entry.getKey().x + offset[0], entry.getKey().z + offset[1]));
                if (neighbor != null && Math.abs(entry.getValue().intValue() - neighbor.intValue()) > terrain.maxStep) {
                    return FeatureResult.TERRAIN_STEP;
                }
            }
        }
        return null;
    }

    private static int anchor(Map<Column, Integer> heights, TerrainPolicy terrain, BlockPosition origin) {
        List<Integer> values = new ArrayList<Integer>(heights.values());
        Collections.sort(values);
        switch (terrain.anchor) {
            case MINIMUM:
                return values.get(0).intValue();
            case MAXIMUM:
                return values.get(values.size() - 1).intValue();
            case MEAN:
                long total = 0L;
                for (Integer value : values) {
                    total += value.intValue();
                }
                return (int) Math.round((double) total / values.size());
            case CENTER:
                Integer center = heights.get(new Column(0, 0));
                return center == null ? values.get((values.size() - 1) / 2).intValue() : center.intValue();
            case PERCENTILE:
                int index = (int) Math.round(terrain.percentile * (values.size() - 1));
                return values.get(index).intValue();
            default:
                return values.get((values.size() - 1) / 2).intValue();
        }
    }

    private static String validateSite(FeatureContext context, BlockPosition origin, StructureTransform transform,
            List<StructureTerrainMask.Support> support, StructureTerrainMask mask,
            StructureFeature.Bounds relativeBounds, TerrainPolicy terrain, SitePolicy site) {
        if (!site.active()) {
            return null;
        }
        int fluidCovered = 0;
        int minimumFluidDepth = Integer.MAX_VALUE;
        int maximumFluidDepth = Integer.MIN_VALUE;
        boolean wrongSurfaceRelation = false;
        for (StructureTerrainMask.Support cell : support) {
            int x = origin.x + cell.x;
            int z = origin.z + cell.z;
            int floor = context.surfaceHeight(x, z, TerrainSurface.OCEAN_FLOOR);
            int solid = context.surfaceHeight(x, z, TerrainSurface.SOLID_SURFACE);
            int fluid = context.surfaceHeight(x, z, TerrainSurface.FLUID_SURFACE);
            if ((floor < 0 || solid < 0) && context.failure() != null) {
                return readFailure(context);
            }
            int contact = origin.y + cell.y;
            int fittedContact = contact - terrain.verticalOffset;
            if (site.type == SitePolicy.Type.LAND_SURFACE) {
                if (terrain.mode == TerrainPolicy.Mode.EXACT) {
                    wrongSurfaceRelation |= contact != solid;
                } else if (terrain.mode == TerrainPolicy.Mode.FOUNDATION) {
                    wrongSurfaceRelation |= fittedContact < solid;
                }
            } else if (site.type == SitePolicy.Type.UNDERWATER) {
                if (terrain.mode == TerrainPolicy.Mode.EXACT) {
                    wrongSurfaceRelation |= contact != floor;
                } else if (terrain.mode == TerrainPolicy.Mode.FOUNDATION) {
                    wrongSurfaceRelation |= fittedContact < floor;
                }
            } else if (site.type == SitePolicy.Type.FLUID_SURFACE) {
                wrongSurfaceRelation |= fluid < 0 || (terrain.mode == TerrainPolicy.Mode.EXACT && contact != fluid);
            }
            if (fluid >= 0 && fluid > floor) {
                int block = context.blockId(x, floor, z);
                if (block < 0) {
                    return readFailure(context);
                }
                boolean selectedFluid = site.medium == SitePolicy.Medium.WATER
                        ? context.isWater(block)
                        : site.medium == SitePolicy.Medium.LAVA ? context.isLava(block) : context.isFluid(block);
                if (selectedFluid) {
                    fluidCovered++;
                    int depth = fluid - floor;
                    minimumFluidDepth = Math.min(minimumFluidDepth, depth);
                    maximumFluidDepth = Math.max(maximumFluidDepth, depth);
                }
            }
        }
        double fluidCoverage = (double) fluidCovered / support.size();
        context.diagnostic("fluidCoverage", Double.valueOf(fluidCoverage));
        if (wrongSurfaceRelation || site.type == SitePolicy.Type.LAND_SURFACE && fluidCovered > 0) {
            return FeatureResult.SITE_WRONG_SURFACE_RELATION;
        }
        if (site.type == SitePolicy.Type.UNDERWATER && fluidCovered == 0) {
            return FeatureResult.SITE_WRONG_SURFACE_RELATION;
        }
        if (fluidCoverage < site.minFluidCoverage || fluidCoverage > site.maxFluidCoverage) {
            return FeatureResult.SITE_FLUID_COVERAGE;
        }
        if (fluidCovered > 0 && (minimumFluidDepth < site.minFluidDepth || maximumFluidDepth > site.maxFluidDepth)) {
            return FeatureResult.SITE_FLUID_DEPTH;
        }

        List<BlockPosition> cells = siteCells(origin, transform, support, mask, relativeBounds, site.scope);
        int air = 0;
        int mediumMatches = 0;
        int minimumDepth = Integer.MAX_VALUE;
        int minimumCover = Integer.MAX_VALUE;
        Map<Long, BlockPosition> caveTops = new LinkedHashMap<Long, BlockPosition>();
        for (BlockPosition cell : cells) {
            int block = context.blockId(cell.x, cell.y, cell.z);
            if (block < 0) {
                return readFailure(context);
            }
            if (block == 0) {
                air++;
            }
            if (matchesMedium(context, block, site.medium)) {
                mediumMatches++;
            }
            int surface = context.surfaceHeight(cell.x, cell.z, TerrainSurface.SOLID_SURFACE);
            if (surface < 0) {
                return readFailure(context);
            }
            int depth = surface - cell.y;
            minimumDepth = Math.min(minimumDepth, depth);
            if (site.type == SitePolicy.Type.CAVE) {
                long key = ((long) cell.x << 32) ^ (cell.z & 0xffffffffL);
                BlockPosition top = caveTops.get(Long.valueOf(key));
                if (top == null || cell.y > top.y) {
                    caveTops.put(Long.valueOf(key), cell);
                }
            }
            if (site.requireSky != null
                    && context.canSeeSky(cell.x, cell.y, cell.z) != site.requireSky.booleanValue()) {
                return FeatureResult.SITE_SKY_MISMATCH;
            }
        }
        if (site.medium != SitePolicy.Medium.ANY && mediumMatches != cells.size()) {
            return FeatureResult.SITE_WRONG_MEDIUM;
        }
        if ((site.type == SitePolicy.Type.UNDERGROUND || site.type == SitePolicy.Type.CAVE)
                && (minimumDepth < site.minDepthBelowSurface || minimumDepth > site.maxDepthBelowSurface)) {
            return FeatureResult.SITE_DEPTH;
        }
        if (site.type == SitePolicy.Type.CAVE) {
            for (BlockPosition top : caveTops.values()) {
                int cover = solidCover(context, top.x, top.y + 1, top.z);
                if (cover < 0) {
                    return readFailure(context);
                }
                minimumCover = Math.min(minimumCover, cover);
            }
            context.diagnostic("existingAirRatio", Double.valueOf((double) air / cells.size()));
            if ((double) air / cells.size() < site.minExistingAirRatio) {
                return FeatureResult.SITE_INSUFFICIENT_CAVITY;
            }
            if (minimumCover < site.minSolidCover || minimumCover > site.maxSolidCover) {
                return FeatureResult.SITE_INSUFFICIENT_COVER;
            }
        }
        return null;
    }

    private static int solidCover(FeatureContext context, int x, int startY, int z) {
        boolean roof = false;
        int cover = 0;
        for (int y = Math.max(WorldGenLimits.MIN_HEIGHT, startY); y <= WorldGenLimits.MAX_HEIGHT; y++) {
            int block = context.blockId(x, y, z);
            if (block < 0) {
                return -1;
            }
            if (context.isStructural(block)) {
                roof = true;
                cover++;
            } else if (roof) {
                break;
            }
        }
        return cover;
    }

    private static List<BlockPosition> siteCells(BlockPosition origin, StructureTransform transform,
            List<StructureTerrainMask.Support> support, StructureTerrainMask mask,
            StructureFeature.Bounds relativeBounds, SitePolicy.Scope scope) {
        List<BlockPosition> result = new ArrayList<BlockPosition>();
        if (scope == SitePolicy.Scope.ORIGIN) {
            result.add(origin);
        } else if (scope == SitePolicy.Scope.SUPPORT_FOOTPRINT) {
            for (StructureTerrainMask.Support cell : support) {
                result.add(origin.offset(cell.x, cell.y, cell.z));
            }
        } else if (scope == SitePolicy.Scope.CLEARANCE_MASK) {
            for (BlockPosition cell : mask.clearance(transform, relativeBounds)) {
                result.add(origin.offset(cell.x, cell.y, cell.z));
            }
        } else {
            for (int x = relativeBounds.min.x; x <= relativeBounds.max.x; x++) {
                for (int y = relativeBounds.min.y; y <= relativeBounds.max.y; y++) {
                    for (int z = relativeBounds.min.z; z <= relativeBounds.max.z; z++) {
                        result.add(origin.offset(x, y, z));
                    }
                }
            }
        }
        return result;
    }

    private static boolean matchesMedium(FeatureContext context, int block, SitePolicy.Medium medium) {
        switch (medium) {
            case AIR:
                return block == 0;
            case WATER:
                return context.isWater(block);
            case LAVA:
                return context.isLava(block);
            case ANY_FLUID:
                return context.isFluid(block);
            case SOLID:
                return context.isStructural(block);
            default:
                return true;
        }
    }

    private static Map<Column, Integer> conformOffsets(FeatureContext context, BlockPosition origin,
            StructureTransform transform, TerrainPolicy terrain, StructureTerrainMask mask,
            List<StructureTerrainMask.Support> support, StructureFeature.Bounds relativeBounds) {
        ConformPolicy policy = terrain.conform;
        Set<Column> selected = new LinkedHashSet<Column>();
        if (!policy.compatibilityMarkersOnly) {
            try {
                TerrainFootprint footprint = TerrainFootprintCompiler.compile(policy.columns, mask, transform,
                        relativeBounds, terrain.masks);
                for (TerrainFootprint.Cell cell : footprint.cells()) {
                    selected.add(new Column(cell.x, cell.z));
                }
            } catch (IllegalArgumentException error) {
                context.diagnostic("conformError", error.getMessage());
                context.markFailure(error.getMessage().contains("unknown terrain mask")
                        ? FeatureResult.CONFORM_UNKNOWN_MASK
                        : FeatureResult.CONFORM_SELECTOR_INVALID);
                return null;
            }
        }
        Set<Column> supportColumns = new LinkedHashSet<Column>();
        for (StructureTerrainMask.Support cell : support) {
            Column column = new Column(cell.x, cell.z);
            supportColumns.add(column);
            if (mask.conforms(cell.x, cell.z, transform) && !TerrainFootprintCompiler.excluded(cell, policy.columns)) {
                selected.add(column);
            }
            if (mask.excludesConform(cell.x, cell.z, transform)) {
                selected.remove(column);
            }
        }
        selected.retainAll(supportColumns);
        if (selected.isEmpty() && !policy.allowEmpty && !policy.compatibilityMarkersOnly) {
            context.markFailure(FeatureResult.CONFORM_EMPTY_SELECTION);
            return null;
        }
        Map<Column, Integer> result = new LinkedHashMap<Column, Integer>();
        for (StructureTerrainMask.Support cell : support) {
            if (!selected.contains(new Column(cell.x, cell.z))) {
                continue;
            }
            int surface = context.surfaceHeight(origin.x + cell.x, origin.z + cell.z, terrain.surface);
            if (surface < 0) {
                return null;
            }
            int displacement = surface - (origin.y + cell.y);
            if (Math.abs(displacement) > policy.maxDisplacement) {
                context.markFailure(FeatureResult.CONFORM_DISPLACEMENT_LIMIT);
                return null;
            }
            result.put(new Column(cell.x, cell.z), Integer.valueOf(displacement));
        }
        for (int pass = 0; pass < policy.smoothingPasses; pass++) {
            Map<Column, Integer> smoothed = new LinkedHashMap<Column, Integer>();
            for (Map.Entry<Column, Integer> entry : result.entrySet()) {
                int total = entry.getValue().intValue();
                int count = 1;
                for (Map.Entry<Column, Integer> candidate : result.entrySet()) {
                    if (!candidate.getKey().equals(entry.getKey())
                            && Math.max(Math.abs(candidate.getKey().x - entry.getKey().x),
                                    Math.abs(candidate.getKey().z - entry.getKey().z)) <= policy.smoothingRadius) {
                        total += candidate.getValue().intValue();
                        count++;
                    }
                }
                int value = (int) Math.round((double) total / count);
                smoothed.put(entry.getKey(),
                        Integer.valueOf(clamp(value, -policy.maxDisplacement, policy.maxDisplacement)));
            }
            result = smoothed;
        }
        for (Map.Entry<Column, Integer> entry : result.entrySet()) {
            for (int[] offset : NEIGHBORS) {
                Integer neighbor = result.get(new Column(entry.getKey().x + offset[0], entry.getKey().z + offset[1]));
                if (neighbor != null && Math.abs(entry.getValue().intValue() - neighbor.intValue()) > policy.maxStep) {
                    context.markFailure(FeatureResult.CONFORM_STEP_LIMIT);
                    return null;
                }
            }
        }
        int minimum = 0;
        int maximum = 0;
        for (Integer value : result.values()) {
            minimum = Math.min(minimum, value.intValue());
            maximum = Math.max(maximum, value.intValue());
        }
        context.diagnostic("conformSource", policy.compatibilityMarkersOnly ? "markers" : policy.columns.signature());
        context.diagnostic("conformSupportColumns", Integer.valueOf(support.size()));
        context.diagnostic("conformSelectedColumns", Integer.valueOf(result.size()));
        context.diagnostic("conformComponents", Integer.valueOf(components(result.keySet())));
        context.diagnostic("conformMinimumDisplacement", Integer.valueOf(minimum));
        context.diagnostic("conformMaximumDisplacement", Integer.valueOf(maximum));
        return result;
    }

    private static int components(Set<Column> cells) {
        Set<Column> remaining = new LinkedHashSet<Column>(cells);
        int result = 0;
        while (!remaining.isEmpty()) {
            result++;
            List<Column> pending = new ArrayList<Column>();
            pending.add(remaining.iterator().next());
            while (!pending.isEmpty()) {
                Column cell = pending.remove(pending.size() - 1);
                if (!remaining.remove(cell)) {
                    continue;
                }
                for (int[] neighbor : NEIGHBORS) {
                    Column adjacent = new Column(cell.x + neighbor[0], cell.z + neighbor[1]);
                    if (remaining.contains(adjacent)) {
                        pending.add(adjacent);
                    }
                }
            }
        }
        return result;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String readFailure(FeatureContext context) {
        return context.failure() == null ? FeatureResult.TERRAIN_UNREADABLE : context.failure();
    }

    private static final int[][] NEIGHBORS = new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    static final class Result {
        final BlockPosition origin;
        final Map<Column, Integer> conformOffsets;
        final String failure;

        private Result(BlockPosition origin, Map<Column, Integer> conformOffsets, String failure) {
            this.origin = origin;
            this.conformOffsets = conformOffsets;
            this.failure = failure;
        }

        static Result accepted(BlockPosition origin, Map<Column, Integer> offsets) {
            return new Result(origin, offsets, null);
        }

        static Result rejected(String failure) {
            return new Result(null, Collections.<Column, Integer>emptyMap(), failure);
        }

        int conformOffset(int x, int z) {
            Integer value = conformOffsets.get(new Column(x, z));
            return value == null ? 0 : value.intValue();
        }

        List<StructureFeature.ConformOffset> conformOffsets() {
            List<StructureFeature.ConformOffset> result = new ArrayList<StructureFeature.ConformOffset>();
            for (Map.Entry<Column, Integer> entry : conformOffsets.entrySet()) {
                result.add(new StructureFeature.ConformOffset(entry.getKey().x, entry.getKey().z,
                        entry.getValue().intValue()));
            }
            return Collections.unmodifiableList(result);
        }
    }

    private static final class Column {
        final int x;
        final int z;

        Column(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object value) {
            return value instanceof Column && x == ((Column) value).x && z == ((Column) value).z;
        }

        @Override
        public int hashCode() {
            return x * 31 + z;
        }
    }
}
