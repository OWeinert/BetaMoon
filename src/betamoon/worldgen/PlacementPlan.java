package betamoon.worldgen;

import betamoon.tileentity.LuaTileEntity;
import betamoon.tileentity.TileEntityRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import org.luaj.vm2.LuaValue;

/** Bounded transactional block changes for one local feature. */
public final class PlacementPlan {
    private final BlockPosition origin;
    private final int maxBlocks;
    private final int maxRadius;
    private final Map<BlockPosition, Change> changes = new LinkedHashMap<BlockPosition, Change>();
    private String failure;

    public PlacementPlan(BlockPosition origin, int maxBlocks, int maxRadius) {
        this.origin = origin;
        this.maxBlocks = maxBlocks;
        this.maxRadius = maxRadius;
    }

    public boolean setBlock(int x, int y, int z, int blockId, int metadata) {
        return setBlock(x, y, z, blockId, metadata, null);
    }

    public boolean setBlock(int x, int y, int z, int blockId, int metadata, Map<String, LuaValue> tileData) {
        if (failure != null) {
            return false;
        }
        if (y < WorldGenLimits.MIN_HEIGHT || y > WorldGenLimits.MAX_HEIGHT) {
            failure = FeatureResult.OUT_OF_BOUNDS;
            return false;
        }
        if (Math.abs(x - origin.x) > maxRadius || Math.abs(y - origin.y) > maxRadius
                || Math.abs(z - origin.z) > maxRadius) {
            failure = FeatureResult.OUT_OF_BOUNDS;
            return false;
        }
        if (blockId < 0 || blockId > 255 || metadata < 0 || metadata > 15) {
            failure = FeatureResult.OUT_OF_BOUNDS;
            return false;
        }
        BlockPosition position = new BlockPosition(x, y, z);
        if (!changes.containsKey(position) && changes.size() >= maxBlocks) {
            failure = FeatureResult.BUDGET_EXCEEDED;
            return false;
        }
        if (tileData != null && !tileData.isEmpty() && !TileEntityRegistry.hasBlock(blockId)) {
            failure = FeatureResult.BLOCKED;
            return false;
        }
        if (tileData != null && !tileData.isEmpty()) {
            try {
                LuaTileEntity probe = TileEntityRegistry.createForBlock(blockId);
                for (Map.Entry<String, LuaValue> value : tileData.entrySet()) {
                    probe.setDataLua(value.getKey(), value.getValue());
                }
            } catch (RuntimeException error) {
                failure = FeatureResult.BLOCKED;
                return false;
            }
        }
        changes.put(position, new Change(position, blockId, metadata, tileData));
        return true;
    }

    public String failure() {
        return failure;
    }

    public int size() {
        return changes.size();
    }

    public boolean contains(int x, int y, int z) {
        return changes.containsKey(new BlockPosition(x, y, z));
    }

    public FeatureResult commit(FeatureContext context) {
        if (failure != null) {
            return FeatureResult.rejected(failure);
        }
        if (context.failure() != null) {
            return FeatureResult.rejected(context.failure());
        }
        if (changes.isEmpty()) {
            return FeatureResult.rejected(FeatureResult.NO_CHANGES);
        }

        World world = context.world();
        List<Original> originals = new ArrayList<Original>();
        BlockPosition min = null;
        BlockPosition max = null;
        for (Change change : changes.values()) {
            if (!world.blockExists(change.position.x, change.position.y, change.position.z)) {
                return FeatureResult.rejected(FeatureResult.UNLOADED_CHUNK);
            }
            originals.add(new Original(change.position, world.getBlockId(change.position.x, change.position.y,
                    change.position.z), world.getBlockMetadata(change.position.x, change.position.y,
                            change.position.z)));
            min = min(min, change.position);
            max = max(max, change.position);
        }

        int committed = 0;
        for (Change change : changes.values()) {
            if (!world.setBlockAndMetadata(change.position.x, change.position.y, change.position.z, change.blockId,
                    change.metadata)) {
                rollback(world, originals, committed);
                return FeatureResult.rejected(FeatureResult.BLOCKED);
            }
            committed++;
        }
        try {
            for (Change change : changes.values()) {
                if (change.tileData.isEmpty()) {
                    continue;
                }
                TileEntity tile = world.getBlockTileEntity(change.position.x, change.position.y,
                        change.position.z);
                if (!(tile instanceof LuaTileEntity)) {
                    rollback(world, originals, committed);
                    return FeatureResult.rejected(FeatureResult.BLOCKED);
                }
                for (Map.Entry<String, LuaValue> value : change.tileData.entrySet()) {
                    ((LuaTileEntity) tile).setDataLua(value.getKey(), value.getValue());
                }
            }
        } catch (RuntimeException error) {
            rollback(world, originals, committed);
            return FeatureResult.rejected(FeatureResult.BLOCKED);
        }
        return FeatureResult.placed(committed, min, max);
    }

    /** Validates the complete plan and reports its real bounds without mutating the world. */
    public FeatureResult preview(FeatureContext context) {
        if (failure != null) {
            return FeatureResult.rejected(failure);
        }
        if (context.failure() != null) {
            return FeatureResult.rejected(context.failure());
        }
        if (changes.isEmpty()) {
            return FeatureResult.rejected(FeatureResult.NO_CHANGES);
        }
        BlockPosition min = null;
        BlockPosition max = null;
        for (Change change : changes.values()) {
            if (!context.world().blockExists(change.position.x, change.position.y, change.position.z)) {
                return FeatureResult.rejected(FeatureResult.UNLOADED_CHUNK);
            }
            min = min(min, change.position);
            max = max(max, change.position);
        }
        return FeatureResult.placed(changes.size(), min, max);
    }

    private static void rollback(World world, List<Original> originals, int committed) {
        for (int index = committed - 1; index >= 0; index--) {
            Original original = originals.get(index);
            world.setBlockAndMetadata(original.position.x, original.position.y, original.position.z, original.blockId,
                    original.metadata);
        }
    }

    private static BlockPosition min(BlockPosition left, BlockPosition right) {
        return left == null ? right : new BlockPosition(Math.min(left.x, right.x), Math.min(left.y, right.y),
                Math.min(left.z, right.z));
    }

    private static BlockPosition max(BlockPosition left, BlockPosition right) {
        return left == null ? right : new BlockPosition(Math.max(left.x, right.x), Math.max(left.y, right.y),
                Math.max(left.z, right.z));
    }

    private static final class Change {
        private final BlockPosition position;
        private final int blockId;
        private final int metadata;
        private final Map<String, LuaValue> tileData;

        private Change(BlockPosition position, int blockId, int metadata, Map<String, LuaValue> tileData) {
            this.position = position;
            this.blockId = blockId;
            this.metadata = metadata;
            this.tileData = tileData == null ? java.util.Collections.<String, LuaValue>emptyMap()
                    : java.util.Collections.unmodifiableMap(new LinkedHashMap<String, LuaValue>(tileData));
        }
    }

    private static final class Original {
        private final BlockPosition position;
        private final int blockId;
        private final int metadata;

        private Original(BlockPosition position, int blockId, int metadata) {
            this.position = position;
            this.blockId = blockId;
            this.metadata = metadata;
        }
    }
}
