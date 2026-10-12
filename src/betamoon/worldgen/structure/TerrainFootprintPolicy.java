package betamoon.worldgen.structure;

import betamoon.worldgen.BlockPosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.List;
import java.util.TreeSet;

/**
 * Immutable declaration for selecting and shaping a two-dimensional terrain
 * mask.
 */
public final class TerrainFootprintPolicy {
    public enum Source {
        SUPPORT, BASE_BOUNDS, TEMPLATE_BOUNDS, CLEARANCE, NAMED;

        public static Source parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("unknown footprint source: " + value);
            }
        }
    }

    public enum Shape {
        MASK, BOUNDS, CONVEX_HULL, ROUNDED_BOUNDS, AUTHORED;

        public static Shape parse(String value) {
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if (normalized.equals("SUPPORT_FOOTPRINT")) {
                return MASK;
            }
            try {
                return valueOf(normalized);
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("unknown footprint shape: " + value);
            }
        }
    }

    public static final TerrainFootprintPolicy SUPPORT = new TerrainFootprintPolicy(Source.SUPPORT, Shape.MASK, null, 0,
            0, 0, true, Collections.<String>emptySet(), Collections.<String>emptySet(), Collections.<String>emptySet(),
            Collections.<String>emptySet(), "any", null, null, null);

    public final Source source;
    public final Shape shape;
    public final String name;
    public final int padding;
    public final int inset;
    public final int cornerRadius;
    public final boolean preserveHoles;
    public final Set<String> states;
    public final Set<String> excludeStates;
    public final Set<String> tags;
    public final Set<String> excludeTags;
    public final String match;
    public final StructureFeature.Bounds bounds;
    public final Integer minimumHeight;
    public final Integer maximumHeight;
    public final List<BlockPosition> authoredCells;
    public final String nearMarker;
    public final int nearMarkerRadius;

    public TerrainFootprintPolicy(Source source, Shape shape, String name, int padding, int inset, int cornerRadius,
            boolean preserveHoles, Set<String> states, Set<String> excludeStates, Set<String> tags,
            Set<String> excludeTags, String match, StructureFeature.Bounds bounds, Integer minimumHeight,
            Integer maximumHeight) {
        this(source, shape, name, padding, inset, cornerRadius, preserveHoles, states, excludeStates, tags, excludeTags,
                match, bounds, minimumHeight, maximumHeight, Collections.<BlockPosition>emptyList(), null, 0);
    }

    public TerrainFootprintPolicy(Source source, Shape shape, String name, int padding, int inset, int cornerRadius,
            boolean preserveHoles, Set<String> states, Set<String> excludeStates, Set<String> tags,
            Set<String> excludeTags, String match, StructureFeature.Bounds bounds, Integer minimumHeight,
            Integer maximumHeight, List<BlockPosition> authoredCells, String nearMarker, int nearMarkerRadius) {
        this.source = source;
        this.shape = shape;
        this.name = name;
        this.padding = padding;
        this.inset = inset;
        this.cornerRadius = cornerRadius;
        this.preserveHoles = preserveHoles;
        this.states = immutable(states);
        this.excludeStates = immutable(excludeStates);
        this.tags = immutable(tags);
        this.excludeTags = immutable(excludeTags);
        this.match = match;
        this.bounds = bounds;
        this.minimumHeight = minimumHeight;
        this.maximumHeight = maximumHeight;
        List<BlockPosition> orderedCells = authoredCells == null
                ? new ArrayList<BlockPosition>()
                : new ArrayList<BlockPosition>(authoredCells);
        Collections.sort(orderedCells, new Comparator<BlockPosition>() {
            @Override
            public int compare(BlockPosition left, BlockPosition right) {
                int z = Integer.compare(left.z, right.z);
                return z == 0 ? Integer.compare(left.x, right.x) : z;
            }
        });
        this.authoredCells = Collections.unmodifiableList(orderedCells);
        this.nearMarker = nearMarker;
        this.nearMarkerRadius = nearMarkerRadius;
    }

    boolean hasSelector() {
        return !states.isEmpty() || !excludeStates.isEmpty() || !tags.isEmpty() || !excludeTags.isEmpty()
                || bounds != null || minimumHeight != null || maximumHeight != null || nearMarker != null;
    }

    String signature() {
        return source.name() + ':' + shape.name() + ':' + name + ':' + padding + ':' + inset + ':' + cornerRadius + ':'
                + preserveHoles + ':' + new TreeSet<String>(states) + ':' + new TreeSet<String>(excludeStates) + ':'
                + new TreeSet<String>(tags) + ':' + new TreeSet<String>(excludeTags) + ':' + match + ':'
                + bounds(bounds) + ':' + minimumHeight + ':' + maximumHeight + ':' + authoredCells + ':' + nearMarker
                + ':' + nearMarkerRadius;
    }

    private static Set<String> immutable(Set<String> values) {
        return values == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<String>(values));
    }

    private static String bounds(StructureFeature.Bounds value) {
        return value == null
                ? "*"
                : value.min.x + "," + value.min.y + "," + value.min.z + ".." + value.max.x + "," + value.max.y + ","
                        + value.max.z;
    }
}
