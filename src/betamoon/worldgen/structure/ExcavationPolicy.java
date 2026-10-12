package betamoon.worldgen.structure;

import betamoon.worldgen.BlockPosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Independent, bounded terrain-removal declaration applied before adaptation
 * and structure geometry.
 */
public final class ExcavationPolicy {
    public enum Shape {
        FOOTPRINT, BOX, CYLINDER, ELLIPSOID, AUTHORED;

        public static Shape parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("unknown excavation shape: " + value);
            }
        }
    }

    public final List<Volume> volumes;
    public final TerrainReplacementPolicy.Selector replace;
    public final TerrainReplacementPolicy.Fluid fluids;
    public final int resultBlock;
    public final int resultMetadata;
    public final int maxBlocks;
    public final Map<String, List<BlockPosition>> masks;

    public ExcavationPolicy(List<Volume> volumes, TerrainReplacementPolicy.Replace replace,
            TerrainReplacementPolicy.Fluid fluids, int resultBlock, int resultMetadata, int maxBlocks) {
        this(volumes, TerrainReplacementPolicy.Selector.preset(replace), fluids, resultBlock, resultMetadata, maxBlocks,
                Collections.<String, List<BlockPosition>>emptyMap());
    }

    public ExcavationPolicy(List<Volume> volumes, TerrainReplacementPolicy.Selector replace,
            TerrainReplacementPolicy.Fluid fluids, int resultBlock, int resultMetadata, int maxBlocks,
            Map<String, List<BlockPosition>> masks) {
        this.volumes = Collections.unmodifiableList(new ArrayList<Volume>(volumes));
        this.replace = replace;
        this.fluids = fluids;
        this.resultBlock = resultBlock;
        this.resultMetadata = resultMetadata;
        this.maxBlocks = maxBlocks;
        Map<String, List<BlockPosition>> copied = new LinkedHashMap<String, List<BlockPosition>>();
        for (Map.Entry<String, List<BlockPosition>> entry : masks.entrySet()) {
            List<BlockPosition> ordered = new ArrayList<BlockPosition>(entry.getValue());
            Collections.sort(ordered, new Comparator<BlockPosition>() {
                @Override
                public int compare(BlockPosition left, BlockPosition right) {
                    int y = Integer.compare(left.y, right.y);
                    int z = Integer.compare(left.z, right.z);
                    return y != 0 ? y : z != 0 ? z : Integer.compare(left.x, right.x);
                }
            });
            copied.put(entry.getKey(), Collections.unmodifiableList(ordered));
        }
        this.masks = Collections.unmodifiableMap(copied);
    }

    String signature() {
        return volumes + "|" + replace.signature() + '|' + fluids + '|' + resultBlock + ':' + resultMetadata + '|'
                + maxBlocks + '|' + new TreeMap<String, List<BlockPosition>>(masks);
    }

    public static final class Bound {
        public final boolean surface;
        public final int value;

        public Bound(boolean surface, int value) {
            this.surface = surface;
            this.value = value;
        }

        @Override
        public String toString() {
            return surface
                    ? "surface" + (value == 0 ? "" : value > 0 ? "+" + value : String.valueOf(value))
                    : String.valueOf(value);
        }
    }

    public static final class Volume {
        public final Shape shape;
        public final TerrainFootprintPolicy footprint;
        public final Bound from;
        public final Bound to;
        public final BlockPosition minimum;
        public final BlockPosition maximum;
        public final BlockPosition center;
        public final int radiusX;
        public final int radiusY;
        public final int radiusZ;
        public final String name;
        public final List<BlockPosition> cells;

        public Volume(Shape shape, TerrainFootprintPolicy footprint, Bound from, Bound to, BlockPosition minimum,
                BlockPosition maximum, BlockPosition center, int radiusX, int radiusY, int radiusZ, String name) {
            this(shape, footprint, from, to, minimum, maximum, center, radiusX, radiusY, radiusZ, name,
                    Collections.<BlockPosition>emptyList());
        }

        public Volume(Shape shape, TerrainFootprintPolicy footprint, Bound from, Bound to, BlockPosition minimum,
                BlockPosition maximum, BlockPosition center, int radiusX, int radiusY, int radiusZ, String name,
                List<BlockPosition> cells) {
            this.shape = shape;
            this.footprint = footprint;
            this.from = from;
            this.to = to;
            this.minimum = minimum;
            this.maximum = maximum;
            this.center = center;
            this.radiusX = radiusX;
            this.radiusY = radiusY;
            this.radiusZ = radiusZ;
            this.name = name;
            this.cells = cells == null
                    ? Collections.<BlockPosition>emptyList()
                    : Collections.unmodifiableList(new ArrayList<BlockPosition>(cells));
        }

        @Override
        public String toString() {
            return shape + ":" + (footprint == null ? "" : footprint.signature()) + ':' + from + ':' + to + ':'
                    + position(minimum) + ':' + position(maximum) + ':' + position(center) + ':' + radiusX + ':'
                    + radiusY + ':' + radiusZ + ':' + name + ':' + cells;
        }

        private static String position(BlockPosition value) {
            return value == null ? "*" : value.x + "," + value.y + "," + value.z;
        }
    }
}
