package betamoon.worldgen.terrain;

import betamoon.worldgen.GenerationMetrics;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.EnumSkyBlock;
import net.minecraft.src.World;

/**
 * Local emitted light followed by loaded-boundary reconciliation at
 * publication.
 */
public final class GeneratedChunkLighting {
    private static final int MAX_WORK = ChunkBlockBuffer.CELL_COUNT * 16;
    private static final Set<Chunk> INITIALIZED = Collections.newSetFromMap(new WeakHashMap<Chunk, Boolean>());
    private static final ThreadLocal<LightQueue> QUEUES = ThreadLocal.withInitial(LightQueue::new);

    private GeneratedChunkLighting() {
    }

    static void initialize(Chunk chunk) {
        LightQueue shared = QUEUES.get();
        LightQueue queue = shared.inUse ? new LightQueue() : shared;
        queue.inUse = true;
        queue.size = 0;
        try {
            byte[] light = chunk.blocklightMap.data;
            for (int index = 0; index < chunk.blocks.length; index++) {
                int emission = Block.lightValue[chunk.blocks[index] & 255];
                if (emission > 0) {
                    setLight(light, index, emission);
                    queue.add(index);
                }
            }
            for (int start = 0; start < queue.size; start++) {
                int index = queue.cells[start];
                int value = getLight(light, index);
                if (value <= 1) {
                    continue;
                }
                int x = index >> 11;
                int z = (index >> 7) & 15;
                int y = index & 127;
                if (x > 0) {
                    spread(chunk.blocks, light, queue, index - 2048, value);
                }
                if (x < 15) {
                    spread(chunk.blocks, light, queue, index + 2048, value);
                }
                if (z > 0) {
                    spread(chunk.blocks, light, queue, index - 128, value);
                }
                if (z < 15) {
                    spread(chunk.blocks, light, queue, index + 128, value);
                }
                if (y > 0) {
                    spread(chunk.blocks, light, queue, index - 1, value);
                }
                if (y < 127) {
                    spread(chunk.blocks, light, queue, index + 1, value);
                }
            }
            synchronized (INITIALIZED) {
                INITIALIZED.add(chunk);
            }
        } finally {
            queue.inUse = false;
            queue.size = 0;
        }
    }

    private static void spread(byte[] blocks, byte[] light, LightQueue queue, int index, int value) {
        int propagated = value - Math.max(1, Block.lightOpacity[blocks[index] & 255]);
        if (propagated > getLight(light, index)) {
            setLight(light, index, propagated);
            queue.add(index);
        }
    }

    private static int getLight(byte[] light, int index) {
        return (light[index >> 1] >> ((index & 1) * 4)) & 15;
    }

    private static void setLight(byte[] light, int index, int value) {
        int shift = (index & 1) * 4;
        light[index >> 1] = (byte) ((light[index >> 1] & ~(15 << shift)) | (value << shift));
    }

    public static void chunkLoaded(Chunk chunk) {
        boolean initialized;
        synchronized (INITIALIZED) {
            initialized = INITIALIZED.remove(chunk);
        }
        World world = chunk.worldObj;
        int x = chunk.xPosition * 16;
        int z = chunk.zPosition * 16;
        if (!initialized) {
            // Saved chunks may contain light from neighbors absent during this load.
            world.scheduleLightingUpdate(EnumSkyBlock.Block, x, 0, z, x + 15, 127, z + 15);
            GenerationMetrics.record(GenerationMetrics.Counter.OWNED_BLOCK_LIGHTING_CELLS, ChunkBlockBuffer.CELL_COUNT);
            return;
        }
        if (world.blockExists(x - 1, 64, z)) {
            schedule(world, x - 1, z, x, z + 15);
        }
        if (world.blockExists(x + 16, 64, z)) {
            schedule(world, x + 15, z, x + 16, z + 15);
        }
        if (world.blockExists(x, 64, z - 1)) {
            schedule(world, x, z - 1, x + 15, z);
        }
        if (world.blockExists(x, 64, z + 16)) {
            schedule(world, x, z + 15, x + 15, z + 16);
        }
        // A newly available diagonal can unblock the native one-cell neighborhood
        // guard.
        for (int dx : new int[]{-1, 16}) {
            for (int dz : new int[]{-1, 16}) {
                if (world.blockExists(x + dx, 64, z + dz)) {
                    schedule(world, x + dx, z + dz, x + dx, z + dz);
                }
            }
        }
    }

    private static void schedule(World world, int minX, int minZ, int maxX, int maxZ) {
        GenerationMetrics.record(GenerationMetrics.Counter.OWNED_BLOCK_LIGHTING_CELLS,
                (long) (maxX - minX + 1) * 128 * (maxZ - minZ + 1));
        world.scheduleLightingUpdate(EnumSkyBlock.Block, minX, 0, minZ, maxX, 127, maxZ);
    }

    private static final class LightQueue {
        private int[] cells = new int[1024];
        private int size;
        private boolean inUse;

        private void add(int index) {
            if (size == MAX_WORK) {
                throw new IllegalStateException("Raw light propagation exceeded its work budget");
            }
            if (size == cells.length) {
                cells = Arrays.copyOf(cells, Math.min(MAX_WORK, cells.length * 2));
            }
            cells[size++] = index;
        }
    }
}
