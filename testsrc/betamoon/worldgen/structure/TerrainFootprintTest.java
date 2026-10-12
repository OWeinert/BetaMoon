package betamoon.worldgen.structure;

import betamoon.worldgen.BlockPosition;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import net.minecraft.src.Block;

/**
 * Verifies deterministic mask algebra, support derivation, selectors, and every
 * transform variant.
 */
public final class TerrainFootprintTest {
    private TerrainFootprintTest() {
    }

    public static void main(String[] arguments) throws Exception {
        require(Block.stone != null, "Vanilla blocks are initialized");
        TerrainFootprint diagonal = footprint(new int[][]{{0, 0}, {3, 3}}).convexHull();
        require(diagonal.size() == 4 && diagonal.contains(1, 1) && !diagonal.contains(1, 2),
                "A collinear convex hull rasterizes a line rather than its complete bounds");

        TerrainFootprint ring = footprint(new int[][]{{0, 0}, {1, 0}, {2, 0}, {0, 1}, {2, 1}, {0, 2}, {1, 2}, {2, 2}});
        require(!ring.contains(1, 1) && ring.dilate(1).contains(1, 1) && ring.bounds().size() == 9,
                "Mask, bounds, union-style dilation, and authored holes remain distinct");
        require(footprint(new int[][]{{0, 0}, {4, 0}, {0, 4}, {4, 4}}).roundedBounds(2).size() < 25,
                "Rounded bounds chamfer rectangle corners deterministically");

        StructureTemplate template = StructureTemplate.read(("{\"format\":\"betamoon_structure\","
                + "\"palette\":{\"base\":{\"block\":1,\"tags\":[\"road_surface\"]},"
                + "\"roof\":{\"block\":1}},\"elements\":[" + "{\"type\":\"block\",\"pos\":[0,0,0],\"state\":\"base\"},"
                + "{\"type\":\"block\",\"pos\":[1,0,0],\"state\":\"base\"},"
                + "{\"type\":\"block\",\"pos\":[4,4,0],\"state\":\"roof\"}]}").getBytes(StandardCharsets.UTF_8));
        StructureTerrainMask mask = new StructureTerrainMask(template.resolve(17L), template);
        StructureFeature.Bounds bounds = new StructureFeature.Bounds(new BlockPosition(0, 0, 0),
                new BlockPosition(4, 4, 0));
        TerrainFootprintPolicy baseBounds = policy(TerrainFootprintPolicy.Source.BASE_BOUNDS,
                TerrainFootprintPolicy.Shape.BOUNDS, Collections.<String>emptySet());
        TerrainFootprint resolved = TerrainFootprintCompiler.compile(baseBounds, mask,
                new StructureTransform(StructureTransform.Rotation.NONE, StructureTransform.Mirror.NONE), bounds,
                Collections.<String, TerrainFootprintPolicy>emptyMap());
        require(resolved.size() == 2 && resolved.maxX == 1,
                "Roof-only overhang columns do not enlarge support-derived base bounds");

        StructureTemplate squareTemplate = StructureTemplate
                .read(("{\"format\":\"betamoon_structure\"," + "\"palette\":{\"base\":{\"block\":1}},\"elements\":["
                        + "{\"type\":\"block\",\"pos\":[0,0,2],\"state\":\"base\"},"
                        + "{\"type\":\"block\",\"pos\":[2,0,0],\"state\":\"base\"}]}")
                        .getBytes(StandardCharsets.UTF_8));
        StructureTerrainMask squareMask = new StructureTerrainMask(squareTemplate.resolve(19L), squareTemplate);
        TerrainFootprintPolicy roundedBounds = new TerrainFootprintPolicy(TerrainFootprintPolicy.Source.BASE_BOUNDS,
                TerrainFootprintPolicy.Shape.ROUNDED_BOUNDS, null, 0, 0, 1, false, Collections.<String>emptySet(),
                Collections.<String>emptySet(), Collections.<String>emptySet(), Collections.<String>emptySet(), "any",
                null, null, null);
        TerrainFootprint rounded = TerrainFootprintCompiler.compile(roundedBounds, squareMask,
                new StructureTransform(StructureTransform.Rotation.NONE, StructureTransform.Mirror.NONE),
                new StructureFeature.Bounds(new BlockPosition(0, 0, 0), new BlockPosition(2, 0, 2)),
                Collections.<String, TerrainFootprintPolicy>emptyMap());
        require(rounded.size() < 9 && rounded.contains(0, 2) && rounded.contains(2, 0) && !rounded.contains(0, 0),
                "Rounded base bounds chamfer unused corners without removing required support cells");

        TerrainFootprintPolicy selected = policy(TerrainFootprintPolicy.Source.SUPPORT,
                TerrainFootprintPolicy.Shape.MASK, Collections.singleton("road_surface"));
        for (StructureTransform.Rotation rotation : StructureTransform.Rotation.values()) {
            for (StructureTransform.Mirror mirror : StructureTransform.Mirror.values()) {
                StructureTransform transform = new StructureTransform(rotation, mirror);
                TerrainFootprint transformed = TerrainFootprintCompiler.compile(selected, mask, transform, bounds,
                        Collections.<String, TerrainFootprintPolicy>emptyMap());
                require(transformed.size() == 2,
                        "Asymmetric selected footprints retain both cells under " + rotation + "/" + mirror);
            }
        }
        System.out.println("Terrain footprint checks passed.");
    }

    private static TerrainFootprintPolicy policy(TerrainFootprintPolicy.Source source,
            TerrainFootprintPolicy.Shape shape, java.util.Set<String> tags) {
        return new TerrainFootprintPolicy(source, shape, null, 0, 0, 0, true, Collections.<String>emptySet(),
                Collections.<String>emptySet(), new LinkedHashSet<String>(tags), Collections.<String>emptySet(), "all",
                null, null, null);
    }

    private static TerrainFootprint footprint(int[][] coordinates) {
        java.util.List<TerrainFootprint.Cell> cells = new java.util.ArrayList<TerrainFootprint.Cell>();
        for (int[] coordinate : coordinates) {
            cells.add(new TerrainFootprint.Cell(coordinate[0], coordinate[1]));
        }
        return new TerrainFootprint(cells);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
