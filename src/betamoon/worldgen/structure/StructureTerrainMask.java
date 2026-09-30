package betamoon.worldgen.structure;

import betamoon.worldgen.BlockPosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.src.Block;
import net.minecraft.src.Material;

/**
 * Precomputed author markers and conservative fallback masks for structure
 * terrain planning.
 */
final class StructureTerrainMask {
    private final List<Support> automaticSupport;
    private final List<BlockPosition> authoredSupport;
    private final List<Support> authoredSupportDetails;
    private final List<BlockPosition> clearance;
    private final Set<Column> ignored;
    private final Set<Column> conform;
    private final Set<Column> conformExclude;
    private final Set<Column> blend;
    private final List<StructureTemplate.Marker> markers;
    private final BlockPosition origin;

    StructureTerrainMask(StructureTemplate template) {
        this(template.blocks, template.markers, template.palette, template.origin, null, 0L);
    }

    StructureTerrainMask(StructureTemplate.Resolved resolved, StructureTemplate template) {
        this(resolved.blocks, resolved.markers, template.palette, template.origin, null, 0L);
    }

    StructureTerrainMask(StructureTemplate.Resolved resolved, StructureTemplate template,
            StructureProcessors processors, long placementSeed) {
        this(resolved.blocks, resolved.markers, template.palette, template.origin, processors, placementSeed);
    }

    private StructureTerrainMask(List<StructureTemplate.TemplateBlock> blocks, List<StructureTemplate.Marker> markers,
            List<StructureTemplate.PaletteEntry> palette, BlockPosition origin, StructureProcessors processors,
            long placementSeed) {
        this.markers = Collections.unmodifiableList(new ArrayList<StructureTemplate.Marker>(markers));
        this.origin = origin;
        Map<Column, Support> lowest = new LinkedHashMap<Column, Support>();
        for (StructureTemplate.TemplateBlock block : blocks) {
            if (processors == null) {
                boolean hasNonAirVariant = false;
                for (StructureTemplate.State state : palette.get(block.state).states) {
                    hasNonAirVariant |= isStructural(state.blockId);
                }
                if (!hasNonAirVariant) {
                    continue;
                }
            } else {
                Random random = new Random(StructureFeature.blockSeed(placementSeed, block));
                StructureTemplate.State selected = palette.get(block.state).select(random);
                if (selected.blockId == 0 || processors.decay > 0.0D && random.nextDouble() < processors.decay) {
                    continue;
                }
                Integer replacement = processors.replacements.get(Integer.valueOf(selected.blockId));
                int resolvedBlock = replacement == null ? selected.blockId : replacement.intValue();
                if (!isStructural(resolvedBlock)) {
                    continue;
                }
            }
            int x = block.position.x - origin.x;
            int y = block.position.y - origin.y;
            int z = block.position.z - origin.z;
            Column column = new Column(x, z);
            Support current = lowest.get(column);
            if (current == null || y < current.y) {
                Set<String> tags = new LinkedHashSet<String>(palette.get(block.state).tags);
                tags.addAll(block.tags);
                lowest.put(column, new Support(x, y, z, x, y, z, palette.get(block.state).name, tags));
            }
        }
        List<BlockPosition> supports = new ArrayList<BlockPosition>();
        List<BlockPosition> clearances = new ArrayList<BlockPosition>();
        Set<Column> ignores = new LinkedHashSet<Column>();
        Set<Column> conforms = new LinkedHashSet<Column>();
        Set<Column> conformExcludes = new LinkedHashSet<Column>();
        Set<Column> blends = new LinkedHashSet<Column>();
        for (StructureTemplate.Marker marker : markers) {
            int x = marker.position.x - origin.x;
            int y = marker.position.y - origin.y;
            int z = marker.position.z - origin.z;
            if (marker.name.equals("terrain_support")) {
                supports.add(new BlockPosition(x, y, z));
            } else if (marker.name.equals("terrain_clearance")) {
                clearances.add(new BlockPosition(x, y, z));
            } else if (marker.name.equals("terrain_ignore")) {
                ignores.add(new Column(x, z));
            } else if (marker.name.equals("terrain_conform")) {
                conforms.add(new Column(x, z));
            } else if (marker.name.equals("terrain_conform_exclude")) {
                conformExcludes.add(new Column(x, z));
            } else if (marker.name.equals("terrain_blend")) {
                blends.add(new Column(x, z));
            }
        }
        List<Support> automatic = new ArrayList<Support>();
        int baseY = Integer.MAX_VALUE;
        for (Support support : lowest.values()) {
            baseY = Math.min(baseY, support.y);
        }
        for (Map.Entry<Column, Support> entry : lowest.entrySet()) {
            if (!ignores.contains(entry.getKey()) && entry.getValue().y == baseY) {
                automatic.add(entry.getValue());
            }
        }
        List<BlockPosition> authored = new ArrayList<BlockPosition>(supports);
        List<Support> authoredDetails = new ArrayList<Support>();
        supports.clear();
        for (BlockPosition position : authored) {
            Support details = lowest.get(new Column(position.x, position.z));
            Set<String> tags = details == null ? Collections.<String>emptySet() : details.tags;
            String state = details == null ? null : details.state;
            supports.add(position);
            authoredDetails.add(
                    new Support(position.x, position.y, position.z, position.x, position.y, position.z, state, tags));
        }
        automaticSupport = Collections.unmodifiableList(automatic);
        authoredSupport = Collections.unmodifiableList(supports);
        authoredSupportDetails = Collections.unmodifiableList(authoredDetails);
        clearance = Collections.unmodifiableList(clearances);
        ignored = Collections.unmodifiableSet(ignores);
        conform = Collections.unmodifiableSet(conforms);
        conformExclude = Collections.unmodifiableSet(conformExcludes);
        blend = Collections.unmodifiableSet(blends);
    }

    private static boolean isStructural(int blockId) {
        if (blockId <= 0 || blockId >= Block.blocksList.length || Block.blocksList[blockId] == null) {
            return false;
        }
        Material material = Block.blocksList[blockId].blockMaterial;
        return material.getIsSolid() && material != Material.leaves && material != Material.snow
                && material != Material.water && material != Material.lava;
    }

    List<Support> support(StructureTransform transform) {
        List<Support> result = new ArrayList<Support>();
        if (!authoredSupport.isEmpty()) {
            for (Support cell : authoredSupportDetails) {
                BlockPosition transformed = transform.apply(cell.x, cell.y, cell.z);
                result.add(cell.transformed(transformed));
            }
        } else {
            for (Support cell : automaticSupport) {
                BlockPosition transformed = transform.apply(cell.x, cell.y, cell.z);
                result.add(cell.transformed(transformed));
            }
        }
        return Collections.unmodifiableList(result);
    }

    List<BlockPosition> clearance(StructureTransform transform, StructureFeature.Bounds relativeBounds) {
        List<BlockPosition> result = new ArrayList<BlockPosition>();
        if (!clearance.isEmpty()) {
            for (BlockPosition cell : clearance) {
                result.add(transform.apply(cell.x, cell.y, cell.z));
            }
            return Collections.unmodifiableList(result);
        }
        for (int x = relativeBounds.min.x; x <= relativeBounds.max.x; x++) {
            for (int z = relativeBounds.min.z; z <= relativeBounds.max.z; z++) {
                for (int y = relativeBounds.min.y; y <= relativeBounds.max.y; y++) {
                    result.add(new BlockPosition(x, y, z));
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    List<BlockPosition> authoredClearance(StructureTransform transform) {
        List<BlockPosition> result = new ArrayList<BlockPosition>();
        for (BlockPosition cell : clearance) {
            result.add(transform.apply(cell.x, cell.y, cell.z));
        }
        return Collections.unmodifiableList(result);
    }

    List<BlockPosition> markers(String name, StructureTransform transform) {
        List<BlockPosition> result = new ArrayList<BlockPosition>();
        for (StructureTemplate.Marker marker : markers) {
            if (marker.name.equals(name)) {
                BlockPosition local = transform.apply(marker.position.x - origin.x, marker.position.y - origin.y,
                        marker.position.z - origin.z);
                result.add(local);
            }
        }
        return Collections.unmodifiableList(result);
    }

    boolean conforms(int relativeX, int relativeZ, StructureTransform transform) {
        for (Column column : conform) {
            BlockPosition transformed = transform.apply(column.x, 0, column.z);
            if (transformed.x == relativeX && transformed.z == relativeZ) {
                return true;
            }
        }
        return false;
    }

    boolean excludesConform(int relativeX, int relativeZ, StructureTransform transform) {
        for (Column column : conformExclude) {
            BlockPosition transformed = transform.apply(column.x, 0, column.z);
            if (transformed.x == relativeX && transformed.z == relativeZ) {
                return true;
            }
        }
        return false;
    }

    List<BlockPosition> blend(StructureTransform transform) {
        List<BlockPosition> result = new ArrayList<BlockPosition>();
        for (Column column : blend) {
            result.add(transform.apply(column.x, 0, column.z));
        }
        return Collections.unmodifiableList(result);
    }

    int supportColumns() {
        return authoredSupport.isEmpty() ? automaticSupport.size() : authoredSupport.size();
    }

    static final class Support {
        final int x;
        final int y;
        final int z;
        final int authoredX;
        final int authoredY;
        final int authoredZ;
        final String state;
        final Set<String> tags;

        Support(int x, int y, int z, int authoredX, int authoredY, int authoredZ, String state, Set<String> tags) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.authoredX = authoredX;
            this.authoredY = authoredY;
            this.authoredZ = authoredZ;
            this.state = state;
            this.tags = Collections.unmodifiableSet(new LinkedHashSet<String>(tags));
        }

        private Support transformed(BlockPosition position) {
            return new Support(position.x, position.y, position.z, authoredX, authoredY, authoredZ, state, tags);
        }
    }

    private static final class Column {
        private final int x;
        private final int z;

        private Column(int x, int z) {
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
