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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import net.minecraft.src.Block;

/** Strict, bounded BetaMoon local-structure template. */
public final class StructureTemplate {
    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    public final String contentHash;
    public final BlockPosition origin;
    public final List<PaletteEntry> palette;
    public final List<TemplateBlock> blocks;
    public final List<Marker> markers;

    private StructureTemplate(int sizeX, int sizeY, int sizeZ, String contentHash, BlockPosition origin,
            List<PaletteEntry> palette, List<TemplateBlock> blocks, List<Marker> markers) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.contentHash = contentHash;
        this.origin = origin;
        this.palette = Collections.unmodifiableList(palette);
        this.blocks = Collections.unmodifiableList(blocks);
        this.markers = Collections.unmodifiableList(markers);
    }

    public static StructureTemplate read(byte[] bytes) throws IOException {
        Map<String, Object> root = ModelJson.read(bytes);
        ModelJson.fields(root, "structure", "format", "size", "origin", "palette", "blocks", "markers",
                "tags", "variant");
        if (!"betamoon_structure".equals(ModelJson.name(root.get("format"), "structure.format"))) {
            throw new IOException("structure.format: expected betamoon_structure");
        }
        int[] size = vector(root.get("size"), "structure.size", 1, WorldGenLimits.MAX_FEATURE_RADIUS * 2 + 1);
        if (size[1] > WorldGenLimits.MAX_HEIGHT + 1) {
            throw new IOException("structure.size[2]: exceeds world height");
        }
        int[] origin = vector(root.get("origin"), "structure.origin", 0,
                WorldGenLimits.MAX_FEATURE_RADIUS * 2);
        if (origin[0] >= size[0] || origin[1] >= size[1] || origin[2] >= size[2]) {
            throw new IOException("structure.origin: expected a position inside structure.size");
        }

        List<Object> paletteValues = ModelJson.array(root.get("palette"), "structure.palette");
        if (paletteValues.isEmpty() || paletteValues.size() > 4096) {
            throw new IOException("structure.palette: expected 1..4096 entries");
        }
        List<PaletteEntry> palette = new ArrayList<PaletteEntry>();
        for (int index = 0; index < paletteValues.size(); index++) {
            palette.add(palette(ModelJson.object(paletteValues.get(index), "structure.palette[" + index + "]"),
                    "structure.palette[" + index + "]"));
        }

        List<Object> blockValues = ModelJson.array(root.get("blocks"), "structure.blocks");
        if (blockValues.size() > WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE) {
            throw new IOException("structure.blocks: exceeds " + WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE);
        }
        List<TemplateBlock> blocks = new ArrayList<TemplateBlock>();
        Map<String, Boolean> occupied = new LinkedHashMap<String, Boolean>();
        for (int index = 0; index < blockValues.size(); index++) {
            String path = "structure.blocks[" + index + "]";
            Map<String, Object> value = ModelJson.object(blockValues.get(index), path);
            ModelJson.fields(value, path, "pos", "state");
            int[] position = position(value.get("pos"), path + ".pos", size);
            int state = integer(value.get("state"), path + ".state", 0, palette.size() - 1);
            String coordinate = position[0] + "," + position[1] + "," + position[2];
            if (occupied.put(coordinate, Boolean.TRUE) != null) {
                throw new IOException(path + ".pos: duplicate block position " + coordinate);
            }
            blocks.add(new TemplateBlock(new BlockPosition(position[0], position[1], position[2]), state));
        }

        List<Marker> markers = new ArrayList<Marker>();
        Object markerInput = root.get("markers");
        if (markerInput != null) {
            List<Object> markerValues = ModelJson.array(markerInput, "structure.markers");
            if (markerValues.size() > 1024) {
                throw new IOException("structure.markers: exceeds 1024 entries");
            }
            for (int index = 0; index < markerValues.size(); index++) {
                String path = "structure.markers[" + index + "]";
                Map<String, Object> value = ModelJson.object(markerValues.get(index), path);
                ModelJson.fields(value, path, "pos", "name", "value");
                int[] position = position(value.get("pos"), path + ".pos", size);
                markers.add(new Marker(new BlockPosition(position[0], position[1], position[2]),
                        ModelJson.name(value.get("name"), path + ".name"), value.get("value")));
            }
        }
        return new StructureTemplate(size[0], size[1], size[2],
                sha256(canonical(root).getBytes(StandardCharsets.UTF_8)),
                new BlockPosition(origin[0], origin[1], origin[2]), palette, blocks, markers);
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
            String text = (String) value;
            return "s" + text.length() + ":" + text;
        }
        if (value instanceof List) {
            StringBuilder result = new StringBuilder("l[");
            for (Object entry : (List<Object>) value) {
                result.append(canonical(entry)).append(';');
            }
            return result.append(']').toString();
        }
        if (value instanceof Map) {
            StringBuilder result = new StringBuilder("m{");
            for (Map.Entry<String, Object> entry
                    : new TreeMap<String, Object>((Map<String, Object>) value).entrySet()) {
                result.append(canonical(entry.getKey())).append('=').append(canonical(entry.getValue())).append(';');
            }
            return result.append('}').toString();
        }
        throw new IllegalArgumentException("Unsupported structure value: " + value.getClass().getName());
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static PaletteEntry palette(Map<String, Object> value, String path) throws IOException {
        ModelJson.fields(value, path, "block", "meta", "data", "variants");
        List<State> states = new ArrayList<State>();
        int totalWeight = 0;
        Object variants = value.get("variants");
        if (variants != null) {
            List<Object> entries = ModelJson.array(variants, path + ".variants");
            if (entries.isEmpty() || entries.size() > 64) {
                throw new IOException(path + ".variants: expected 1..64 entries");
            }
            for (int index = 0; index < entries.size(); index++) {
                String entryPath = path + ".variants[" + index + "]";
                Map<String, Object> entry = ModelJson.object(entries.get(index), entryPath);
                ModelJson.fields(entry, entryPath, "block", "meta", "data", "weight");
                int weight = entry.get("weight") == null ? 1 : integer(entry.get("weight"), entryPath + ".weight",
                        1, 1000000);
                totalWeight = Math.addExact(totalWeight, weight);
                states.add(state(entry, entryPath, totalWeight));
            }
        } else {
            totalWeight = 1;
            states.add(state(value, path, totalWeight));
        }
        return new PaletteEntry(Collections.unmodifiableList(states), totalWeight);
    }

    private static State state(Map<String, Object> value, String path, int cumulativeWeight) throws IOException {
        int block = resolveBlock(value.get("block"), path + ".block");
        int metadata = value.get("meta") == null ? 0 : integer(value.get("meta"), path + ".meta", 0, 15);
        Map<String, Object> data = value.get("data") == null ? Collections.<String, Object>emptyMap()
                : new LinkedHashMap<String, Object>(ModelJson.object(value.get("data"), path + ".data"));
        return new State(block, metadata, Collections.unmodifiableMap(data), cumulativeWeight);
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
            String normalized = internal == null ? ""
                    : internal.startsWith("tile.") ? internal.substring("tile.".length()) : internal;
            if (name.equals(internal) || bare.equals(internal) || bare.equals(normalized)) {
                return id;
            }
        }
        throw new IOException(path + ": unknown block " + name);
    }

    private static int[] vector(Object value, String path, int min, int max) throws IOException {
        List<Object> values = ModelJson.array(value, path);
        if (values.size() != 3) {
            throw new IOException(path + ": expected exactly three integers");
        }
        return new int[] { integer(values.get(0), path + "[0]", min, max),
                integer(values.get(1), path + "[1]", min, max),
                integer(values.get(2), path + "[2]", min, max) };
    }

    private static int[] position(Object value, String path, int[] size) throws IOException {
        List<Object> values = ModelJson.array(value, path);
        if (values.size() != 3) {
            throw new IOException(path + ": expected exactly three integers");
        }
        return new int[] { integer(values.get(0), path + "[0]", 0, size[0] - 1),
                integer(values.get(1), path + "[1]", 0, size[1] - 1),
                integer(values.get(2), path + "[2]", 0, size[2] - 1) };
    }

    private static int integer(Object value, String path, int min, int max) throws IOException {
        double number = ModelJson.number(value, path);
        if (number != Math.rint(number) || number < min || number > max) {
            throw new IOException(path + ": expected integer " + min + ".." + max);
        }
        return (int) number;
    }

    public static final class PaletteEntry {
        private final List<State> states;
        private final int totalWeight;

        private PaletteEntry(List<State> states, int totalWeight) {
            this.states = states;
            this.totalWeight = totalWeight;
        }

        public State select(Random random) {
            int selected = random.nextInt(totalWeight) + 1;
            for (State state : states) {
                if (selected <= state.cumulativeWeight) {
                    return state;
                }
            }
            return states.get(states.size() - 1);
        }

        public int variants() {
            return states.size();
        }
    }

    public static final class State {
        public final int blockId;
        public final int metadata;
        public final Map<String, Object> tileData;
        private final int cumulativeWeight;

        private State(int blockId, int metadata, Map<String, Object> tileData, int cumulativeWeight) {
            this.blockId = blockId;
            this.metadata = metadata;
            this.tileData = tileData;
            this.cumulativeWeight = cumulativeWeight;
        }
    }

    public static final class TemplateBlock {
        public final BlockPosition position;
        public final int state;

        private TemplateBlock(BlockPosition position, int state) {
            this.position = position;
            this.state = state;
        }
    }

    public static final class Marker {
        public final BlockPosition position;
        public final String name;
        public final Object value;

        private Marker(BlockPosition position, String name, Object value) {
            this.position = position;
            this.name = name;
            this.value = value;
        }
    }
}
