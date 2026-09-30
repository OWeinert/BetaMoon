package betamoon.worldgen.structure;

import betamoon.worldgen.BlockPosition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Immutable structure compiled from the shared declarative structure document. */
public final class StructureTemplate {
    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    public final String contentHash;
    public final BlockPosition origin;
    public final BlockPosition minimum;
    public final BlockPosition maximum;
    public final List<PaletteEntry> palette;
    public final List<TemplateBlock> blocks;
    public final List<Marker> markers;
    public final List<StructureLoot> loots;

    private final Map<String, Integer> paletteNames;
    private final StructureProgram program;

    StructureTemplate(String contentHash, BlockPosition minimum, BlockPosition maximum,
            List<PaletteEntry> palette, Map<String, Integer> paletteNames, List<TemplateBlock> blocks,
            List<Marker> markers, List<StructureLoot> loots, StructureProgram program) {
        this.contentHash = contentHash;
        this.minimum = minimum;
        this.maximum = maximum;
        sizeX = maximum.x - minimum.x + 1;
        sizeY = maximum.y - minimum.y + 1;
        sizeZ = maximum.z - minimum.z + 1;
        origin = new BlockPosition(0, 0, 0);
        this.palette = Collections.unmodifiableList(new ArrayList<PaletteEntry>(palette));
        this.paletteNames = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(paletteNames));
        this.blocks = Collections.unmodifiableList(new ArrayList<TemplateBlock>(blocks));
        this.markers = Collections.unmodifiableList(new ArrayList<Marker>(markers));
        this.loots = Collections.unmodifiableList(new ArrayList<StructureLoot>(loots));
        this.program = program;
    }

    public static StructureTemplate read(byte[] bytes) throws IOException {
        return StructureDocumentReader.read(bytes);
    }

    /** Compiles a strict data tree produced by another declarative frontend such as Lua. */
    public static StructureTemplate read(Map<String, Object> document) throws IOException {
        return StructureDocumentReader.read(document);
    }

    public Integer paletteIndex(String name) {
        return paletteNames.get(name);
    }

    /** Resolves deterministic conditional geometry and document processors for one placement seed. */
    public Resolved resolve(long placementSeed) {
        StructureProgram.Resolved resolved = program.resolve(placementSeed, palette);
        return new Resolved(resolved.blocks, resolved.markers, resolved.loots);
    }

    public static final class Resolved {
        public final List<TemplateBlock> blocks;
        public final List<Marker> markers;
        public final List<StructureLoot> loots;

        private Resolved(List<TemplateBlock> blocks, List<Marker> markers, List<StructureLoot> loots) {
            this.blocks = blocks;
            this.markers = markers;
            this.loots = loots;
        }
    }

    public static final class PaletteEntry {
        public final String name;
        public final Set<String> tags;
        final List<State> states;
        final int totalWeight;

        PaletteEntry(String name, Set<String> tags, List<State> states, int totalWeight) {
            this.name = name;
            this.tags = Collections.unmodifiableSet(new LinkedHashSet<String>(tags));
            this.states = Collections.unmodifiableList(new ArrayList<State>(states));
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

        State first() {
            return states.get(0);
        }
    }

    public static final class State {
        public final int blockId;
        public final int metadata;
        public final Map<String, Object> tileData;
        final int cumulativeWeight;

        State(int blockId, int metadata, Map<String, Object> tileData, int cumulativeWeight) {
            this.blockId = blockId;
            this.metadata = metadata;
            this.tileData = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(tileData));
            this.cumulativeWeight = cumulativeWeight;
        }
    }

    public static final class TemplateBlock {
        public final BlockPosition position;
        public final int state;
        public final Set<String> tags;
        public final StructureTransform localTransform;

        TemplateBlock(BlockPosition position, int state, Set<String> tags, StructureTransform localTransform) {
            this.position = position;
            this.state = state;
            this.tags = Collections.unmodifiableSet(new LinkedHashSet<String>(tags));
            this.localTransform = localTransform;
        }
    }

    public static final class Marker {
        public final BlockPosition position;
        public final String name;
        public final Object value;

        Marker(BlockPosition position, String name, Object value) {
            this.position = position;
            this.name = name;
            this.value = value;
        }
    }
}
