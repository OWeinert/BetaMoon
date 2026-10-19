package betamoon.worldgen.terrain;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.ChunkLoader;
import net.minecraft.src.EnumSkyBlock;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;

/**
 * Differential native lighting across publication order, opaque/fluid cells and
 * saved chunks.
 */
public final class GeneratedChunkLightingTest {
    public static void verify() throws Exception {
        for (int order = 0; order < 3; order++) {
            LightingWorld optimized = world();
            LightingWorld reference = world();
            List<int[]> coordinates = new ArrayList<int[]>();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    coordinates.add(new int[]{x, z});
                }
            }
            if (order == 1) {
                Collections.reverse(coordinates);
            } else if (order == 2) {
                Collections.shuffle(coordinates, new Random(817));
            }
            for (int[] coordinate : coordinates) {
                Chunk actual = create(optimized, coordinate[0], coordinate[1]);
                Chunk expected = create(reference, coordinate[0], coordinate[1]);
                require(Arrays.equals(actual.blocklightMap.data, localReference(actual)),
                        "Local propagation must match independent relaxation");
                optimized.loaded.chunks.put(key(coordinate[0], coordinate[1]), actual);
                reference.loaded.chunks.put(key(coordinate[0], coordinate[1]), expected);
                GeneratedChunkLighting.chunkLoaded(actual);
                full(reference, expected);
                drain(optimized);
                drain(reference);
            }
            // Solve the loaded reference area to its final native fixed point.
            for (Chunk chunk : reference.loaded.chunks.values()) {
                full(reference, chunk);
            }
            drain(reference);
            for (Map.Entry<Long, Chunk> entry : optimized.loaded.chunks.entrySet()) {
                require(Arrays.equals(entry.getValue().blocklightMap.data,
                        reference.loaded.chunks.get(entry.getKey()).blocklightMap.data),
                        "Boundary and diagonal publication must preserve native light: order " + order);
            }
            require(optimized.scheduledBlockCells < reference.scheduledBlockCells,
                    "Boundary reconciliation must schedule less work than full chunk scans");
            Chunk center = optimized.loaded.chunks.get(key(0, 0));
            NBTTagCompound saved = new NBTTagCompound();
            ChunkLoader.storeChunkInCompound(center, optimized, saved);
            Chunk restored = ChunkLoader.loadChunkIntoWorldFromCompound(optimized, saved);
            require(Arrays.equals(center.blocklightMap.data, restored.blocklightMap.data),
                    "Saved lighting must round trip");
            optimized.loaded.chunks.put(key(0, 0), restored);
            long before = optimized.scheduledBlockCells;
            GeneratedChunkLighting.chunkLoaded(restored);
            require(optimized.scheduledBlockCells - before == ChunkBlockBuffer.CELL_COUNT,
                    "Saved chunks retain full native reconciliation");
            drain(optimized);
        }
    }

    private static LightingWorld world() throws Exception {
        return new LightingWorld(new TerrainFoundationTest.Save(Files.createTempDirectory("worldgen-light-")));
    }

    private static Chunk create(LightingWorld world, int cx, int cz) {
        ChunkBlockBuffer buffer = new ChunkBlockBuffer();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                buffer.fillColumn(x, z, 0, 3, 1, 0);
                if ((x + z) % 3 == 0) {
                    buffer.set(x, 11, z, Block.waterStill.blockID, 0);
                    buffer.set(x, 17, z, Block.glass.blockID, 0);
                }
                if (x == 8) {
                    buffer.fillColumn(x, z, 20, 25, 1, 0);
                }
            }
        }
        if (cx == 0 && cz == 0) {
            buffer.set(15, 12, 8, Block.glowStone.blockID, 0);
            buffer.set(10, 20, 15, Block.lavaStill.blockID, 0);
            buffer.set(15, 31, 15, Block.torchWood.blockID, 0);
        }
        if (cx == -1 && cz == -1) {
            buffer.set(15, 12, 15, Block.glowStone.blockID, 0);
        }
        return RawChunkFinalizer.build(world, cx, cz, buffer);
    }

    private static byte[] localReference(Chunk chunk) {
        int[] light = new int[ChunkBlockBuffer.CELL_COUNT];
        for (int index = 0; index < light.length; index++) {
            light[index] = Block.lightValue[chunk.blocks[index] & 255];
        }
        boolean changed = true;
        for (int pass = 0; changed && pass < 16; pass++) {
            changed = false;
            for (int index = 0; index < light.length; index++) {
                int x = index >> 11;
                int z = (index >> 7) & 15;
                int y = index & 127;
                int neighbor = 0;
                if (x > 0) {
                    neighbor = Math.max(neighbor, light[index - 2048]);
                }
                if (x < 15) {
                    neighbor = Math.max(neighbor, light[index + 2048]);
                }
                if (z > 0) {
                    neighbor = Math.max(neighbor, light[index - 128]);
                }
                if (z < 15) {
                    neighbor = Math.max(neighbor, light[index + 128]);
                }
                if (y > 0) {
                    neighbor = Math.max(neighbor, light[index - 1]);
                }
                if (y < 127) {
                    neighbor = Math.max(neighbor, light[index + 1]);
                }
                int value = Math.max(light[index],
                        neighbor - Math.max(1, Block.lightOpacity[chunk.blocks[index] & 255]));
                changed |= value != light[index];
                light[index] = value;
            }
        }
        byte[] result = new byte[light.length / 2];
        for (int index = 0; index < light.length; index++) {
            result[index >> 1] |= (byte) (light[index] << ((index & 1) * 4));
        }
        return result;
    }

    private static void full(World world, Chunk chunk) {
        int x = chunk.xPosition * 16;
        int z = chunk.zPosition * 16;
        world.scheduleLightingUpdate(EnumSkyBlock.Block, x, 0, z, x + 15, 127, z + 15);
    }

    private static void drain(World world) {
        int batches = 0;
        while (world.updatingLighting()) {
            require(++batches < 10000, "Lighting must terminate");
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static final class LightingWorld extends World {
        private LoadedProvider loaded;
        private long scheduledBlockCells;

        private LightingWorld(TerrainFoundationTest.Save save) {
            super(save, "lighting", new WorldProvider() {
            }, 42);
        }

        protected IChunkProvider getChunkProvider() {
            loaded = new LoadedProvider();
            return loaded;
        }

        protected void getInitialSpawnLocation() {
        }

        public void scheduleLightingUpdate(EnumSkyBlock kind, int minX, int minY, int minZ, int maxX, int maxY,
                int maxZ) {
            if (kind == EnumSkyBlock.Block) {
                scheduledBlockCells += (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            }
            super.scheduleLightingUpdate(kind, minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private static final class LoadedProvider implements IChunkProvider {
        private final Map<Long, Chunk> chunks = new LinkedHashMap<Long, Chunk>();

        public boolean chunkExists(int x, int z) {
            return chunks.containsKey(key(x, z));
        }

        public Chunk provideChunk(int x, int z) {
            Chunk chunk = chunks.get(key(x, z));
            if (chunk == null) {
                throw new AssertionError("Lighting must not load a missing chunk: " + x + "," + z);
            }
            return chunk;
        }

        public Chunk prepareChunk(int x, int z) {
            return provideChunk(x, z);
        }

        public void populate(IChunkProvider provider, int x, int z) {
        }

        public boolean saveChunks(boolean all, IProgressUpdate progress) {
            return true;
        }

        public boolean unload100OldestChunks() {
            return false;
        }

        public boolean canSave() {
            return true;
        }

        public String makeString() {
            return "loaded-only lighting fixture";
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
