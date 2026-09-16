package betamoon.luaapi.block;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.src.Chunk;
import net.minecraft.src.Vec3D;
import net.minecraft.src.World;

/**
 * Client-only draw positions. Never creates tiles, alters blocks, or writes
 * world data.
 */
public final class BlockModelScene {
    private static final Map<Chunk, BitSet> LOADED = new WeakHashMap<>();
    private static final BlockModelRenderer RENDERER = new BlockModelRenderer();

    private BlockModelScene() {
    }

    public static void loaded(Chunk chunk) {
        LOADED.put(chunk, index(chunk));
    }

    public static void unloaded(Chunk chunk) {
        LOADED.remove(chunk);
    }

    public static void changed(Chunk chunk, int x, int y, int z, boolean changed) {
        BitSet positions = LOADED.get(chunk);
        if (changed && positions != null) {
            positions.set(x << 11 | z << 7 | y, BlockModelRegistry.isDynamic(chunk.getBlockID(x, y, z)));
        }
    }

    static void rebuildLoaded() {
        for (Chunk chunk : new ArrayList<>(LOADED.keySet())) {
            LOADED.put(chunk, index(chunk));
        }
    }

    private static BitSet index(Chunk chunk) {
        BitSet positions = new BitSet();
        if (chunk.blocks == null) {
            return positions;
        }
        boolean[] dynamic = new boolean[256];
        boolean anyDynamic = false;
        for (int id = 1; id < dynamic.length; id++) {
            dynamic[id] = BlockModelRegistry.isDynamic(id);
            anyDynamic |= dynamic[id];
        }
        if (anyDynamic) {
            for (int index = 0; index < chunk.blocks.length; index++) {
                if (dynamic[chunk.blocks[index] & 255]) {
                    positions.set(index);
                }
            }
        }
        return positions;
    }

    public static void render(World world, Vec3D camera, float partialTick) {
        if (world == null || camera == null) {
            return;
        }
        for (Map.Entry<Chunk, BitSet> entry : new ArrayList<>(LOADED.entrySet())) {
            Chunk chunk = entry.getKey();
            if (chunk.worldObj != world || !chunk.isChunkLoaded) {
                continue;
            }
            BitSet positions = entry.getValue();
            for (int index = positions.nextSetBit(0); index >= 0; index = positions.nextSetBit(index + 1)) {
                int x = chunk.xPosition * 16 + (index >> 11);
                int y = index & 127;
                int z = chunk.zPosition * 16 + ((index >> 7) & 15);
                double dx = x + 0.5 - camera.xCoord;
                double dy = y + 0.5 - camera.yCoord;
                double dz = z + 0.5 - camera.zCoord;
                // Match the previous special-renderer range, independently of world tile
                // entities.
                if (dx * dx + dy * dy + dz * dz < 4096) {
                    RENDERER.render(world, x, y, z, x - camera.xCoord, y - camera.yCoord, z - camera.zCoord,
                            partialTick);
                }
            }
        }
    }
}
