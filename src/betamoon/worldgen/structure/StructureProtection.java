package betamoon.worldgen.structure;

import betamoon.utils.WeakIdentityMap;
import java.util.BitSet;
import net.minecraft.src.Chunk;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;

/**
 * Explicit template protection, including air, travels with the native chunk
 * rather than a global position list.
 */
public final class StructureProtection {
    private static final String TAG = "BetaMoonProtectedStructureCells";
    private static final WeakIdentityMap<Chunk, BitSet> CELLS = new WeakIdentityMap<Chunk, BitSet>();

    private StructureProtection() {
    }

    public static void mark(World world, int x, int y, int z) {
        Chunk chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
        BitSet cells = CELLS.get(chunk);
        if (cells == null) {
            cells = new BitSet();
            CELLS.put(chunk, cells);
        }
        cells.set(index(x, y, z));
        chunk.setChunkModified();
    }

    public static boolean contains(World world, int x, int y, int z) {
        if (y < 0 || y > 127 || !world.getIChunkProvider().chunkExists(x >> 4, z >> 4)) {
            return false;
        }
        BitSet cells = CELLS.get(world.getChunkFromChunkCoords(x >> 4, z >> 4));
        return cells != null && cells.get(index(x, y, z));
    }

    public static void write(Chunk chunk, NBTTagCompound tag) {
        BitSet cells = CELLS.get(chunk);
        if (cells != null && !cells.isEmpty()) {
            tag.setByteArray(TAG, cells.toByteArray());
        }
    }

    public static Chunk read(Chunk chunk, NBTTagCompound tag) {
        if (chunk == null) {
            return null;
        }
        try {
            byte[] bytes = tag.hasKey(TAG) ? tag.getByteArray(TAG) : new byte[0];
            if (bytes.length > 4096) {
                throw new IllegalArgumentException("Structure protection exceeds one native chunk");
            }
            CELLS.put(chunk, BitSet.valueOf(bytes));
            return chunk;
        } catch (RuntimeException failure) {
            // Native caches catch Exception and regenerate terrain. Invalid saved ownership
            // is not a cache miss.
            throw new InvalidProtection(failure);
        }
    }

    private static int index(int x, int y, int z) {
        if (y < 0 || y > 127) {
            throw new IllegalArgumentException("Structure protection height must be in 0..127");
        }
        return (x & 15) << 11 | (z & 15) << 7 | y;
    }

    private static final class InvalidProtection extends Error {
        private InvalidProtection(Throwable cause) {
            super("save_failed: saved structure protection is invalid; restore a consistent chunk backup", cause);
        }
    }
}
