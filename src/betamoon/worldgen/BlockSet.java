package betamoon.worldgen;

import java.util.Arrays;

/** Compact immutable set for Beta's byte-sized block IDs. */
public final class BlockSet {
    private final boolean[] values = new boolean[256];

    public BlockSet(int... blockIds) {
        for (int blockId : blockIds) {
            if (blockId < 0 || blockId >= values.length) {
                throw new IllegalArgumentException("Block ID is outside 0..255: " + blockId);
            }
            values[blockId] = true;
        }
    }

    public boolean contains(int blockId) {
        return blockId >= 0 && blockId < values.length && values[blockId];
    }

    public int[] values() {
        int count = 0;
        for (boolean value : values) {
            if (value) {
                count++;
            }
        }
        int[] result = new int[count];
        int index = 0;
        for (int blockId = 0; blockId < values.length; blockId++) {
            if (values[blockId]) {
                result[index++] = blockId;
            }
        }
        return Arrays.copyOf(result, result.length);
    }
}
