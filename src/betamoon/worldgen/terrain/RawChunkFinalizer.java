package betamoon.worldgen.terrain;

import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.NibbleArray;
import net.minecraft.src.World;

/**
 * Applies metadata before native height/skylight setup and initializes local
 * emitted block light.
 */
public final class RawChunkFinalizer {
    private RawChunkFinalizer() {
    }

    public static Chunk build(World world, int chunkX, int chunkZ, ChunkBlockBuffer buffer) {
        Chunk chunk = new Chunk(world, buffer.copyBlocks(), chunkX, chunkZ);
        chunk.data = new NibbleArray(buffer.copyMetadata());
        chunk.func_1024_c();
        emittedLight(chunk);
        return chunk;
    }

    private static void emittedLight(Chunk chunk) {
        int sources = 0;
        for (byte block : chunk.blocks) {
            if (Block.lightValue[block & 255] > 0) {
                sources++;
            }
        }
        if (sources == 0) {
            return;
        }
        int[] queue = new int[ChunkBlockBuffer.CELL_COUNT * 16];
        int end = 0;
        for (int index = 0; index < chunk.blocks.length; index++) {
            int light = Block.lightValue[chunk.blocks[index] & 255];
            if (light > 0) {
                chunk.blocklightMap.setNibble(index >> 11, index & 127, (index >> 7) & 15, light);
                queue[end++] = index;
            }
        }
        int start = 0;
        int[] dx = {1, -1, 0, 0, 0, 0};
        int[] dy = {0, 0, 1, -1, 0, 0};
        int[] dz = {0, 0, 0, 0, 1, -1};
        while (start < end) {
            int index = queue[start++];
            int x = index >> 11;
            int y = index & 127;
            int z = (index >> 7) & 15;
            int light = chunk.blocklightMap.getNibble(x, y, z);
            for (int side = 0; side < 6; side++) {
                int nx = x + dx[side];
                int ny = y + dy[side];
                int nz = z + dz[side];
                if (nx < 0 || nx >= 16 || nz < 0 || nz >= 16 || ny < 0 || ny >= 128) {
                    continue;
                }
                int target = (nx << 11) | (nz << 7) | ny;
                int propagated = light - Math.max(1, Block.lightOpacity[chunk.blocks[target] & 255]);
                if (propagated > chunk.blocklightMap.getNibble(nx, ny, nz)) {
                    chunk.blocklightMap.setNibble(nx, ny, nz, propagated);
                    if (end == queue.length) {
                        throw new IllegalStateException("Raw light propagation exceeded its work budget");
                    }
                    queue[end++] = target;
                }
            }
        }
    }
}
