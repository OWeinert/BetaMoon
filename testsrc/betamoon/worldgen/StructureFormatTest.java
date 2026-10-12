package betamoon.worldgen;

import betamoon.worldgen.structure.StructureTemplate;
import betamoon.worldgen.structure.StructureTransform;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.src.Block;

/** Verifies the replacement JSON structure format, compiler, and deterministic variation pipeline. */
public final class StructureFormatTest {
    private StructureFormatTest() {
    }

    public static void main(String[] arguments) throws Exception {
        require(Block.stone != null, "Vanilla blocks are initialized");
        rejectsLegacyFormat();
        compilesGeometryAndTemplates();
        resolvesConditionalGeometryDeterministically();
        appliesProcessorsInOrder();
        appliesTagExclusionsIndependently();
        protectsDepositTargets();
        protectsSemanticStructureCells();
        rejectsDuplicateTileDataMarkers();
        appliesDirectionalGeometryMetadata();
        erodesFromSelectedFaces();
        parsesBundledStructureExamples();
        System.out.println("Replacement structure format checks passed.");
    }

    private static void rejectsLegacyFormat() throws Exception {
        expectFailure("{\"format\":\"betamoon_structure\",\"size\":[1,1,1],"
                + "\"origin\":[0,0,0],\"palette\":[],\"blocks\":[]}", "unsupported field");
    }

    private static void compilesGeometryAndTemplates() throws Exception {
        StructureTemplate template = read("{"
                + "\"format\":\"betamoon_structure\","
                + "\"bounds\":{\"min\":[-6,-1,-6],\"max\":[6,8,6]},"
                + "\"palette\":{"
                + "\"stone\":{\"block\":\"minecraft:stone\",\"tags\":[\"structural\"]},"
                + "\"moss\":{\"block\":48}},"
                + "\"templates\":{\"pillar\":{\"elements\":["
                + "{\"type\":\"fill\",\"from\":[0,0,0],\"to\":[0,2,0],\"state\":\"stone\"}]}},"
                + "\"elements\":["
                + "{\"type\":\"fill\",\"from\":[-2,0,-2],\"to\":[2,0,2],\"state\":\"stone\"},"
                + "{\"type\":\"shell\",\"from\":[-2,1,-2],\"to\":[2,3,2],"
                + "\"faces\":[\"north\",\"south\"],\"state\":\"stone\"},"
                + "{\"type\":\"frame\",\"from\":[-2,1,-2],\"to\":[2,3,2],\"state\":\"stone\"},"
                + "{\"type\":\"line\",\"from\":[-2,4,-2],\"to\":[2,5,2],\"state\":\"stone\"},"
                + "{\"type\":\"staircase\",\"from\":[-3,0,4],\"to\":[3,3,4],"
                + "\"width\":2,\"state\":\"stone\"},"
                + "{\"type\":\"cylinder\",\"base\":[0,1,0],\"radius\":1,\"height\":3,"
                + "\"mode\":\"shell\",\"caps\":[\"top\"],\"state\":\"stone\",\"write\":\"replace\"},"
                + "{\"type\":\"ellipsoid\",\"center\":[0,6,0],\"radius\":[2,1,2],"
                + "\"mode\":\"shell\",\"state\":\"stone\"},"
                + "{\"type\":\"voxel_map\",\"at\":[-1,1,5],\"legend\":{\"#\":\"stone\",\".\":null},"
                + "\"layers\":[[\"#.#\"]]},"
                + "{\"type\":\"array\",\"template\":\"pillar\",\"at\":[-5,0,-5],"
                + "\"count\":[2,1,1],\"step\":[10,0,0]},"
                + "{\"type\":\"repeat\",\"count\":2,\"step\":[0,0,2],\"element\":{"
                + "\"type\":\"block\",\"pos\":[0,7,-1],\"state\":\"stone\"}},"
                + "{\"type\":\"replace_state\",\"from\":[-2,0,-2],\"to\":[2,0,2],"
                + "\"replace\":[\"stone\"],\"with\":\"moss\"},"
                + "{\"type\":\"clear\",\"from\":[0,0,0],\"to\":[0,0,0]},"
                + "{\"type\":\"marker\",\"pos\":[0,0,0],\"name\":\"terrain_support\"}],"
                + "\"processors\":[]}");
        StructureTemplate.Resolved resolved = template.resolve(9L);
        require(template.minimum.equals(new BlockPosition(-6, -1, -6))
                && template.maximum.equals(new BlockPosition(6, 8, 6)), "Explicit signed bounds are retained");
        require(!contains(resolved, 0, 0, 0), "Clear removes an earlier compiled cell");
        require(state(resolved, template, -2, 0, -2).equals("moss"),
                "State replacement edits previously authored geometry");
        require(contains(resolved, -5, 2, -5) && contains(resolved, 5, 2, -5),
                "Template arrays expand reusable geometry");
        require(contains(resolved, 0, 7, -1) && contains(resolved, 0, 7, 1),
                "Repeat expands arbitrary nested elements");
        require(resolved.markers.size() == 1, "Markers compile as ordinary elements");
    }

    private static void resolvesConditionalGeometryDeterministically() throws Exception {
        StructureTemplate template = read("{"
                + "\"format\":\"betamoon_structure\","
                + "\"palette\":{\"stone\":{\"block\":\"minecraft:stone\"}},"
                + "\"elements\":["
                + "{\"type\":\"chance\",\"key\":\"tower\",\"chance\":0.5,\"elements\":["
                + "{\"type\":\"block\",\"pos\":[0,1,0],\"state\":\"stone\"}]},"
                + "{\"type\":\"choice\",\"key\":\"roof\",\"choices\":["
                + "{\"weight\":1,\"elements\":[]},{\"weight\":1,\"elements\":["
                + "{\"type\":\"block\",\"pos\":[0,2,0],\"state\":\"stone\"}]}]}],"
                + "\"processors\":[]}");
        for (long seed = 0; seed < 128; seed++) {
            require(positions(template.resolve(seed)).equals(positions(template.resolve(seed))),
                    "Conditional results repeat for the same placement seed");
        }
        Set<Set<BlockPosition>> variants = new HashSet<Set<BlockPosition>>();
        for (long seed = 0; seed < 128; seed++) {
            variants.add(positions(template.resolve(seed)));
        }
        require(variants.size() > 1, "World-derived placement seeds produce deterministic variation");
    }

    private static void appliesProcessorsInOrder() throws Exception {
        StructureTemplate template = read("{"
                + "\"format\":\"betamoon_structure\","
                + "\"bounds\":{\"min\":[0,0,0],\"max\":[2,1,0]},"
                + "\"palette\":{"
                + "\"stone\":{\"block\":\"minecraft:stone\",\"tags\":[\"weatherable\"]},"
                + "\"moss\":{\"block\":48},"
                + "\"brick\":{\"block\":45},"
                + "\"growth\":{\"block\":18}},"
                + "\"elements\":[{\"type\":\"fill\",\"from\":[0,0,0],\"to\":[2,0,0],"
                + "\"state\":\"stone\"}],"
                + "\"processors\":["
                + "{\"type\":\"recolor\",\"key\":\"base\",\"select\":{\"bounds\":{"
                + "\"min\":[0,0,0],\"max\":[0,0,0]}},\"with\":\"moss\"},"
                + "{\"type\":\"replace\",\"key\":\"middle\",\"select\":{\"bounds\":{"
                + "\"min\":[1,0,0],\"max\":[1,0,0]}},\"chance\":1,\"with\":\"brick\"},"
                + "{\"type\":\"decay\",\"key\":\"end\",\"select\":{\"bounds\":{"
                + "\"min\":[2,0,0],\"max\":[2,0,0]}},\"chance\":1},"
                + "{\"type\":\"deposit\",\"key\":\"growth\",\"select\":{\"states\":[\"brick\"],"
                + "\"faces\":[\"up\"]},\"chance\":1,\"with\":\"growth\"}]}");
        StructureTemplate.Resolved first = template.resolve(112233L);
        StructureTemplate.Resolved second = template.resolve(112233L);
        require(positions(first).equals(positions(second)), "Processor results repeat for the same placement seed");
        require(state(first, template, 0, 0, 0).equals("moss"), "Recolor changes the selected symbolic state");
        require(state(first, template, 1, 0, 0).equals("brick"), "Replace applies its deterministic target");
        require(!contains(first, 2, 0, 0), "Decay omits a selected structure cell without placing air");
        require(state(first, template, 1, 1, 0).equals("growth"), "Deposit adds a state on a selected exposed face");
    }

    private static void appliesTagExclusionsIndependently() throws Exception {
        StructureTemplate template = read("{\"format\":\"betamoon_structure\","
                + "\"palette\":{\"stone\":{\"block\":1},\"moss\":{\"block\":48}},"
                + "\"elements\":["
                + "{\"type\":\"block\",\"pos\":[0,0,0],\"state\":\"stone\","
                + "\"tags\":[\"protected_detail\"]},"
                + "{\"type\":\"block\",\"pos\":[1,0,0],\"state\":\"stone\"}],"
                + "\"processors\":[{\"type\":\"recolor\",\"key\":\"unprotected_only\","
                + "\"select\":{\"excludeTags\":[\"protected_detail\"]},\"with\":\"moss\"}]}");
        StructureTemplate.Resolved resolved = template.resolve(12L);
        require(state(resolved, template, 0, 0, 0).equals("stone"),
                "excludeTags alone rejects matching element tags");
        require(state(resolved, template, 1, 0, 0).equals("moss"),
                "excludeTags alone permits other cells");
    }

    private static void protectsDepositTargets() throws Exception {
        String prefix = "{\"format\":\"betamoon_structure\","
                + "\"bounds\":{\"min\":[0,0,0],\"max\":[4,0,0]},"
                + "\"palette\":{\"source\":{\"block\":1,\"tags\":[\"protected\"]},"
                + "\"target\":{\"block\":4,\"tags\":[\"protected\"]},"
                + "\"growth\":{\"block\":48}},\"elements\":["
                + "{\"type\":\"block\",\"pos\":[0,0,0],\"state\":\"source\"},"
                + "{\"type\":\"block\",\"pos\":[2,0,0],\"state\":\"target\"}],"
                + "\"processors\":[{\"type\":\"deposit\",\"key\":\"growth\","
                + "\"select\":{\"states\":[\"source\"],\"faces\":[\"east\"]},"
                + "\"chance\":1,\"distance\":2,\"onConflict\":\"replace\","
                + "\"with\":\"growth\"";
        StructureTemplate protectedTemplate = read(prefix + "}]}");
        StructureTemplate.Resolved protectedResult = protectedTemplate.resolve(22L);
        require(state(protectedResult, protectedTemplate, 2, 0, 0).equals("target"),
                "Deposit may read a protected source but does not replace a protected target by default");

        StructureTemplate allowedTemplate = read(prefix + ",\"allowProtected\":true}]}");
        StructureTemplate.Resolved allowedResult = allowedTemplate.resolve(22L);
        require(state(allowedResult, allowedTemplate, 2, 0, 0).equals("growth"),
                "allowProtected explicitly permits deposit replacement of a protected target");
    }

    private static void protectsSemanticStructureCells() throws Exception {
        String prefix = "{\"format\":\"betamoon_structure\","
                + "\"palette\":{\"container\":{\"variants\":[{\"block\":1},{\"block\":54}]},"
                + "\"typed_data\":{\"block\":1,\"data\":{\"owner\":\"example\"}},"
                + "\"stone\":{\"block\":1}},"
                + "\"elements\":["
                + "{\"type\":\"block\",\"pos\":[0,0,0],\"state\":\"container\"},"
                + "{\"type\":\"block\",\"pos\":[1,0,0],\"state\":\"typed_data\"},"
                + "{\"type\":\"block\",\"pos\":[2,0,0],\"state\":\"stone\"},"
                + "{\"type\":\"marker\",\"pos\":[2,0,0],\"name\":\"tile_data\","
                + "\"value\":{\"stored\":1}},"
                + "{\"type\":\"block\",\"pos\":[3,0,0],\"state\":\"stone\"},"
                + "{\"type\":\"loot\",\"pos\":[3,0,0],\"items\":[{\"slot\":0,"
                + "\"stack\":{\"item\":1}}]},"
                + "{\"type\":\"block\",\"pos\":[4,0,0],\"state\":\"stone\"},"
                + "{\"type\":\"marker\",\"pos\":[4,0,0],\"name\":\"connector\","
                + "\"value\":{\"pool\":\"path\",\"facing\":\"east\"}},"
                + "{\"type\":\"block\",\"pos\":[5,0,0],\"state\":\"stone\"},"
                + "{\"type\":\"marker\",\"pos\":[5,0,0],\"name\":\"terrain_support\"},"
                + "{\"type\":\"block\",\"pos\":[6,0,0],\"state\":\"stone\"}],"
                + "\"processors\":[{\"type\":\"decay\",\"key\":\"all\",\"chance\":1";
        StructureTemplate protectedTemplate = read(prefix + "}]} ");
        StructureTemplate.Resolved protectedResult = protectedTemplate.resolve(33L);
        require(contains(protectedResult, 0, 0, 0),
                "A tile-entity variant protects its complete symbolic palette state automatically");
        require(contains(protectedResult, 1, 0, 0), "Typed palette data is protected automatically");
        require(contains(protectedResult, 2, 0, 0), "Tile-data marker targets are protected automatically");
        require(contains(protectedResult, 3, 0, 0), "Loot targets are protected automatically");
        require(contains(protectedResult, 4, 0, 0), "Connector cells are protected automatically");
        require(contains(protectedResult, 5, 0, 0), "Terrain-support cells are protected automatically");
        require(!contains(protectedResult, 6, 0, 0), "Ordinary untagged cells remain processor-eligible");

        StructureTemplate allowedTemplate = read(prefix + ",\"allowProtected\":true}]} ");
        StructureTemplate.Resolved allowedResult = allowedTemplate.resolve(33L);
        require(allowedResult.blocks.isEmpty(), "allowProtected explicitly enables semantic-cell decay");
    }

    private static void rejectsDuplicateTileDataMarkers() throws Exception {
        StructureTemplate template = read("{\"format\":\"betamoon_structure\","
                + "\"palette\":{\"stone\":{\"block\":1}},\"elements\":["
                + "{\"type\":\"block\",\"pos\":[0,0,0],\"state\":\"stone\"},"
                + "{\"type\":\"marker\",\"pos\":[0,0,0],\"name\":\"tile_data\","
                + "\"value\":{\"first\":1}},"
                + "{\"type\":\"marker\",\"pos\":[0,0,0],\"name\":\"data\","
                + "\"value\":{\"second\":2}}],\"processors\":[]}");
        try {
            template.resolve(1L);
            throw new AssertionError("Duplicate tile-data markers must be rejected");
        } catch (IllegalStateException error) {
            require(error.getMessage().contains("duplicate tile-data marker"),
                    "Duplicate tile-data markers report a focused failure");
        }
    }

    private static void erodesFromSelectedFaces() throws Exception {
        StructureTemplate template = read("{\"format\":\"betamoon_structure\","
                + "\"palette\":{\"stone\":{\"block\":\"minecraft:stone\"}},"
                + "\"elements\":[{\"type\":\"fill\",\"from\":[0,0,0],\"to\":[2,0,0],"
                + "\"state\":\"stone\"}],\"processors\":[{\"type\":\"erode\",\"key\":\"west\","
                + "\"select\":{\"faces\":[\"west\"]},\"iterations\":2,\"chance\":1}]}");
        StructureTemplate.Resolved resolved = template.resolve(44L);
        require(!contains(resolved, 0, 0, 0) && !contains(resolved, 1, 0, 0) && contains(resolved, 2, 0, 0),
                "Erosion recalculates exposure between atomic iterations");
    }

    private static void appliesDirectionalGeometryMetadata() throws Exception {
        StructureTemplate template = read("{\"format\":\"betamoon_structure\","
                + "\"bounds\":{\"min\":[-1,0,0],\"max\":[1,1,0]},"
                + "\"palette\":{\"stone\":{\"block\":\"minecraft:stone\"}},"
                + "\"elements\":[{\"type\":\"staircase\",\"from\":[0,0,0],\"to\":[1,1,0],"
                + "\"facing\":\"east\",\"state\":\"stone\"}],"
                + "\"processors\":[{\"type\":\"deposit\",\"key\":\"west_face\","
                + "\"select\":{\"bounds\":{\"min\":[0,0,0],\"max\":[0,0,0]},"
                + "\"faces\":[\"west\"]},\"chance\":1,\"with\":\"stone\"}]}");
        StructureTemplate.Resolved resolved = template.resolve(55L);
        require(block(resolved, 0, 0, 0).localTransform.rotation
                        == StructureTransform.Rotation.CLOCKWISE_90,
                "Staircase facing rotates directional state metadata from north to east");
        require(block(resolved, -1, 0, 0).localTransform.rotation
                        == StructureTransform.Rotation.COUNTERCLOCKWISE_90,
                "Horizontal deposit targets receive their depositing face as authored facing");
    }

    private static void parsesBundledStructureExamples() throws Exception {
        Path examples = Paths.get("examples");
        int parsed = 0;
        try (Stream<Path> paths = Files.walk(examples)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                String normalized = path.toString().replace('\\', '/');
                if (normalized.contains("/worldgen/structures/") && normalized.endsWith(".json")) {
                    StructureTemplate.read(Files.readAllBytes(path));
                    parsed++;
                }
            }
        }
        require(parsed >= 5, "Bundled JSON structure examples use the replacement format");
    }

    private static StructureTemplate read(String json) throws Exception {
        return StructureTemplate.read(json.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean contains(StructureTemplate.Resolved resolved, int x, int y, int z) {
        for (StructureTemplate.TemplateBlock block : resolved.blocks) {
            if (block.position.equals(new BlockPosition(x, y, z))) {
                return true;
            }
        }
        return false;
    }

    private static StructureTemplate.TemplateBlock block(StructureTemplate.Resolved resolved, int x, int y, int z) {
        for (StructureTemplate.TemplateBlock block : resolved.blocks) {
            if (block.position.equals(new BlockPosition(x, y, z))) {
                return block;
            }
        }
        throw new AssertionError("Missing block at [" + x + ", " + y + ", " + z + "]");
    }

    private static String state(StructureTemplate.Resolved resolved, StructureTemplate template,
            int x, int y, int z) {
        for (StructureTemplate.TemplateBlock block : resolved.blocks) {
            if (block.position.equals(new BlockPosition(x, y, z))) {
                return template.palette.get(block.state).name;
            }
        }
        throw new AssertionError("Missing block at [" + x + ", " + y + ", " + z + "]");
    }

    private static Set<BlockPosition> positions(StructureTemplate.Resolved resolved) {
        Set<BlockPosition> result = new HashSet<BlockPosition>();
        for (StructureTemplate.TemplateBlock block : resolved.blocks) {
            result.add(block.position);
        }
        return result;
    }

    private static void expectFailure(String json, String expected) throws Exception {
        try {
            read(json);
            throw new AssertionError("Expected structure parsing to fail");
        } catch (IOException error) {
            require(error.getMessage().contains(expected), "Expected focused failure containing " + expected);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
