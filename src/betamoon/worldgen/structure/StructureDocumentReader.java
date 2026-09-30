package betamoon.worldgen.structure;

import betamoon.assets.model.ModelJson;
import betamoon.worldgen.BlockPosition;
import betamoon.worldgen.WorldGenLimits;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.Block;

/**
 * Reads a strict JSON-compatible data tree and compiles it into one immutable
 * structure template.
 */
final class StructureDocumentReader {
    private static final int MAX_PALETTE = 4096;
    private static final int MAX_VARIANTS = 64;
    private static final int MAX_TEMPLATES = 256;
    private static final int MAX_MARKERS = 1024;
    private static final int MAX_LOOT_ELEMENTS = 256;
    private static final int MAX_DEPTH = 16;
    private static final int MAX_EMISSIONS = WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE * 8;
    private static final int MAX_REPEAT = 256;
    private static final StructureTransform IDENTITY_TRANSFORM = new StructureTransform(
            StructureTransform.Rotation.NONE, StructureTransform.Mirror.NONE);

    private StructureDocumentReader() {
    }

    static StructureTemplate read(byte[] bytes) throws IOException {
        return read(ModelJson.read(bytes));
    }

    static StructureTemplate read(Map<String, Object> root) throws IOException {
        ModelJson.fields(root, "structure", "format", "bounds", "palette", "templates", "elements", "processors");
        if (!"betamoon_structure".equals(ModelJson.name(root.get("format"), "structure.format"))) {
            throw new IOException("structure.format: expected betamoon_structure");
        }

        Palette palette = palette(root.get("palette"));
        Map<String, TemplateDefinition> templates = templates(root.get("templates"));
        List<Object> elements = ModelJson.array(root.get("elements"), "structure.elements");
        Compiler compiler = new Compiler(palette, templates, canonical(root));
        compiler.elements(elements, Affine.IDENTITY, Collections.<String, String>emptyMap(), "structure.elements", 0,
                "root");
        compiler.processors(root.get("processors"));
        Bounds declared = root.get("bounds") == null ? null : bounds(root.get("bounds"), "structure.bounds");
        return compiler.finish(declared);
    }

    private static Palette palette(Object input) throws IOException {
        Map<String, Object> values = ModelJson.object(input, "structure.palette");
        if (values.isEmpty() || values.size() > MAX_PALETTE) {
            throw new IOException("structure.palette: expected 1.." + MAX_PALETTE + " named states");
        }
        List<StructureTemplate.PaletteEntry> entries = new ArrayList<StructureTemplate.PaletteEntry>();
        Map<String, Integer> names = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Object> named : values.entrySet()) {
            String name = identifier(named.getKey(), "structure.palette name");
            String path = "structure.palette." + name;
            Map<String, Object> value = ModelJson.object(named.getValue(), path);
            ModelJson.fields(value, path, "block", "meta", "data", "variants", "tags");
            List<StructureTemplate.State> states = new ArrayList<StructureTemplate.State>();
            int totalWeight = 0;
            if (value.get("variants") != null) {
                if (value.get("block") != null || value.get("meta") != null || value.get("data") != null) {
                    throw new IOException(path + ": variants cannot be combined with block, meta, or data");
                }
                List<Object> variants = ModelJson.array(value.get("variants"), path + ".variants");
                if (variants.isEmpty() || variants.size() > MAX_VARIANTS) {
                    throw new IOException(path + ".variants: expected 1.." + MAX_VARIANTS + " entries");
                }
                for (int index = 0; index < variants.size(); index++) {
                    String variantPath = path + ".variants[" + index + "]";
                    Map<String, Object> variant = ModelJson.object(variants.get(index), variantPath);
                    ModelJson.fields(variant, variantPath, "block", "meta", "data", "weight");
                    int weight = optionalInteger(variant.get("weight"), variantPath + ".weight", 1, 1000000, 1);
                    try {
                        totalWeight = Math.addExact(totalWeight, weight);
                    } catch (ArithmeticException error) {
                        throw new IOException(path + ".variants: total weight is too large");
                    }
                    states.add(state(variant, variantPath, totalWeight));
                }
            } else {
                totalWeight = 1;
                states.add(state(value, path, totalWeight));
            }
            names.put(name, Integer.valueOf(entries.size()));
            entries.add(new StructureTemplate.PaletteEntry(name, tags(value.get("tags"), path + ".tags"), states,
                    totalWeight));
        }
        return new Palette(entries, names);
    }

    private static StructureTemplate.State state(Map<String, Object> value, String path, int cumulativeWeight)
            throws IOException {
        int block = resolveBlock(value.get("block"), path + ".block");
        int metadata = optionalInteger(value.get("meta"), path + ".meta", 0, 15, 0);
        Map<String, Object> data = value.get("data") == null
                ? Collections.<String, Object>emptyMap()
                : immutableObject(ModelJson.object(value.get("data"), path + ".data"), path + ".data", 0);
        return new StructureTemplate.State(block, metadata, data, cumulativeWeight);
    }

    private static Map<String, TemplateDefinition> templates(Object input) throws IOException {
        if (input == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> values = ModelJson.object(input, "structure.templates");
        if (values.size() > MAX_TEMPLATES) {
            throw new IOException("structure.templates: exceeds " + MAX_TEMPLATES + " definitions");
        }
        Map<String, TemplateDefinition> result = new LinkedHashMap<String, TemplateDefinition>();
        for (Map.Entry<String, Object> named : values.entrySet()) {
            String name = identifier(named.getKey(), "structure.templates name");
            String path = "structure.templates." + name;
            Map<String, Object> value = ModelJson.object(named.getValue(), path);
            ModelJson.fields(value, path, "elements");
            result.put(name, new TemplateDefinition(ModelJson.array(value.get("elements"), path + ".elements"),
                    path + ".elements"));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Bounds bounds(Object input, String path) throws IOException {
        Map<String, Object> value = ModelJson.object(input, path);
        ModelJson.fields(value, path, "min", "max");
        BlockPosition minimum = vector(value.get("min"), path + ".min");
        BlockPosition maximum = vector(value.get("max"), path + ".max");
        if (minimum.x > maximum.x || minimum.y > maximum.y || minimum.z > maximum.z) {
            throw new IOException(path + ": min must not exceed max");
        }
        if (minimum.x > 0 || minimum.y > 0 || minimum.z > 0 || maximum.x < 0 || maximum.y < 0 || maximum.z < 0) {
            throw new IOException(path + ": declared bounds must contain the placement origin [0, 0, 0]");
        }
        checkCoordinate(minimum, path + ".min");
        checkCoordinate(maximum, path + ".max");
        return new Bounds(minimum, maximum);
    }

    private static final class Compiler {
        private final Palette palette;
        private final Map<String, TemplateDefinition> templates;
        private final String sourceSemantics;
        private final Map<BlockPosition, MutableCell> cells = new LinkedHashMap<BlockPosition, MutableCell>();
        private final Map<BlockPosition, MutableCell> maximumCells = new LinkedHashMap<BlockPosition, MutableCell>();
        private final List<StructureTemplate.Marker> markers = new ArrayList<StructureTemplate.Marker>();
        private final List<StructureTemplate.Marker> maximumMarkers = new ArrayList<StructureTemplate.Marker>();
        private final List<StructureLoot> loots = new ArrayList<StructureLoot>();
        private final List<StructureLoot> maximumLoots = new ArrayList<StructureLoot>();
        private final List<String> templateStack = new ArrayList<String>();
        private final Set<String> randomKeys = new LinkedHashSet<String>();
        private final List<StructureProgram.Processor> processors = new ArrayList<StructureProgram.Processor>();
        private List<StructureProgram.Action> currentActions = new ArrayList<StructureProgram.Action>();
        private int emissions;

        private Compiler(Palette palette, Map<String, TemplateDefinition> templates, String sourceSemantics) {
            this.palette = palette;
            this.templates = templates;
            this.sourceSemantics = sourceSemantics;
        }

        private void elements(List<Object> values, Affine transform, Map<String, String> remap, String path, int depth,
                String instancePath) throws IOException {
            if (depth > MAX_DEPTH) {
                throw new IOException(path + ": nested element/template depth exceeds " + MAX_DEPTH);
            }
            for (int index = 0; index < values.size(); index++) {
                element(ModelJson.object(values.get(index), path + "[" + index + "]"), transform, remap,
                        path + "[" + index + "]", depth, instancePath);
            }
        }

        private void element(Map<String, Object> value, Affine transform, Map<String, String> remap, String path,
                int depth, String instancePath) throws IOException {
            String type = ModelJson.name(value.get("type"), path + ".type");
            if (type.equals("block")) {
                fields(value, path, "type", "pos", "state", "tags", "write");
                write(transform.apply(vector(value.get("pos"), path + ".pos")), state(value, remap, path),
                        tags(value.get("tags"), path + ".tags"), replace(value, path), transform.orientation(), path);
            } else if (type.equals("fill")) {
                fields(value, path, "type", "from", "to", "state", "tags", "write");
                cuboid(value, transform, remap, path, CuboidMode.FILL);
            } else if (type.equals("shell")) {
                fields(value, path, "type", "from", "to", "state", "tags", "write", "thickness", "faces");
                cuboid(value, transform, remap, path, CuboidMode.SHELL);
            } else if (type.equals("frame")) {
                fields(value, path, "type", "from", "to", "state", "tags", "write", "thickness");
                cuboid(value, transform, remap, path, CuboidMode.FRAME);
            } else if (type.equals("line")) {
                fields(value, path, "type", "from", "to", "state", "tags", "write");
                line(value, transform, remap, path);
            } else if (type.equals("staircase")) {
                fields(value, path, "type", "from", "to", "width", "state", "facing", "tags", "write");
                staircase(value, transform, remap, path);
            } else if (type.equals("cylinder")) {
                fields(value, path, "type", "base", "radius", "height", "axis", "mode", "thickness", "caps", "state",
                        "tags", "write");
                cylinder(value, transform, remap, path);
            } else if (type.equals("ellipsoid")) {
                fields(value, path, "type", "center", "radius", "mode", "thickness", "state", "tags", "write");
                ellipsoid(value, transform, remap, path);
            } else if (type.equals("voxel_map")) {
                fields(value, path, "type", "at", "legend", "layers", "tags", "write");
                voxelMap(value, transform, remap, path);
            } else if (type.equals("clear")) {
                fields(value, path, "type", "from", "to");
                clear(value, transform, path);
            } else if (type.equals("replace_state")) {
                fields(value, path, "type", "from", "to", "replace", "with");
                replaceState(value, transform, remap, path);
            } else if (type.equals("template")) {
                fields(value, path, "type", "name", "at", "rotation", "mirror", "states");
                template(value, transform, remap, path, depth, instancePath);
            } else if (type.equals("array")) {
                fields(value, path, "type", "template", "at", "count", "step", "rotation", "mirror", "states");
                array(value, transform, remap, path, depth, instancePath);
            } else if (type.equals("repeat")) {
                fields(value, path, "type", "count", "step", "element");
                repeat(value, transform, remap, path, depth, instancePath);
            } else if (type.equals("marker")) {
                fields(value, path, "type", "pos", "name", "value");
                marker(value, transform, path);
            } else if (type.equals("loot")) {
                fields(value, path, "type", "pos", "key", "items", "pools", "table", "slots", "existing", "overflow");
                loot(value, transform, path, instancePath);
            } else if (type.equals("chance")) {
                fields(value, path, "type", "key", "chance", "elements");
                chance(value, transform, remap, path, depth, instancePath);
            } else if (type.equals("choice")) {
                fields(value, path, "type", "key", "choices");
                choice(value, transform, remap, path, depth, instancePath);
            } else {
                throw new IOException(path + ".type: unsupported structure element " + type);
            }
        }

        private int state(Map<String, Object> value, Map<String, String> remap, String path) throws IOException {
            String name = remap(ModelJson.name(value.get("state"), path + ".state"), remap);
            return palette.index(name, path + ".state");
        }

        private void cuboid(Map<String, Object> value, Affine transform, Map<String, String> remap, String path,
                CuboidMode mode) throws IOException {
            Box box = box(value, path);
            int state = state(value, remap, path);
            Set<String> tags = tags(value.get("tags"), path + ".tags");
            boolean replace = replace(value, path);
            int thickness = optionalInteger(value.get("thickness"), path + ".thickness", 1, 32, 1);
            Set<String> faces = mode == CuboidMode.SHELL
                    ? faces(value.get("faces"), path + ".faces")
                    : Collections.<String>emptySet();
            for (int x = box.min.x; x <= box.max.x; x++) {
                for (int y = box.min.y; y <= box.max.y; y++) {
                    for (int z = box.min.z; z <= box.max.z; z++) {
                        boolean emit;
                        if (mode == CuboidMode.FILL) {
                            emit = true;
                        } else if (mode == CuboidMode.SHELL) {
                            emit = shellFace(x, y, z, box, thickness, faces);
                        } else {
                            int boundaries = boundaryAxes(x, y, z, box, thickness);
                            emit = boundaries >= 2;
                        }
                        if (emit) {
                            write(transform.apply(new BlockPosition(x, y, z)), state, tags, replace,
                                    transform.orientation(), path);
                        }
                    }
                }
            }
        }

        private void line(Map<String, Object> value, Affine transform, Map<String, String> remap, String path)
                throws IOException {
            BlockPosition from = vector(value.get("from"), path + ".from");
            BlockPosition to = vector(value.get("to"), path + ".to");
            int state = state(value, remap, path);
            Set<String> tags = tags(value.get("tags"), path + ".tags");
            boolean replace = replace(value, path);
            int steps = Math.max(Math.abs(to.x - from.x), Math.max(Math.abs(to.y - from.y), Math.abs(to.z - from.z)));
            BlockPosition previous = from;
            write(transform.apply(from), state, tags, replace, transform.orientation(), path);
            for (int step = 1; step <= steps; step++) {
                BlockPosition current = new BlockPosition(interpolate(from.x, to.x, step, steps),
                        interpolate(from.y, to.y, step, steps), interpolate(from.z, to.z, step, steps));
                bridge(previous, current, state, tags, replace, transform, path);
                previous = current;
            }
        }

        private void bridge(BlockPosition from, BlockPosition to, int state, Set<String> tags, boolean replace,
                Affine transform, String path) throws IOException {
            int[] xs = from.x == to.x ? new int[]{from.x} : ordered(from.x, to.x);
            int[] ys = from.y == to.y ? new int[]{from.y} : ordered(from.y, to.y);
            int[] zs = from.z == to.z ? new int[]{from.z} : ordered(from.z, to.z);
            for (int x : xs) {
                for (int y : ys) {
                    for (int z : zs) {
                        write(transform.apply(new BlockPosition(x, y, z)), state, tags, replace,
                                transform.orientation(), path);
                    }
                }
            }
        }

        private void staircase(Map<String, Object> value, Affine transform, Map<String, String> remap, String path)
                throws IOException {
            BlockPosition from = vector(value.get("from"), path + ".from");
            BlockPosition to = vector(value.get("to"), path + ".to");
            boolean alongX = from.z == to.z && from.x != to.x;
            boolean alongZ = from.x == to.x && from.z != to.z;
            if (!alongX && !alongZ) {
                throw new IOException(path + ": staircase endpoints must differ along exactly one horizontal axis");
            }
            int width = optionalInteger(value.get("width"), path + ".width", 1, 32, 1);
            int state = state(value, remap, path);
            Set<String> tags = tags(value.get("tags"), path + ".tags");
            boolean replace = replace(value, path);
            StructureTransform orientation = transform.orientation();
            if (value.get("facing") != null) {
                orientation = orientation.compose(
                        facingTransform(ModelJson.name(value.get("facing"), path + ".facing"), path + ".facing"));
            }
            int low = alongX ? Math.min(from.x, to.x) : Math.min(from.z, to.z);
            int high = alongX ? Math.max(from.x, to.x) : Math.max(from.z, to.z);
            int startY = (alongX ? from.x : from.z) == low ? from.y : to.y;
            int endY = (alongX ? from.x : from.z) == high ? from.y : to.y;
            int distance = high - low;
            int widthStart = -width / 2;
            for (int step = 0; step <= distance; step++) {
                int y = interpolate(startY, endY, step, distance);
                for (int offset = widthStart; offset < widthStart + width; offset++) {
                    int x = alongX ? low + step : from.x + offset;
                    int z = alongZ ? low + step : from.z + offset;
                    write(transform.apply(new BlockPosition(x, y, z)), state, tags, replace, orientation, path);
                }
            }
        }

        private static StructureTransform facingTransform(String facing, String path) throws IOException {
            StructureTransform.Rotation rotation;
            if (facing.equals("north")) {
                rotation = StructureTransform.Rotation.NONE;
            } else if (facing.equals("east")) {
                rotation = StructureTransform.Rotation.CLOCKWISE_90;
            } else if (facing.equals("south")) {
                rotation = StructureTransform.Rotation.CLOCKWISE_180;
            } else if (facing.equals("west")) {
                rotation = StructureTransform.Rotation.COUNTERCLOCKWISE_90;
            } else {
                throw new IOException(path + ": expected north, east, south, or west");
            }
            return new StructureTransform(rotation, StructureTransform.Mirror.NONE);
        }

        private void cylinder(Map<String, Object> value, Affine transform, Map<String, String> remap, String path)
                throws IOException {
            BlockPosition base = vector(value.get("base"), path + ".base");
            int[] radius = radii(value.get("radius"), path + ".radius", 2);
            int height = integer(value.get("height"), path + ".height", 1, WorldGenLimits.MAX_HEIGHT + 1);
            String axis = optionalName(value.get("axis"), path + ".axis", "y");
            if (!axis.equals("x") && !axis.equals("y") && !axis.equals("z")) {
                throw new IOException(path + ".axis: expected x, y, or z");
            }
            String mode = optionalName(value.get("mode"), path + ".mode", "solid");
            if (!mode.equals("solid") && !mode.equals("shell")) {
                throw new IOException(path + ".mode: expected solid or shell");
            }
            int thickness = optionalInteger(value.get("thickness"), path + ".thickness", 1, 32, 1);
            Set<String> caps = caps(value.get("caps"), path + ".caps");
            int state = state(value, remap, path);
            Set<String> tags = tags(value.get("tags"), path + ".tags");
            boolean replace = replace(value, path);
            for (int heightOffset = 0; heightOffset < height; heightOffset++) {
                for (int first = -radius[0]; first <= radius[0]; first++) {
                    for (int second = -radius[1]; second <= radius[1]; second++) {
                        if (!VoxelShapeRasterizer.ellipse(first, second, radius[0], radius[1])) {
                            continue;
                        }
                        boolean cap = heightOffset < thickness && caps.contains("bottom")
                                || heightOffset >= height - thickness && caps.contains("top");
                        int innerFirst = radius[0] - thickness;
                        int innerSecond = radius[1] - thickness;
                        boolean wall = innerFirst <= 0 || innerSecond <= 0
                                || !VoxelShapeRasterizer.ellipse(first, second, innerFirst, innerSecond);
                        if (mode.equals("shell") && !cap && !wall) {
                            continue;
                        }
                        BlockPosition position;
                        if (axis.equals("x")) {
                            position = base.offset(heightOffset, first, second);
                        } else if (axis.equals("z")) {
                            position = base.offset(first, second, heightOffset);
                        } else {
                            position = base.offset(first, heightOffset, second);
                        }
                        write(transform.apply(position), state, tags, replace, transform.orientation(), path);
                    }
                }
            }
        }

        private void ellipsoid(Map<String, Object> value, Affine transform, Map<String, String> remap, String path)
                throws IOException {
            BlockPosition center = vector(value.get("center"), path + ".center");
            int[] radius = radii(value.get("radius"), path + ".radius", 3);
            String mode = optionalName(value.get("mode"), path + ".mode", "solid");
            if (!mode.equals("solid") && !mode.equals("shell")) {
                throw new IOException(path + ".mode: expected solid or shell");
            }
            int thickness = optionalInteger(value.get("thickness"), path + ".thickness", 1, 32, 1);
            int state = state(value, remap, path);
            Set<String> tags = tags(value.get("tags"), path + ".tags");
            boolean replace = replace(value, path);
            for (int x = -radius[0]; x <= radius[0]; x++) {
                for (int y = -radius[1]; y <= radius[1]; y++) {
                    for (int z = -radius[2]; z <= radius[2]; z++) {
                        if (!VoxelShapeRasterizer.ellipsoid(x, y, z, radius[0], radius[1], radius[2])) {
                            continue;
                        }
                        int[] inner = new int[]{radius[0] - thickness, radius[1] - thickness, radius[2] - thickness};
                        boolean wall = inner[0] <= 0 || inner[1] <= 0 || inner[2] <= 0
                                || !VoxelShapeRasterizer.ellipsoid(x, y, z, inner[0], inner[1], inner[2]);
                        if (mode.equals("shell") && !wall) {
                            continue;
                        }
                        write(transform.apply(center.offset(x, y, z)), state, tags, replace, transform.orientation(),
                                path);
                    }
                }
            }
        }

        private void voxelMap(Map<String, Object> value, Affine transform, Map<String, String> remap, String path)
                throws IOException {
            BlockPosition at = vector(value.get("at"), path + ".at");
            Map<String, Object> legendValue = ModelJson.object(value.get("legend"), path + ".legend");
            Map<Character, String> legend = new LinkedHashMap<Character, String>();
            for (Map.Entry<String, Object> entry : legendValue.entrySet()) {
                if (entry.getKey().length() != 1 || entry.getKey().charAt(0) > 127) {
                    throw new IOException(path + ".legend: keys must be one ASCII character");
                }
                legend.put(Character.valueOf(entry.getKey().charAt(0)),
                        entry.getValue() == null
                                ? null
                                : remap(ModelJson.name(entry.getValue(), path + ".legend." + entry.getKey()), remap));
            }
            List<Object> layers = ModelJson.array(value.get("layers"), path + ".layers");
            if (layers.isEmpty()) {
                throw new IOException(path + ".layers: expected at least one layer");
            }
            Set<String> elementTags = tags(value.get("tags"), path + ".tags");
            boolean replace = replace(value, path);
            int expectedRows = -1;
            int expectedWidth = -1;
            for (int y = 0; y < layers.size(); y++) {
                List<Object> rows = ModelJson.array(layers.get(y), path + ".layers[" + y + "]");
                if (expectedRows < 0) {
                    expectedRows = rows.size();
                } else if (rows.size() != expectedRows) {
                    throw new IOException(path + ".layers[" + y + "]: inconsistent row count");
                }
                for (int z = 0; z < rows.size(); z++) {
                    String row = ModelJson.name(rows.get(z), path + ".layers[" + y + "][" + z + "]");
                    if (expectedWidth < 0) {
                        expectedWidth = row.length();
                    } else if (row.length() != expectedWidth) {
                        throw new IOException(path + ".layers[" + y + "][" + z + "]: inconsistent row width");
                    }
                    for (int x = 0; x < row.length(); x++) {
                        Character symbol = Character.valueOf(row.charAt(x));
                        if (!legend.containsKey(symbol)) {
                            throw new IOException(path + ".layers[" + y + "][" + z + "]: symbol '" + symbol
                                    + "' is absent from legend");
                        }
                        String stateName = legend.get(symbol);
                        if (stateName != null) {
                            write(transform.apply(at.offset(x, y, z)),
                                    palette.index(stateName, path + ".legend." + symbol), elementTags, replace,
                                    transform.orientation(), path);
                        }
                    }
                }
            }
        }

        private void clear(Map<String, Object> value, Affine transform, String path) throws IOException {
            Box box = box(value, path);
            List<BlockPosition> positions = new ArrayList<BlockPosition>();
            for (int x = box.min.x; x <= box.max.x; x++) {
                for (int y = box.min.y; y <= box.max.y; y++) {
                    for (int z = box.min.z; z <= box.max.z; z++) {
                        BlockPosition position = transform.apply(new BlockPosition(x, y, z));
                        positions.add(position);
                        cells.remove(position);
                    }
                }
            }
            currentActions.add(new StructureProgram.ClearAction(positions));
        }

        private void replaceState(Map<String, Object> value, Affine transform, Map<String, String> remap, String path)
                throws IOException {
            Box box = box(value, path);
            Set<Integer> replace = new LinkedHashSet<Integer>();
            List<Object> names = ModelJson.array(value.get("replace"), path + ".replace");
            if (names.isEmpty()) {
                throw new IOException(path + ".replace: expected at least one state");
            }
            for (int index = 0; index < names.size(); index++) {
                String name = remap(ModelJson.name(names.get(index), path + ".replace[" + index + "]"), remap);
                replace.add(Integer.valueOf(palette.index(name, path + ".replace[" + index + "]")));
            }
            int with = palette.index(remap(ModelJson.name(value.get("with"), path + ".with"), remap), path + ".with");
            List<BlockPosition> positions = new ArrayList<BlockPosition>();
            for (int x = box.min.x; x <= box.max.x; x++) {
                for (int y = box.min.y; y <= box.max.y; y++) {
                    for (int z = box.min.z; z <= box.max.z; z++) {
                        BlockPosition position = transform.apply(new BlockPosition(x, y, z));
                        positions.add(position);
                        MutableCell cell = cells.get(position);
                        if (cell != null && replace.contains(Integer.valueOf(cell.state))) {
                            cell.state = with;
                        }
                    }
                }
            }
            currentActions.add(new StructureProgram.ReplaceStateAction(positions, replace, with));
        }

        private void template(Map<String, Object> value, Affine parent, Map<String, String> parentRemap, String path,
                int depth, String instancePath) throws IOException {
            String name = ModelJson.name(value.get("name"), path + ".name");
            TemplateDefinition template = templates.get(name);
            if (template == null) {
                throw new IOException(path + ".name: unknown local template " + name);
            }
            if (templateStack.contains(name)) {
                throw new IOException(path + ": recursive template reference " + templateStack + " -> " + name);
            }
            BlockPosition at = value.get("at") == null
                    ? new BlockPosition(0, 0, 0)
                    : vector(value.get("at"), path + ".at");
            Affine local = Affine.of(transform(value, path), at);
            Map<String, String> remap = composeRemap(parentRemap, value.get("states"), path + ".states");
            StructureTransform orientation = local.orientation();
            templateStack.add(name);
            try {
                elements(template.elements, parent.compose(local), remap, template.path, depth + 1,
                        instancePath + "/template:" + name + "@" + at.x + "," + at.y + "," + at.z + ":"
                                + orientation.rotation + ":" + orientation.mirror);
            } finally {
                templateStack.remove(templateStack.size() - 1);
            }
        }

        private void array(Map<String, Object> value, Affine transform, Map<String, String> remap, String path,
                int depth, String instancePath) throws IOException {
            BlockPosition at = value.get("at") == null
                    ? new BlockPosition(0, 0, 0)
                    : vector(value.get("at"), path + ".at");
            int[] count = positiveVector(value.get("count"), path + ".count", MAX_REPEAT);
            BlockPosition step = vector(value.get("step"), path + ".step");
            long instances = (long) count[0] * count[1] * count[2];
            if (instances > MAX_REPEAT) {
                throw new IOException(path + ".count: array exceeds " + MAX_REPEAT + " instances");
            }
            for (int x = 0; x < count[0]; x++) {
                for (int y = 0; y < count[1]; y++) {
                    for (int z = 0; z < count[2]; z++) {
                        Map<String, Object> instance = new LinkedHashMap<String, Object>(value);
                        instance.put("name", value.get("template"));
                        instance.put("at", Arrays.<Object>asList(Double.valueOf(at.x + x * step.x),
                                Double.valueOf(at.y + y * step.y), Double.valueOf(at.z + z * step.z)));
                        instance.remove("template");
                        instance.remove("count");
                        instance.remove("step");
                        template(instance, transform, remap, path, depth,
                                instancePath + "/array:" + x + "," + y + "," + z);
                    }
                }
            }
        }

        private void repeat(Map<String, Object> value, Affine transform, Map<String, String> remap, String path,
                int depth, String instancePath) throws IOException {
            int count = integer(value.get("count"), path + ".count", 1, MAX_REPEAT);
            BlockPosition step = vector(value.get("step"), path + ".step");
            Map<String, Object> repeated = ModelJson.object(value.get("element"), path + ".element");
            for (int index = 0; index < count; index++) {
                Affine translated = transform
                        .compose(Affine.translation(index * step.x, index * step.y, index * step.z));
                element(repeated, translated, remap, path + ".element", depth + 1, instancePath + "/repeat:" + index);
            }
        }

        private void chance(Map<String, Object> value, Affine transform, Map<String, String> remap, String path,
                int depth, String instancePath) throws IOException {
            String key = randomKey(value.get("key"), path + ".key", instancePath);
            double probability = number(value.get("chance"), path + ".chance", 0.0D, 1.0D);
            List<StructureProgram.Action> actions = conditionalActions(
                    ModelJson.array(value.get("elements"), path + ".elements"), transform, remap, path + ".elements",
                    depth + 1, instancePath + "/chance:" + key);
            currentActions.add(new StructureProgram.ChanceAction(key, probability, actions));
        }

        private void choice(Map<String, Object> value, Affine transform, Map<String, String> remap, String path,
                int depth, String instancePath) throws IOException {
            String key = randomKey(value.get("key"), path + ".key", instancePath);
            List<Object> values = ModelJson.array(value.get("choices"), path + ".choices");
            if (values.isEmpty() || values.size() > 64) {
                throw new IOException(path + ".choices: expected 1..64 alternatives");
            }
            List<StructureProgram.Choice> choices = new ArrayList<StructureProgram.Choice>();
            int totalWeight = 0;
            for (int index = 0; index < values.size(); index++) {
                String choicePath = path + ".choices[" + index + "]";
                Map<String, Object> choice = ModelJson.object(values.get(index), choicePath);
                fields(choice, choicePath, "weight", "elements");
                int weight = optionalInteger(choice.get("weight"), choicePath + ".weight", 1, 1000000, 1);
                try {
                    totalWeight = Math.addExact(totalWeight, weight);
                } catch (ArithmeticException error) {
                    throw new IOException(path + ".choices: total weight is too large");
                }
                List<StructureProgram.Action> actions = conditionalActions(
                        ModelJson.array(choice.get("elements"), choicePath + ".elements"), transform, remap,
                        choicePath + ".elements", depth + 1, instancePath + "/choice:" + key + ":" + index);
                choices.add(new StructureProgram.Choice(weight, actions));
            }
            currentActions.add(new StructureProgram.ChoiceAction(key, choices, totalWeight));
        }

        private List<StructureProgram.Action> conditionalActions(List<Object> values, Affine transform,
                Map<String, String> remap, String path, int depth, String instancePath) throws IOException {
            Map<BlockPosition, MutableCell> savedCells = copyCells(cells);
            int markerCount = markers.size();
            int lootCount = loots.size();
            List<StructureProgram.Action> parentActions = currentActions;
            List<StructureProgram.Action> childActions = new ArrayList<StructureProgram.Action>();
            currentActions = childActions;
            try {
                elements(values, transform, remap, path, depth, instancePath);
                return childActions;
            } finally {
                cells.clear();
                cells.putAll(savedCells);
                while (markers.size() > markerCount) {
                    markers.remove(markers.size() - 1);
                }
                while (loots.size() > lootCount) {
                    loots.remove(loots.size() - 1);
                }
                currentActions = parentActions;
            }
        }

        private String randomKey(Object input, String path, String instancePath) throws IOException {
            String authored = identifier(ModelJson.name(input, path), path);
            String effective = instancePath + "/" + authored;
            if (!randomKeys.add(effective)) {
                throw new IOException(path + ": duplicate randomized key " + authored + " in this instance");
            }
            return effective;
        }

        private void processors(Object input) throws IOException {
            if (input == null) {
                return;
            }
            List<Object> values = ModelJson.array(input, "structure.processors");
            if (values.size() > 64) {
                throw new IOException("structure.processors: exceeds 64 processors");
            }
            for (int index = 0; index < values.size(); index++) {
                String path = "structure.processors[" + index + "]";
                processors.add(processor(ModelJson.object(values.get(index), path), path));
            }
        }

        private StructureProgram.Processor processor(Map<String, Object> value, String path) throws IOException {
            String typeName = ModelJson.name(value.get("type"), path + ".type");
            StructureProgram.Processor.Type type;
            try {
                type = StructureProgram.Processor.Type.valueOf(typeName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException error) {
                throw new IOException(path + ".type: expected recolor, replace, decay, erode, or deposit");
            }
            if (type == StructureProgram.Processor.Type.RECOLOR) {
                fields(value, path, "type", "key", "select", "with", "map", "allowProtected");
            } else if (type == StructureProgram.Processor.Type.ERODE) {
                fields(value, path, "type", "key", "select", "chance", "pattern", "iterations", "allowProtected");
            } else if (type == StructureProgram.Processor.Type.DEPOSIT) {
                fields(value, path, "type", "key", "select", "chance", "pattern", "with", "distance", "onConflict",
                        "allowProtected");
            } else if (type == StructureProgram.Processor.Type.REPLACE) {
                fields(value, path, "type", "key", "select", "chance", "pattern", "with", "allowProtected");
            } else {
                fields(value, path, "type", "key", "select", "chance", "pattern", "allowProtected");
            }
            String key = randomKey(value.get("key"), path + ".key", "processor");
            StructureProgram.Selector selector = selector(value.get("select"), path + ".select");
            boolean random = type != StructureProgram.Processor.Type.RECOLOR;
            double chance = random && value.get("chance") != null
                    ? number(value.get("chance"), path + ".chance", 0.0D, 1.0D)
                    : 1.0D;
            StructureProgram.Distribution distribution = distribution(value.get("pattern"), path + ".pattern");
            boolean allowProtected = value.get("allowProtected") != null
                    && ModelJson.bool(value.get("allowProtected"), path + ".allowProtected");
            Integer with = null;
            Map<Integer, Integer> mapping = new LinkedHashMap<Integer, Integer>();
            List<StructureProgram.WeightedState> weighted = new ArrayList<StructureProgram.WeightedState>();
            int totalWeight = 0;
            if (type == StructureProgram.Processor.Type.RECOLOR) {
                if ((value.get("with") == null) == (value.get("map") == null)) {
                    throw new IOException(path + ": recolor requires exactly one of with or map");
                }
                if (value.get("with") != null) {
                    with = Integer
                            .valueOf(palette.index(ModelJson.name(value.get("with"), path + ".with"), path + ".with"));
                } else {
                    Map<String, Object> states = ModelJson.object(value.get("map"), path + ".map");
                    if (states.isEmpty()) {
                        throw new IOException(path + ".map: expected at least one state mapping");
                    }
                    for (Map.Entry<String, Object> entry : states.entrySet()) {
                        int from = palette.index(entry.getKey(), path + ".map." + entry.getKey());
                        int to = palette.index(ModelJson.name(entry.getValue(), path + ".map." + entry.getKey()),
                                path + ".map." + entry.getKey());
                        mapping.put(Integer.valueOf(from), Integer.valueOf(to));
                    }
                }
            } else if (type == StructureProgram.Processor.Type.REPLACE
                    || type == StructureProgram.Processor.Type.DEPOSIT) {
                WeightedResult result = weighted(value.get("with"), path + ".with");
                with = result.single;
                weighted = result.states;
                totalWeight = result.totalWeight;
            }
            int iterations = type == StructureProgram.Processor.Type.ERODE
                    ? optionalInteger(value.get("iterations"), path + ".iterations", 1, 16, 1)
                    : 1;
            int distance = type == StructureProgram.Processor.Type.DEPOSIT
                    ? optionalInteger(value.get("distance"), path + ".distance", 1, 8, 1)
                    : 1;
            String conflict = type == StructureProgram.Processor.Type.DEPOSIT
                    ? optionalName(value.get("onConflict"), path + ".onConflict", "skip")
                    : "skip";
            if (!conflict.equals("skip") && !conflict.equals("error") && !conflict.equals("replace")) {
                throw new IOException(path + ".onConflict: expected skip, error, or replace");
            }
            StructureProgram.Processor result = new StructureProgram.Processor(type, key, selector, chance,
                    distribution, with, mapping, weighted, totalWeight, iterations, distance, conflict, allowProtected);
            if (type == StructureProgram.Processor.Type.DEPOSIT) {
                expandMaximumDeposit(distance, with == null ? weighted.get(0).state : with.intValue(), selector, path);
            }
            return result;
        }

        private void expandMaximumDeposit(int distance, int state, StructureProgram.Selector selector, String path)
                throws IOException {
            Map<BlockPosition, MutableCell> additions = new LinkedHashMap<BlockPosition, MutableCell>();
            for (Map.Entry<BlockPosition, MutableCell> entry : new ArrayList<Map.Entry<BlockPosition, MutableCell>>(
                    maximumCells.entrySet())) {
                for (StructureProgram.Direction direction : selector.depositDirections()) {
                    BlockPosition target;
                    switch (direction) {
                        case DOWN:
                            target = entry.getKey().offset(0, -distance, 0);
                            break;
                        case UP:
                            target = entry.getKey().offset(0, distance, 0);
                            break;
                        case NORTH:
                            target = entry.getKey().offset(0, 0, -distance);
                            break;
                        case SOUTH:
                            target = entry.getKey().offset(0, 0, distance);
                            break;
                        case WEST:
                            target = entry.getKey().offset(-distance, 0, 0);
                            break;
                        default:
                            target = entry.getKey().offset(distance, 0, 0);
                            break;
                    }
                    checkCoordinate(target, path + ".distance");
                    additions.put(target,
                            new MutableCell(state, Collections.<String>emptySet(), entry.getValue().localTransform));
                }
            }
            maximumCells.putAll(additions);
            if (maximumCells.size() > WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE) {
                throw new IOException(path + ": worst-case deposit exceeds "
                        + WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE + " cells");
            }
        }

        private WeightedResult weighted(Object input, String path) throws IOException {
            if (input instanceof String) {
                return new WeightedResult(Integer.valueOf(palette.index(ModelJson.name(input, path), path)),
                        Collections.<StructureProgram.WeightedState>emptyList(), 0);
            }
            List<Object> values = ModelJson.array(input, path);
            if (values.isEmpty() || values.size() > 64) {
                throw new IOException(path + ": expected one state or 1..64 weighted states");
            }
            List<StructureProgram.WeightedState> states = new ArrayList<StructureProgram.WeightedState>();
            int total = 0;
            for (int index = 0; index < values.size(); index++) {
                String entryPath = path + "[" + index + "]";
                Map<String, Object> entry = ModelJson.object(values.get(index), entryPath);
                fields(entry, entryPath, "state", "weight");
                int weight = optionalInteger(entry.get("weight"), entryPath + ".weight", 1, 1000000, 1);
                try {
                    total = Math.addExact(total, weight);
                } catch (ArithmeticException error) {
                    throw new IOException(path + ": total weight is too large");
                }
                states.add(new StructureProgram.WeightedState(
                        palette.index(ModelJson.name(entry.get("state"), entryPath + ".state"), entryPath + ".state"),
                        weight));
            }
            return new WeightedResult(null, states, total);
        }

        private StructureProgram.Distribution distribution(Object input, String path) throws IOException {
            if (input == null) {
                return new StructureProgram.IndependentDistribution();
            }
            Map<String, Object> value = ModelJson.object(input, path);
            String type = ModelJson.name(value.get("type"), path + ".type");
            if (type.equals("independent")) {
                fields(value, path, "type");
                return new StructureProgram.IndependentDistribution();
            }
            if (type.equals("noise")) {
                fields(value, path, "type", "scale", "threshold");
                return new StructureProgram.NoiseDistribution(
                        optionalInteger(value.get("scale"), path + ".scale", 1, 32, 3),
                        value.get("threshold") == null
                                ? 0.5D
                                : number(value.get("threshold"), path + ".threshold", 0.0D, 1.0D));
            }
            if (type.equals("clusters")) {
                fields(value, path, "type", "radius", "density", "falloff");
                return new StructureProgram.ClusterDistribution(
                        optionalInteger(value.get("radius"), path + ".radius", 1, 16, 2),
                        value.get("density") == null
                                ? 0.2D
                                : number(value.get("density"), path + ".density", 0.0D, 1.0D),
                        value.get("falloff") == null
                                ? 0.65D
                                : number(value.get("falloff"), path + ".falloff", 0.0D, 1.0D));
            }
            throw new IOException(path + ".type: expected independent, noise, or clusters");
        }

        private StructureProgram.Selector selector(Object input, String path) throws IOException {
            if (input == null) {
                return new StructureProgram.Selector(Collections.<Integer>emptySet(), Collections.<Integer>emptySet(),
                        Collections.<String>emptySet(), false, Collections.<String>emptySet(), null, null, null, null,
                        Collections.<StructureProgram.Direction>emptySet(), null, 0, 6, null,
                        Collections.<String>emptySet());
            }
            Map<String, Object> value = ModelJson.object(input, path);
            fields(value, path, "states", "excludeStates", "tags", "match", "excludeTags", "bounds", "height", "faces",
                    "exposed", "minExposedFaces", "maxExposedFaces", "nearMarker", "excludeMarkers");
            Set<Integer> states = stateSet(value.get("states"), path + ".states");
            Set<Integer> excludedStates = stateSet(value.get("excludeStates"), path + ".excludeStates");
            Set<String> includedTags = tags(value.get("tags"), path + ".tags");
            Set<String> excludedTags = tags(value.get("excludeTags"), path + ".excludeTags");
            String match = optionalName(value.get("match"), path + ".match", "any");
            if (!match.equals("any") && !match.equals("all")) {
                throw new IOException(path + ".match: expected any or all");
            }
            BlockPosition minimum = null;
            BlockPosition maximum = null;
            if (value.get("bounds") != null) {
                Map<String, Object> bounds = ModelJson.object(value.get("bounds"), path + ".bounds");
                fields(bounds, path + ".bounds", "min", "max");
                minimum = vector(bounds.get("min"), path + ".bounds.min");
                maximum = vector(bounds.get("max"), path + ".bounds.max");
                if (minimum.x > maximum.x || minimum.y > maximum.y || minimum.z > maximum.z) {
                    throw new IOException(path + ".bounds: min must not exceed max");
                }
            }
            Integer minHeight = null;
            Integer maxHeight = null;
            if (value.get("height") != null) {
                Map<String, Object> height = ModelJson.object(value.get("height"), path + ".height");
                fields(height, path + ".height", "min", "max");
                minHeight = height.get("min") == null
                        ? null
                        : Integer.valueOf(integer(height.get("min"), path + ".height.min", -WorldGenLimits.MAX_HEIGHT,
                                WorldGenLimits.MAX_HEIGHT));
                maxHeight = height.get("max") == null
                        ? null
                        : Integer.valueOf(integer(height.get("max"), path + ".height.max", -WorldGenLimits.MAX_HEIGHT,
                                WorldGenLimits.MAX_HEIGHT));
                if (minHeight != null && maxHeight != null && minHeight.intValue() > maxHeight.intValue()) {
                    throw new IOException(path + ".height: min must not exceed max");
                }
            }
            Set<StructureProgram.Direction> selectedFaces = directionSet(value.get("faces"), path + ".faces");
            Boolean exposed = value.get("exposed") == null
                    ? null
                    : Boolean.valueOf(ModelJson.bool(value.get("exposed"), path + ".exposed"));
            int minExposed = optionalInteger(value.get("minExposedFaces"), path + ".minExposedFaces", 0, 6, 0);
            int maxExposed = optionalInteger(value.get("maxExposedFaces"), path + ".maxExposedFaces", 0, 6, 6);
            if (minExposed > maxExposed) {
                throw new IOException(path + ": minExposedFaces must not exceed maxExposedFaces");
            }
            StructureProgram.MarkerDistance nearMarker = null;
            if (value.get("nearMarker") != null) {
                Map<String, Object> marker = ModelJson.object(value.get("nearMarker"), path + ".nearMarker");
                fields(marker, path + ".nearMarker", "name", "distance", "value");
                nearMarker = new StructureProgram.MarkerDistance(
                        identifier(ModelJson.name(marker.get("name"), path + ".nearMarker.name"),
                                path + ".nearMarker.name"),
                        optionalInteger(marker.get("distance"), path + ".nearMarker.distance", 0, 32, 1),
                        immutable(marker.get("value"), path + ".nearMarker.value", 0));
            }
            Set<String> excludedMarkers = identifiers(value.get("excludeMarkers"), path + ".excludeMarkers");
            return new StructureProgram.Selector(states, excludedStates, includedTags, match.equals("all"),
                    excludedTags, minimum, maximum, minHeight, maxHeight, selectedFaces, exposed, minExposed,
                    maxExposed, nearMarker, excludedMarkers);
        }

        private Set<Integer> stateSet(Object input, String path) throws IOException {
            if (input == null) {
                return Collections.emptySet();
            }
            List<Object> values = ModelJson.array(input, path);
            Set<Integer> result = new LinkedHashSet<Integer>();
            for (int index = 0; index < values.size(); index++) {
                result.add(Integer.valueOf(palette.index(ModelJson.name(values.get(index), path + "[" + index + "]"),
                        path + "[" + index + "]")));
            }
            return result;
        }

        private Set<StructureProgram.Direction> directionSet(Object input, String path) throws IOException {
            if (input == null) {
                return Collections.emptySet();
            }
            List<Object> values = ModelJson.array(input, path);
            Set<StructureProgram.Direction> result = new LinkedHashSet<StructureProgram.Direction>();
            for (int index = 0; index < values.size(); index++) {
                String name = ModelJson.name(values.get(index), path + "[" + index + "]");
                try {
                    result.add(StructureProgram.Direction.valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException error) {
                    throw new IOException(path + "[" + index + "]: expected down, up, north, south, west, or east");
                }
            }
            return result;
        }

        private Set<String> identifiers(Object input, String path) throws IOException {
            if (input == null) {
                return Collections.emptySet();
            }
            List<Object> values = ModelJson.array(input, path);
            Set<String> result = new LinkedHashSet<String>();
            for (int index = 0; index < values.size(); index++) {
                result.add(identifier(ModelJson.name(values.get(index), path + "[" + index + "]"),
                        path + "[" + index + "]"));
            }
            return result;
        }

        private static Map<BlockPosition, MutableCell> copyCells(Map<BlockPosition, MutableCell> source) {
            Map<BlockPosition, MutableCell> result = new LinkedHashMap<BlockPosition, MutableCell>();
            for (Map.Entry<BlockPosition, MutableCell> entry : source.entrySet()) {
                MutableCell cell = entry.getValue();
                result.put(entry.getKey(), new MutableCell(cell.state, cell.tags, cell.localTransform));
            }
            return result;
        }

        private void marker(Map<String, Object> value, Affine transform, String path) throws IOException {
            if (markers.size() >= MAX_MARKERS) {
                throw new IOException(path + ": structure exceeds " + MAX_MARKERS + " markers");
            }
            BlockPosition position = transform.apply(vector(value.get("pos"), path + ".pos"));
            checkCoordinate(position, path + ".pos");
            String name = identifier(ModelJson.name(value.get("name"), path + ".name"), path + ".name");
            if (name.equals("loot")) {
                throw new IOException(path + ": loot is a dedicated element type, not a marker name");
            }
            Object markerValue = immutable(value.get("value"), path + ".value", 0);
            markerValue = transformMarkerValue(name, markerValue, transform.orientation(), path + ".value");
            markers.add(new StructureTemplate.Marker(position, name, markerValue));
            StructureTemplate.Marker marker = markers.get(markers.size() - 1);
            maximumMarkers.add(marker);
            currentActions.add(new StructureProgram.MarkerAction(marker));
        }

        private void loot(Map<String, Object> value, Affine transform, String path, String instancePath)
                throws IOException {
            if (loots.size() >= MAX_LOOT_ELEMENTS) {
                throw new IOException(path + ": structure exceeds " + MAX_LOOT_ELEMENTS + " loot elements");
            }
            BlockPosition position = transform.apply(vector(value.get("pos"), path + ".pos"));
            checkCoordinate(position, path + ".pos");
            boolean randomized = value.get("pools") != null || value.get("table") != null;
            String key = randomized ? randomKey(value.get("key"), path + ".key", instancePath) : null;
            StructureLoot loot = StructureLoot.read(value, position, key, path);
            loots.add(loot);
            maximumLoots.add(loot);
            currentActions.add(new StructureProgram.LootAction(loot));
        }

        private void write(BlockPosition position, int state, Set<String> tags, boolean replace,
                StructureTransform localTransform, String path) throws IOException {
            checkCoordinate(position, path);
            emissions++;
            if (emissions > MAX_EMISSIONS) {
                throw new IOException(path + ": element expansion exceeds " + MAX_EMISSIONS + " emissions");
            }
            MutableCell previous = cells.get(position);
            if (previous == null) {
                cells.put(position, new MutableCell(state, tags, localTransform));
            } else if (previous.state == state) {
                previous.tags.addAll(tags);
            } else if (replace) {
                cells.put(position, new MutableCell(state, tags, localTransform));
            } else {
                throw new IOException(path + ": conflicting state at " + coordinate(position)
                        + "; use write='replace' for an intentional overwrite");
            }
            if (cells.size() > WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE) {
                throw new IOException(path + ": structure exceeds " + WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE
                        + " distinct cells");
            }
            MutableCell maximum = maximumCells.get(position);
            if (maximum == null || replace) {
                maximumCells.put(position, new MutableCell(state, tags, localTransform));
            } else {
                maximum.tags.addAll(tags);
            }
            currentActions.add(new StructureProgram.WriteAction(Collections
                    .singletonList(new StructureProgram.CellWrite(position, state, tags, localTransform, replace))));
        }

        private StructureTemplate finish(Bounds declared) throws IOException {
            List<StructureTemplate.TemplateBlock> blocks = new ArrayList<StructureTemplate.TemplateBlock>();
            for (Map.Entry<BlockPosition, MutableCell> entry : maximumCells.entrySet()) {
                MutableCell cell = entry.getValue();
                blocks.add(new StructureTemplate.TemplateBlock(entry.getKey(), cell.state, cell.tags,
                        cell.localTransform));
            }
            Collections.sort(blocks, BLOCK_ORDER);
            Collections.sort(maximumMarkers, MARKER_ORDER);
            Collections.sort(maximumLoots, LOOT_ORDER);

            for (StructureLoot loot : maximumLoots) {
                if (!maximumCells.containsKey(loot.position)) {
                    throw new IOException(
                            "loot element at " + coordinate(loot.position) + " does not target a structure block");
                }
            }

            Bounds occupied = occupiedBounds(blocks, maximumMarkers, maximumLoots);
            Bounds bounds = declared == null ? occupied : declared;
            if (!contains(bounds, occupied.minimum) || !contains(bounds, occupied.maximum)) {
                throw new IOException("structure.bounds: declared bounds do not contain all expanded cells, markers,"
                        + " and loot targets");
            }
            String hash = sha256(semanticHash(bounds, palette.entries, blocks, maximumMarkers, maximumLoots) + '|'
                    + sourceSemantics);
            return new StructureTemplate(hash, bounds.minimum, bounds.maximum, palette.entries, palette.names, blocks,
                    maximumMarkers, maximumLoots, new StructureProgram(currentActions, processors));
        }
    }

    private static final Comparator<StructureTemplate.TemplateBlock> BLOCK_ORDER =
            new Comparator<StructureTemplate.TemplateBlock>() {
        @Override
        public int compare(StructureTemplate.TemplateBlock left, StructureTemplate.TemplateBlock right) {
            int y = Integer.compare(left.position.y, right.position.y);
            int z = Integer.compare(left.position.z, right.position.z);
            return y != 0 ? y : z != 0 ? z : Integer.compare(left.position.x, right.position.x);
        }
    };

    private static final Comparator<StructureTemplate.Marker> MARKER_ORDER = new Comparator<StructureTemplate.Marker>() {
        @Override
        public int compare(StructureTemplate.Marker left, StructureTemplate.Marker right) {
            int y = Integer.compare(left.position.y, right.position.y);
            int z = Integer.compare(left.position.z, right.position.z);
            int x = Integer.compare(left.position.x, right.position.x);
            int name = left.name.compareTo(right.name);
            return y != 0 ? y : z != 0 ? z : x != 0 ? x : name;
        }
    };

    private static final Comparator<StructureLoot> LOOT_ORDER = new Comparator<StructureLoot>() {
        @Override
        public int compare(StructureLoot left, StructureLoot right) {
            int y = Integer.compare(left.position.y, right.position.y);
            int z = Integer.compare(left.position.z, right.position.z);
            int x = Integer.compare(left.position.x, right.position.x);
            if (y != 0 || z != 0 || x != 0) {
                return y != 0 ? y : z != 0 ? z : x;
            }
            String leftKey = left.key == null ? "" : left.key;
            String rightKey = right.key == null ? "" : right.key;
            return leftKey.compareTo(rightKey);
        }
    };

    private static String semanticHash(Bounds bounds, List<StructureTemplate.PaletteEntry> palette,
            List<StructureTemplate.TemplateBlock> blocks, List<StructureTemplate.Marker> markers,
            List<StructureLoot> loots) {
        StringBuilder value = new StringBuilder("betamoon_structure|");
        append(value, bounds.minimum);
        append(value, bounds.maximum);
        for (StructureTemplate.PaletteEntry entry : palette) {
            value.append('|').append(entry.name).append(entry.tags);
            for (int index = 0; index < entry.variants(); index++) {
                StructureTemplate.State state = entry.states.get(index);
                value.append(':').append(state.blockId).append(':').append(state.metadata).append(':')
                        .append(canonical(state.tileData)).append(':').append(state.cumulativeWeight);
            }
        }
        for (StructureTemplate.TemplateBlock block : blocks) {
            value.append("|b");
            append(value, block.position);
            value.append(':').append(block.state).append(':').append(block.tags).append(':')
                    .append(block.localTransform.rotation).append(':').append(block.localTransform.mirror);
        }
        for (StructureTemplate.Marker marker : markers) {
            value.append("|m");
            append(value, marker.position);
            value.append(':').append(marker.name).append(':').append(canonical(marker.value));
        }
        for (StructureLoot loot : loots) {
            value.append("|l:").append(loot.semantics());
        }
        return sha256(value.toString());
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte part : digest) {
                result.append(String.format(Locale.ROOT, "%02x", part & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void append(StringBuilder target, BlockPosition position) {
        target.append(position.x).append(',').append(position.y).append(',').append(position.z);
    }

    @SuppressWarnings("unchecked")
    private static String canonical(Object value) {
        if (value == null) {
            return "n";
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue() ? "b1" : "b0";
        }
        if (value instanceof Number) {
            return "d" + Long.toHexString(Double.doubleToLongBits(((Number) value).doubleValue()));
        }
        if (value instanceof String) {
            return "s" + ((String) value).length() + ':' + value;
        }
        if (value instanceof List) {
            StringBuilder result = new StringBuilder("l[");
            for (Object entry : (List<Object>) value) {
                result.append(canonical(entry)).append(';');
            }
            return result.append(']').toString();
        }
        if (value instanceof Map) {
            List<String> keys = new ArrayList<String>(((Map<String, Object>) value).keySet());
            Collections.sort(keys);
            StringBuilder result = new StringBuilder("m{");
            for (String key : keys) {
                result.append(canonical(key)).append('=').append(canonical(((Map<String, Object>) value).get(key)))
                        .append(';');
            }
            return result.append('}').toString();
        }
        return String.valueOf(value);
    }

    private static Bounds occupiedBounds(List<StructureTemplate.TemplateBlock> blocks,
            List<StructureTemplate.Marker> markers, List<StructureLoot> loots) {
        BlockPosition minimum = new BlockPosition(0, 0, 0);
        BlockPosition maximum = new BlockPosition(0, 0, 0);
        for (StructureTemplate.TemplateBlock block : blocks) {
            minimum = minimum(minimum, block.position);
            maximum = maximum(maximum, block.position);
        }
        for (StructureTemplate.Marker marker : markers) {
            minimum = minimum(minimum, marker.position);
            maximum = maximum(maximum, marker.position);
        }
        for (StructureLoot loot : loots) {
            minimum = minimum(minimum, loot.position);
            maximum = maximum(maximum, loot.position);
        }
        return new Bounds(minimum, maximum);
    }

    private static BlockPosition minimum(BlockPosition left, BlockPosition right) {
        return new BlockPosition(Math.min(left.x, right.x), Math.min(left.y, right.y), Math.min(left.z, right.z));
    }

    private static BlockPosition maximum(BlockPosition left, BlockPosition right) {
        return new BlockPosition(Math.max(left.x, right.x), Math.max(left.y, right.y), Math.max(left.z, right.z));
    }

    private static boolean contains(Bounds bounds, BlockPosition position) {
        return position.x >= bounds.minimum.x && position.x <= bounds.maximum.x && position.y >= bounds.minimum.y
                && position.y <= bounds.maximum.y && position.z >= bounds.minimum.z && position.z <= bounds.maximum.z;
    }

    private static String remap(String state, Map<String, String> remap) {
        String replacement = remap.get(state);
        return replacement == null ? state : replacement;
    }

    private static Map<String, String> composeRemap(Map<String, String> parent, Object input, String path)
            throws IOException {
        Map<String, String> result = new LinkedHashMap<String, String>(parent);
        if (input == null) {
            return result;
        }
        Map<String, Object> values = ModelJson.object(input, path);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            result.put(identifier(entry.getKey(), path),
                    remap(ModelJson.name(entry.getValue(), path + "." + entry.getKey()), parent));
        }
        return result;
    }

    private static StructureTransform transform(Map<String, Object> value, String path) throws IOException {
        try {
            StructureTransform.Rotation rotation = StructureTransform.rotation(
                    optionalName(value.get("rotation"), path + ".rotation", "none"), new java.util.Random(0L));
            StructureTransform.Mirror mirror = StructureTransform
                    .mirror(optionalName(value.get("mirror"), path + ".mirror", "none"), new java.util.Random(0L));
            return new StructureTransform(rotation, mirror);
        } catch (IllegalArgumentException error) {
            throw new IOException(path + ": " + error.getMessage());
        }
    }

    private static Object transformMarkerValue(String name, Object input, StructureTransform transform, String path)
            throws IOException {
        if (!name.equals("connector") || !(input instanceof Map)) {
            return input;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> original = (Map<String, Object>) input;
        Map<String, Object> value = new LinkedHashMap<String, Object>(original);
        Object facing = value.get("facing");
        if (facing != null) {
            try {
                StructureTransform.Direction direction = StructureTransform.Direction
                        .parse(ModelJson.name(facing, path + ".facing"));
                value.put("facing", transform.apply(direction).name().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException error) {
                throw new IOException(path + ".facing: expected north, east, south, or west");
            }
        }
        return Collections.unmodifiableMap(value);
    }

    private static boolean replace(Map<String, Object> value, String path) throws IOException {
        String write = optionalName(value.get("write"), path + ".write", "error");
        if (!write.equals("error") && !write.equals("replace")) {
            throw new IOException(path + ".write: expected error or replace");
        }
        return write.equals("replace");
    }

    private static Box box(Map<String, Object> value, String path) throws IOException {
        BlockPosition first = vector(value.get("from"), path + ".from");
        BlockPosition second = vector(value.get("to"), path + ".to");
        return new Box(minimum(first, second), maximum(first, second));
    }

    private static Set<String> faces(Object input, String path) throws IOException {
        if (input == null) {
            return new LinkedHashSet<String>(Arrays.asList("down", "up", "north", "south", "west", "east"));
        }
        Set<String> result = new LinkedHashSet<String>();
        List<Object> values = ModelJson.array(input, path);
        if (values.isEmpty()) {
            throw new IOException(path + ": expected at least one face");
        }
        for (int index = 0; index < values.size(); index++) {
            String face = ModelJson.name(values.get(index), path + "[" + index + "]");
            if (!Arrays.asList("down", "up", "north", "south", "west", "east").contains(face)) {
                throw new IOException(path + "[" + index + "]: unknown face " + face);
            }
            if (!result.add(face)) {
                throw new IOException(path + "[" + index + "]: duplicate face " + face);
            }
        }
        return result;
    }

    private static Set<String> caps(Object input, String path) throws IOException {
        if (input == null) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<String>();
        List<Object> values = ModelJson.array(input, path);
        for (int index = 0; index < values.size(); index++) {
            String cap = ModelJson.name(values.get(index), path + "[" + index + "]");
            if (!cap.equals("bottom") && !cap.equals("top")) {
                throw new IOException(path + "[" + index + "]: expected bottom or top");
            }
            if (!result.add(cap)) {
                throw new IOException(path + "[" + index + "]: duplicate cap " + cap);
            }
        }
        return result;
    }

    private static boolean shellFace(int x, int y, int z, Box box, int thickness, Set<String> faces) {
        return faces.contains("west") && x - box.min.x < thickness
                || faces.contains("east") && box.max.x - x < thickness
                || faces.contains("down") && y - box.min.y < thickness
                || faces.contains("up") && box.max.y - y < thickness
                || faces.contains("north") && z - box.min.z < thickness
                || faces.contains("south") && box.max.z - z < thickness;
    }

    private static int boundaryAxes(int x, int y, int z, Box box, int thickness) {
        int result = 0;
        if (x - box.min.x < thickness || box.max.x - x < thickness) {
            result++;
        }
        if (y - box.min.y < thickness || box.max.y - y < thickness) {
            result++;
        }
        if (z - box.min.z < thickness || box.max.z - z < thickness) {
            result++;
        }
        return result;
    }

    private static int interpolate(int from, int to, int step, int steps) {
        if (steps == 0) {
            return from;
        }
        return (int) Math.round(from + (to - from) * (step / (double) steps));
    }

    private static int[] ordered(int first, int second) {
        return first < second ? new int[]{first, second} : new int[]{second, first};
    }

    private static int[] radii(Object input, String path, int count) throws IOException {
        if (input instanceof Number) {
            int radius = integer(input, path, 1, WorldGenLimits.MAX_FEATURE_RADIUS);
            int[] result = new int[count];
            Arrays.fill(result, radius);
            return result;
        }
        List<Object> values = ModelJson.array(input, path);
        if (values.size() != count) {
            throw new IOException(path + ": expected one integer or exactly " + count + " integers");
        }
        int[] result = new int[count];
        for (int index = 0; index < count; index++) {
            result[index] = integer(values.get(index), path + "[" + index + "]", 1,
                    index == 1 && count == 3 ? WorldGenLimits.MAX_HEIGHT : WorldGenLimits.MAX_FEATURE_RADIUS);
        }
        return result;
    }

    private static BlockPosition vector(Object input, String path) throws IOException {
        List<Object> values = ModelJson.array(input, path);
        if (values.size() != 3) {
            throw new IOException(path + ": expected exactly three integers");
        }
        BlockPosition result = new BlockPosition(
                integer(values.get(0), path + "[0]", -WorldGenLimits.MAX_FEATURE_RADIUS,
                        WorldGenLimits.MAX_FEATURE_RADIUS),
                integer(values.get(1), path + "[1]", -WorldGenLimits.MAX_HEIGHT, WorldGenLimits.MAX_HEIGHT),
                integer(values.get(2), path + "[2]", -WorldGenLimits.MAX_FEATURE_RADIUS,
                        WorldGenLimits.MAX_FEATURE_RADIUS));
        return result;
    }

    private static int[] positiveVector(Object input, String path, int maximum) throws IOException {
        List<Object> values = ModelJson.array(input, path);
        if (values.size() != 3) {
            throw new IOException(path + ": expected exactly three positive integers");
        }
        return new int[]{integer(values.get(0), path + "[0]", 1, maximum),
                integer(values.get(1), path + "[1]", 1, maximum), integer(values.get(2), path + "[2]", 1, maximum)};
    }

    private static void checkCoordinate(BlockPosition position, String path) throws IOException {
        if (Math.abs(position.x) > WorldGenLimits.MAX_FEATURE_RADIUS
                || Math.abs(position.z) > WorldGenLimits.MAX_FEATURE_RADIUS
                || Math.abs(position.y) > WorldGenLimits.MAX_HEIGHT) {
            throw new IOException(
                    path + ": expanded coordinate " + coordinate(position) + " exceeds structure coordinate limits");
        }
    }

    private static String coordinate(BlockPosition position) {
        return "[" + position.x + ", " + position.y + ", " + position.z + "]";
    }

    private static int resolveBlock(Object value, String path) throws IOException {
        if (value instanceof Number) {
            int id = integer(value, path, 0, 255);
            if (id != 0 && (id >= Block.blocksList.length || Block.blocksList[id] == null)) {
                throw new IOException(path + ": unknown block id " + id);
            }
            return id;
        }
        String name = ModelJson.name(value, path);
        if (name.equals("air") || name.equals("minecraft:air")) {
            return 0;
        }
        String bare = name.indexOf(':') >= 0 ? name.substring(name.indexOf(':') + 1) : name;
        try {
            Field field = Block.class.getField(bare);
            if (Modifier.isStatic(field.getModifiers()) && Block.class.isAssignableFrom(field.getType())) {
                return ((Block) field.get(null)).blockID;
            }
        } catch (Exception ignored) {
        }
        for (int id = 1; id < Block.blocksList.length; id++) {
            Block block = Block.blocksList[id];
            if (block == null) {
                continue;
            }
            String internal = block.getBlockName();
            String normalized = internal == null
                    ? ""
                    : internal.startsWith("tile.") ? internal.substring("tile.".length()) : internal;
            if (name.equals(internal) || bare.equals(internal) || bare.equals(normalized)) {
                return id;
            }
        }
        throw new IOException(path + ": unknown block " + name);
    }

    private static Set<String> tags(Object input, String path) throws IOException {
        if (input == null) {
            return Collections.emptySet();
        }
        List<Object> values = ModelJson.array(input, path);
        if (values.size() > 64) {
            throw new IOException(path + ": exceeds 64 tags");
        }
        Set<String> result = new LinkedHashSet<String>();
        for (int index = 0; index < values.size(); index++) {
            String tag = identifier(ModelJson.name(values.get(index), path + "[" + index + "]"),
                    path + "[" + index + "]");
            if (!result.add(tag)) {
                throw new IOException(path + "[" + index + "]: duplicate tag " + tag);
            }
        }
        return result;
    }

    private static String identifier(String value, String path) throws IOException {
        if (!value.matches("[a-z][a-z0-9_.-]{0,63}")) {
            throw new IOException(path + ": expected lowercase identifier up to 64 characters");
        }
        return value;
    }

    private static int integer(Object value, String path, int minimum, int maximum) throws IOException {
        double number = ModelJson.number(value, path);
        if (number != Math.rint(number) || number < minimum || number > maximum) {
            throw new IOException(path + ": expected integer " + minimum + ".." + maximum);
        }
        return (int) number;
    }

    private static double number(Object value, String path, double minimum, double maximum) throws IOException {
        double number = ModelJson.number(value, path);
        if (number < minimum || number > maximum) {
            throw new IOException(path + ": expected number " + minimum + ".." + maximum);
        }
        return number;
    }

    private static int optionalInteger(Object value, String path, int minimum, int maximum, int fallback)
            throws IOException {
        return value == null ? fallback : integer(value, path, minimum, maximum);
    }

    private static String optionalName(Object value, String path, String fallback) throws IOException {
        return value == null ? fallback : ModelJson.name(value, path);
    }

    private static void fields(Map<String, Object> object, String path, String... supported) throws IOException {
        ModelJson.fields(object, path, supported);
    }

    private static Map<String, Object> immutableObject(Map<String, Object> input, String path, int depth)
            throws IOException {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            result.put(entry.getKey(), immutable(entry.getValue(), path + "." + entry.getKey(), depth + 1));
        }
        return Collections.unmodifiableMap(result);
    }

    @SuppressWarnings("unchecked")
    private static Object immutable(Object input, String path, int depth) throws IOException {
        if (depth > 32) {
            throw new IOException(path + ": data nesting exceeds 32 levels");
        }
        if (input == null || input instanceof String || input instanceof Boolean || input instanceof Number) {
            return input;
        }
        if (input instanceof Map) {
            return immutableObject((Map<String, Object>) input, path, depth);
        }
        if (input instanceof List) {
            List<Object> result = new ArrayList<Object>();
            int index = 0;
            for (Object value : (List<Object>) input) {
                result.add(immutable(value, path + "[" + index++ + "]", depth + 1));
            }
            return Collections.unmodifiableList(result);
        }
        throw new IOException(path + ": unsupported data value");
    }

    private enum CuboidMode {
        FILL, SHELL, FRAME
    }

    private static final class Palette {
        private final List<StructureTemplate.PaletteEntry> entries;
        private final Map<String, Integer> names;

        private Palette(List<StructureTemplate.PaletteEntry> entries, Map<String, Integer> names) {
            this.entries = entries;
            this.names = names;
        }

        private int index(String name, String path) throws IOException {
            Integer result = names.get(name);
            if (result == null) {
                throw new IOException(path + ": unknown palette state " + name);
            }
            return result.intValue();
        }
    }

    private static final class TemplateDefinition {
        private final List<Object> elements;
        private final String path;

        private TemplateDefinition(List<Object> elements, String path) {
            this.elements = elements;
            this.path = path;
        }
    }

    private static final class WeightedResult {
        private final Integer single;
        private final List<StructureProgram.WeightedState> states;
        private final int totalWeight;

        private WeightedResult(Integer single, List<StructureProgram.WeightedState> states, int totalWeight) {
            this.single = single;
            this.states = states;
            this.totalWeight = totalWeight;
        }
    }

    private static final class MutableCell {
        private int state;
        private final Set<String> tags;
        private final StructureTransform localTransform;

        private MutableCell(int state, Set<String> tags, StructureTransform localTransform) {
            this.state = state;
            this.tags = new LinkedHashSet<String>(tags);
            this.localTransform = localTransform;
        }
    }

    private static final class Bounds {
        private final BlockPosition minimum;
        private final BlockPosition maximum;

        private Bounds(BlockPosition minimum, BlockPosition maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }

    private static final class Box {
        private final BlockPosition min;
        private final BlockPosition max;

        private Box(BlockPosition min, BlockPosition max) {
            this.min = min;
            this.max = max;
        }
    }

    /** Integer horizontal affine transform plus vertical translation. */
    private static final class Affine {
        private static final Affine IDENTITY = new Affine(1, 0, 0, 1, 0, 0, 0);

        private final int xx;
        private final int xz;
        private final int zx;
        private final int zz;
        private final int tx;
        private final int ty;
        private final int tz;

        private Affine(int xx, int xz, int zx, int zz, int tx, int ty, int tz) {
            this.xx = xx;
            this.xz = xz;
            this.zx = zx;
            this.zz = zz;
            this.tx = tx;
            this.ty = ty;
            this.tz = tz;
        }

        private static Affine of(StructureTransform transform, BlockPosition translation) {
            BlockPosition x = transform.apply(1, 0, 0);
            BlockPosition z = transform.apply(0, 0, 1);
            return new Affine(x.x, z.x, x.z, z.z, translation.x, translation.y, translation.z);
        }

        private static Affine translation(int x, int y, int z) {
            return new Affine(1, 0, 0, 1, x, y, z);
        }

        private BlockPosition apply(BlockPosition position) {
            return new BlockPosition(xx * position.x + xz * position.z + tx, position.y + ty,
                    zx * position.x + zz * position.z + tz);
        }

        private Affine compose(Affine inner) {
            return new Affine(xx * inner.xx + xz * inner.zx, xx * inner.xz + xz * inner.zz,
                    zx * inner.xx + zz * inner.zx, zx * inner.xz + zz * inner.zz, xx * inner.tx + xz * inner.tz + tx,
                    inner.ty + ty, zx * inner.tx + zz * inner.tz + tz);
        }

        private StructureTransform orientation() {
            for (StructureTransform.Rotation rotation : StructureTransform.Rotation.values()) {
                for (StructureTransform.Mirror mirror : StructureTransform.Mirror.values()) {
                    StructureTransform candidate = new StructureTransform(rotation, mirror);
                    BlockPosition x = candidate.apply(1, 0, 0);
                    BlockPosition z = candidate.apply(0, 0, 1);
                    if (x.x == xx && x.z == zx && z.x == xz && z.z == zz) {
                        return candidate;
                    }
                }
            }
            return IDENTITY_TRANSFORM;
        }
    }
}
