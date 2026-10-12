package betamoon.worldgen.surface;

import betamoon.worldgen.BlockSet;
import betamoon.worldgen.IntRange;
import betamoon.worldgen.SeedMixer;
import betamoon.worldgen.WorldGenKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Allocation-free compiled surface layers applied to the current raw chunk buffer. */
public final class SurfaceRuleSet {
    public final WorldGenKey key;
    public final String resourceOwner;
    public final String owner;
    public final String source;
    public final BlockSet replace;
    public final List<Layer> layers;
    public final Integer underwaterBlock;
    public final int seaLevel;

    public SurfaceRuleSet(WorldGenKey key, String resourceOwner, String owner, String source, BlockSet replace,
            List<Layer> layers, Integer underwaterBlock, int seaLevel) {
        this.key = key;
        this.resourceOwner = resourceOwner;
        this.owner = owner;
        this.source = source;
        this.replace = replace;
        this.layers = Collections.unmodifiableList(new ArrayList<Layer>(layers));
        this.underwaterBlock = underwaterBlock;
        this.seaLevel = seaLevel;
    }

    public void apply(long worldSeed, int chunkX, int chunkZ, byte[] blocks, int localX, int localZ) {
        long seed = SeedMixer.generationSeed(worldSeed, "minecraft:overworld", "surface", chunkX, chunkZ, key,
                localX * 31L + localZ);
        Random random = new Random(seed);
        int surfaceY = -1;
        for (int y = 127; y >= 0; y--) {
            int index = (localZ * 16 + localX) * 128 + y;
            int existing = blocks[index] & 255;
            if (existing == 0) {
                surfaceY = -1;
                continue;
            }
            if (!replace.contains(existing)) {
                continue;
            }
            if (surfaceY < 0) {
                surfaceY = y;
                int cursor = y;
                for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
                    Layer layer = layers.get(layerIndex);
                    int depth = layer.depth.sample(random);
                    int block = layerIndex == 0 && surfaceY < seaLevel && underwaterBlock != null
                            ? underwaterBlock.intValue() : layer.blockId;
                    for (int offset = 0; offset < depth && cursor >= 0; offset++, cursor--) {
                        int target = (localZ * 16 + localX) * 128 + cursor;
                        if (!replace.contains(blocks[target] & 255)) {
                            break;
                        }
                        blocks[target] = (byte) block;
                    }
                }
                y = Math.min(y, cursor + 1);
            }
        }
    }

    public static final class Layer {
        public final int blockId;
        public final IntRange depth;

        public Layer(int blockId, IntRange depth) {
            this.blockId = blockId;
            this.depth = depth;
        }
    }
}
