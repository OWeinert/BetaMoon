package betamoon.worldgen.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Deterministic fixed, sampled, or depth-layered terrain material. */
public final class TerrainMaterialPolicy {
    public enum Kind {
        FIXED, SAMPLE_SURFACE, SAMPLE_SUBSURFACE, PALETTE
    }

    public static final TerrainMaterialPolicy SAMPLE_SURFACE = new TerrainMaterialPolicy(Kind.SAMPLE_SURFACE, 0, 0,
            Collections.<State>emptyList());
    public static final TerrainMaterialPolicy SAMPLE_SUBSURFACE = new TerrainMaterialPolicy(Kind.SAMPLE_SUBSURFACE, 0,
            0, Collections.<State>emptyList());

    public final Kind kind;
    public final int blockId;
    public final int metadata;
    public final List<State> layers;

    public TerrainMaterialPolicy(Kind kind, int blockId, int metadata, List<State> layers) {
        this.kind = kind;
        this.blockId = blockId;
        this.metadata = metadata;
        this.layers = layers == null
                ? Collections.<State>emptyList()
                : Collections.unmodifiableList(new ArrayList<State>(layers));
    }

    public static TerrainMaterialPolicy fixed(int blockId, int metadata) {
        return new TerrainMaterialPolicy(Kind.FIXED, blockId, metadata, Collections.<State>emptyList());
    }

    String signature() {
        return kind.name() + ':' + blockId + ':' + metadata + ':' + layers;
    }

    public static final class State {
        public final int blockId;
        public final int metadata;
        public final int weight;

        public State(int blockId, int metadata, int weight) {
            this.blockId = blockId;
            this.metadata = metadata;
            this.weight = weight;
        }

        @Override
        public String toString() {
            return blockId + ":" + metadata + "@" + weight;
        }
    }
}
