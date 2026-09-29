package betamoon.worldgen.structure;

import betamoon.worldgen.BlockPosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Precomputed author markers and conservative fallback masks for structure terrain planning. */
final class StructureTerrainMask {
    private final List<Support> automaticSupport;
    private final List<BlockPosition> authoredSupport;
    private final List<BlockPosition> clearance;
    private final Set<Column> ignored;
    private final Set<Column> conform;
    private final Set<Column> blend;

    StructureTerrainMask(StructureTemplate template) {
        Map<Column, Integer> lowest = new LinkedHashMap<Column, Integer>();
        for (StructureTemplate.TemplateBlock block : template.blocks) {
            StructureTemplate.State state = template.palette.get(block.state).first();
            if (state.blockId == 0) {
                continue;
            }
            int x = block.position.x - template.origin.x;
            int y = block.position.y - template.origin.y;
            int z = block.position.z - template.origin.z;
            Column column = new Column(x, z);
            Integer current = lowest.get(column);
            if (current == null || y < current.intValue()) {
                lowest.put(column, Integer.valueOf(y));
            }
        }
        List<BlockPosition> supports = new ArrayList<BlockPosition>();
        List<BlockPosition> clearances = new ArrayList<BlockPosition>();
        Set<Column> ignores = new LinkedHashSet<Column>();
        Set<Column> conforms = new LinkedHashSet<Column>();
        Set<Column> blends = new LinkedHashSet<Column>();
        for (StructureTemplate.Marker marker : template.markers) {
            int x = marker.position.x - template.origin.x;
            int y = marker.position.y - template.origin.y;
            int z = marker.position.z - template.origin.z;
            if (marker.name.equals("terrain_support")) {
                supports.add(new BlockPosition(x, y, z));
            } else if (marker.name.equals("terrain_clearance")) {
                clearances.add(new BlockPosition(x, y, z));
            } else if (marker.name.equals("terrain_ignore")) {
                ignores.add(new Column(x, z));
            } else if (marker.name.equals("terrain_conform")) {
                conforms.add(new Column(x, z));
            } else if (marker.name.equals("terrain_blend")) {
                blends.add(new Column(x, z));
            }
        }
        List<Support> automatic = new ArrayList<Support>();
        for (Map.Entry<Column, Integer> entry : lowest.entrySet()) {
            if (!ignores.contains(entry.getKey())) {
                automatic.add(new Support(entry.getKey().x, entry.getValue().intValue(), entry.getKey().z));
            }
        }
        automaticSupport = Collections.unmodifiableList(automatic);
        authoredSupport = Collections.unmodifiableList(supports);
        clearance = Collections.unmodifiableList(clearances);
        ignored = Collections.unmodifiableSet(ignores);
        conform = Collections.unmodifiableSet(conforms);
        blend = Collections.unmodifiableSet(blends);
    }

    List<Support> support(StructureTransform transform) {
        List<Support> result = new ArrayList<Support>();
        if (!authoredSupport.isEmpty()) {
            for (BlockPosition cell : authoredSupport) {
                BlockPosition transformed = transform.apply(cell.x, cell.y, cell.z);
                result.add(new Support(transformed.x, transformed.y, transformed.z));
            }
        } else {
            for (Support cell : automaticSupport) {
                BlockPosition transformed = transform.apply(cell.x, cell.y, cell.z);
                result.add(new Support(transformed.x, transformed.y, transformed.z));
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

    boolean conforms(int relativeX, int relativeZ, StructureTransform transform) {
        for (Column column : conform) {
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

        Support(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
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
