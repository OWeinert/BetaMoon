package betamoon.worldgen.structure;

import betamoon.worldgen.BlockPosition;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves transformed support, clearance, bounds, selectors, and named masks
 * into immutable footprints.
 */
final class TerrainFootprintCompiler {
    private static final int MAX_AREA = 4096;

    private TerrainFootprintCompiler() {
    }

    static TerrainFootprint compile(TerrainFootprintPolicy policy, StructureTerrainMask mask,
            StructureTransform transform, StructureFeature.Bounds templateBounds,
            Map<String, TerrainFootprintPolicy> named) {
        return compile(policy, mask, transform, templateBounds, named, new LinkedHashSet<String>());
    }

    private static TerrainFootprint compile(TerrainFootprintPolicy policy, StructureTerrainMask mask,
            StructureTransform transform, StructureFeature.Bounds templateBounds,
            Map<String, TerrainFootprintPolicy> named, Set<String> activeNames) {
        TerrainFootprint required = support(mask, transform, null);
        TerrainFootprint source;
        if (!policy.authoredCells.isEmpty()) {
            Set<TerrainFootprint.Cell> cells = new LinkedHashSet<TerrainFootprint.Cell>();
            for (BlockPosition cell : policy.authoredCells) {
                BlockPosition transformed = transform.apply(cell.x, 0, cell.z);
                cells.add(new TerrainFootprint.Cell(transformed.x, transformed.z));
            }
            source = new TerrainFootprint(cells);
        } else if (policy.source == TerrainFootprintPolicy.Source.NAMED) {
            TerrainFootprintPolicy declaration = named.get(policy.name);
            if (declaration == null) {
                throw new IllegalArgumentException("unknown terrain mask: " + policy.name);
            }
            if (!activeNames.add(policy.name)) {
                throw new IllegalArgumentException("recursive terrain mask: " + policy.name);
            }
            source = compile(declaration, mask, transform, templateBounds, named, activeNames);
            activeNames.remove(policy.name);
        } else if (policy.source == TerrainFootprintPolicy.Source.CLEARANCE) {
            Set<TerrainFootprint.Cell> cells = new LinkedHashSet<TerrainFootprint.Cell>();
            for (BlockPosition cell : mask.authoredClearance(transform)) {
                cells.add(new TerrainFootprint.Cell(cell.x, cell.z));
            }
            source = new TerrainFootprint(cells);
        } else if (policy.source == TerrainFootprintPolicy.Source.TEMPLATE_BOUNDS) {
            source = rectangle(templateBounds.min.x, templateBounds.max.x, templateBounds.min.z, templateBounds.max.z);
        } else {
            source = support(mask, transform, policy.hasSelector() ? policy : null);
            if (policy.source == TerrainFootprintPolicy.Source.BASE_BOUNDS) {
                source = source.bounds();
            }
        }
        if (source.isEmpty()) {
            return source;
        }

        TerrainFootprint shaped = source;
        if (policy.shape == TerrainFootprintPolicy.Shape.BOUNDS) {
            shaped = source.bounds();
        } else if (policy.shape == TerrainFootprintPolicy.Shape.CONVEX_HULL) {
            shaped = source.convexHull();
        } else if (policy.shape == TerrainFootprintPolicy.Shape.ROUNDED_BOUNDS) {
            shaped = source.roundedBounds(policy.cornerRadius);
        }
        shaped = shaped.union(required.intersection(source));
        if (policy.padding > 0) {
            shaped = shaped.dilate(policy.padding);
        }
        if (policy.inset > 0) {
            TerrainFootprint eroded = shaped.erode(policy.inset);
            TerrainFootprint requiredSelected = required.intersection(source);
            if (eroded.isEmpty() || !requiredSelected.difference(eroded).isEmpty()) {
                throw new IllegalArgumentException("footprint inset removes required support cells");
            }
            shaped = eroded;
        }
        if (policy.preserveHoles && policy.shape != TerrainFootprintPolicy.Shape.MASK
                && policy.shape != TerrainFootprintPolicy.Shape.AUTHORED) {
            TerrainFootprint holes = source.bounds().difference(source);
            shaped = shaped.difference(holes);
        }
        if (shaped.size() > MAX_AREA || shaped.maxX - shaped.minX + 1 > 64 || shaped.maxZ - shaped.minZ + 1 > 64) {
            throw new IllegalArgumentException("terrain footprint exceeds 4096 cells or 64 blocks per dimension");
        }
        return shaped;
    }

    private static TerrainFootprint support(StructureTerrainMask mask, StructureTransform transform,
            TerrainFootprintPolicy selector) {
        Set<TerrainFootprint.Cell> cells = new LinkedHashSet<TerrainFootprint.Cell>();
        for (StructureTerrainMask.Support support : mask.support(transform)) {
            if (selector == null || matches(support, selector, mask, transform)) {
                cells.add(new TerrainFootprint.Cell(support.x, support.z));
            }
        }
        return new TerrainFootprint(cells);
    }

    private static boolean matches(StructureTerrainMask.Support support, TerrainFootprintPolicy selector,
            StructureTerrainMask mask, StructureTransform transform) {
        if (excluded(support, selector)) {
            return false;
        }
        if (!selector.states.isEmpty() && !selector.states.contains(support.state)) {
            return false;
        }
        if (!selector.tags.isEmpty()) {
            boolean matches = selector.match.equals("all")
                    ? support.tags.containsAll(selector.tags)
                    : !Collections.disjoint(selector.tags, support.tags);
            if (!matches) {
                return false;
            }
        }
        if (selector.bounds != null
                && (support.authoredX < selector.bounds.min.x || support.authoredX > selector.bounds.max.x
                        || support.authoredY < selector.bounds.min.y || support.authoredY > selector.bounds.max.y
                        || support.authoredZ < selector.bounds.min.z || support.authoredZ > selector.bounds.max.z)) {
            return false;
        }
        if (selector.minimumHeight != null && support.authoredY < selector.minimumHeight.intValue()
                || selector.maximumHeight != null && support.authoredY > selector.maximumHeight.intValue()) {
            return false;
        }
        if (selector.nearMarker != null) {
            boolean near = false;
            for (BlockPosition marker : mask.markers(selector.nearMarker, transform)) {
                near |= Math.max(Math.abs(marker.x - support.x),
                        Math.abs(marker.z - support.z)) <= selector.nearMarkerRadius;
            }
            if (!near) {
                return false;
            }
        }
        return true;
    }

    static boolean excluded(StructureTerrainMask.Support support, TerrainFootprintPolicy selector) {
        return !selector.excludeStates.isEmpty() && selector.excludeStates.contains(support.state)
                || !Collections.disjoint(selector.excludeTags, support.tags);
    }

    private static TerrainFootprint rectangle(int minX, int maxX, int minZ, int maxZ) {
        Set<TerrainFootprint.Cell> cells = new LinkedHashSet<TerrainFootprint.Cell>();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                cells.add(new TerrainFootprint.Cell(x, z));
            }
        }
        return new TerrainFootprint(cells);
    }
}
