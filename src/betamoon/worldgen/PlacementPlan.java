package betamoon.worldgen;

import betamoon.loot.InventoryLootOperation;
import betamoon.tileentity.LuaTileEntity;
import betamoon.tileentity.TileEntityRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.IInventory;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import org.luaj.vm2.LuaValue;

/** Bounded transactional block changes for one local feature. */
public final class PlacementPlan {
    public enum WritePriority {
        EXCAVATION, TERRAIN_ADAPTATION, STRUCTURE
    }

    private final BlockPosition origin;
    private final int maxBlocks;
    private final int maxRadius;
    private final Map<BlockPosition, Change> changes = new LinkedHashMap<BlockPosition, Change>();
    private final Map<BlockPosition, InventoryLootOperation> inventoryLoot =
            new LinkedHashMap<BlockPosition, InventoryLootOperation>();
    private String failure;

    public PlacementPlan(BlockPosition origin, int maxBlocks, int maxRadius) {
        this.origin = origin;
        this.maxBlocks = maxBlocks;
        this.maxRadius = maxRadius;
    }

    public boolean setBlock(int x, int y, int z, int blockId, int metadata) {
        return setBlock(x, y, z, blockId, metadata, null, WritePriority.STRUCTURE);
    }

    public boolean setBlock(int x, int y, int z, int blockId, int metadata, WritePriority priority) {
        return setBlock(x, y, z, blockId, metadata, null, priority);
    }

    public boolean setBlock(int x, int y, int z, int blockId, int metadata, Map<String, LuaValue> tileData) {
        return setBlock(x, y, z, blockId, metadata, tileData, WritePriority.STRUCTURE);
    }

    public boolean setBlock(int x, int y, int z, int blockId, int metadata, Map<String, LuaValue> tileData,
            WritePriority priority) {
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
        Change previous = changes.get(position);
        if (previous != null && previous.priority.ordinal() > priority.ordinal()) {
            return true;
        }
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
        changes.put(position, new Change(position, blockId, metadata, tileData, priority));
        return true;
    }

    public String failure() {
        return failure;
    }

    public boolean addInventoryLoot(int x, int y, int z, InventoryLootOperation operation) {
        if (failure != null) {
            return false;
        }
        if (operation == null || y < WorldGenLimits.MIN_HEIGHT || y > WorldGenLimits.MAX_HEIGHT
                || Math.abs(x - origin.x) > maxRadius || Math.abs(y - origin.y) > maxRadius
                || Math.abs(z - origin.z) > maxRadius) {
            failure = FeatureResult.OUT_OF_BOUNDS;
            return false;
        }
        BlockPosition position = new BlockPosition(x, y, z);
        if (inventoryLoot.containsKey(position)) {
            failure = FeatureResult.BLOCKED;
            return false;
        }
        inventoryLoot.put(position, operation);
        return true;
    }

    public int inventoryOperationCount() {
        return inventoryLoot.size();
    }

    public boolean hasPlannedChanges() {
        return !changes.isEmpty() || !inventoryLoot.isEmpty();
    }

    public int size() {
        return changes.size();
    }

    public boolean contains(int x, int y, int z) {
        return changes.containsKey(new BlockPosition(x, y, z));
    }

    public boolean hasPriority(BlockPosition position, WritePriority priority) {
        Change change = changes.get(position);
        return change != null && change.priority == priority;
    }

    public List<PlannedBlock> plannedBlocks() {
        List<PlannedBlock> result = new ArrayList<PlannedBlock>();
        for (Change change : changes.values()) {
            result.add(new PlannedBlock(change.position, change.blockId, change.metadata));
        }
        return Collections.unmodifiableList(result);
    }

    public FeatureResult commit(FeatureContext context) {
        return commit(context, false);
    }

    /**
     * Commits a runtime placement, then publishes render and neighbor updates as
     * one completed change.
     */
    public FeatureResult commitWithUpdates(FeatureContext context) {
        return commit(context, true);
    }

    private FeatureResult commit(FeatureContext context, boolean publishUpdates) {
        if (failure != null) {
            return FeatureResult.rejected(failure);
        }
        if (context.failure() != null) {
            return FeatureResult.rejected(context.failure());
        }
        if (changes.isEmpty() && inventoryLoot.isEmpty()) {
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
            originals.add(new Original(change.position,
                    world.getBlockId(change.position.x, change.position.y, change.position.z),
                    world.getBlockMetadata(change.position.x, change.position.y, change.position.z),
                    tileData(world, change.position)));
            min = min(min, change.position);
            max = max(max, change.position);
        }
        for (BlockPosition position : inventoryLoot.keySet()) {
            if (!world.blockExists(position.x, position.y, position.z)) {
                return FeatureResult.rejected(FeatureResult.UNLOADED_CHUNK);
            }
            min = min(min, position);
            max = max(max, position);
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
                TileEntity tile = world.getBlockTileEntity(change.position.x, change.position.y, change.position.z);
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
        List<InventoryOriginal> inventoryOriginals = new ArrayList<InventoryOriginal>();
        try {
            for (BlockPosition position : inventoryLoot.keySet()) {
                TileEntity tile = world.getBlockTileEntity(position.x, position.y, position.z);
                if (!(tile instanceof IInventory)) {
                    restoreInventories(inventoryOriginals);
                    rollback(world, originals, committed);
                    return FeatureResult.rejected(FeatureResult.LOOT_TARGET_MISSING);
                }
                inventoryOriginals.add(new InventoryOriginal((IInventory) tile));
            }
            int index = 0;
            for (InventoryLootOperation operation : inventoryLoot.values()) {
                InventoryLootOperation.Result result = operation.apply(inventoryOriginals.get(index++).inventory);
                if (result != InventoryLootOperation.Result.APPLIED) {
                    restoreInventories(inventoryOriginals);
                    rollback(world, originals, committed);
                    return FeatureResult.rejected(lootFailure(result));
                }
            }
        } catch (RuntimeException error) {
            restoreInventories(inventoryOriginals);
            rollback(world, originals, committed);
            return FeatureResult.rejected(FeatureResult.LOOT_MUTATION_FAILED);
        }
        if (publishUpdates) {
            publishUpdates(world);
        }
        return FeatureResult.placed(committed, min, max, context.diagnostics());
    }

    /**
     * Validates the complete plan and reports its real bounds without mutating the
     * world.
     */
    public FeatureResult preview(FeatureContext context) {
        if (failure != null) {
            return FeatureResult.rejected(failure);
        }
        if (context.failure() != null) {
            return FeatureResult.rejected(context.failure());
        }
        if (changes.isEmpty() && inventoryLoot.isEmpty()) {
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
        for (BlockPosition position : inventoryLoot.keySet()) {
            if (!context.world().blockExists(position.x, position.y, position.z)) {
                return FeatureResult.rejected(FeatureResult.UNLOADED_CHUNK);
            }
            min = min(min, position);
            max = max(max, position);
        }
        return FeatureResult.placed(changes.size(), min, max, context.diagnostics());
    }

    private static void rollback(World world, List<Original> originals, int committed) {
        for (int index = committed - 1; index >= 0; index--) {
            Original original = originals.get(index);
            world.setBlockAndMetadata(original.position.x, original.position.y, original.position.z, original.blockId,
                    original.metadata);
            if (original.tileData != null) {
                TileEntity tile = TileEntity.createAndLoadEntity(original.tileData);
                if (tile != null) {
                    world.setBlockTileEntity(original.position.x, original.position.y, original.position.z, tile);
                }
            }
        }
    }

    private static NBTTagCompound tileData(World world, BlockPosition position) {
        TileEntity tile = world.getBlockTileEntity(position.x, position.y, position.z);
        if (tile == null) {
            return null;
        }
        try {
            NBTTagCompound data = new NBTTagCompound();
            tile.writeToNBT(data);
            return data;
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static void restoreInventories(List<InventoryOriginal> originals) {
        for (InventoryOriginal original : originals) {
            for (int slot = 0; slot < original.contents.length; slot++) {
                original.inventory.setInventorySlotContents(slot, copy(original.contents[slot]));
            }
            original.inventory.onInventoryChanged();
        }
    }

    private static String lootFailure(InventoryLootOperation.Result result) {
        if (result == InventoryLootOperation.Result.INCOMPATIBLE_SIZE) {
            return FeatureResult.LOOT_INVENTORY_SIZE;
        }
        if (result == InventoryLootOperation.Result.EXISTING_CONTENTS) {
            return FeatureResult.LOOT_EXISTING_CONTENTS;
        }
        if (result == InventoryLootOperation.Result.OVERFLOW) {
            return FeatureResult.LOOT_OVERFLOW;
        }
        return FeatureResult.LOOT_MUTATION_FAILED;
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : new ItemStack(stack.itemID, stack.stackSize, stack.getItemDamage());
    }

    private void publishUpdates(World world) {
        for (Change change : changes.values()) {
            world.markBlockNeedsUpdate(change.position.x, change.position.y, change.position.z);
            world.notifyBlocksOfNeighborChange(change.position.x, change.position.y, change.position.z, change.blockId);
        }
    }

    private static BlockPosition min(BlockPosition left, BlockPosition right) {
        return left == null
                ? right
                : new BlockPosition(Math.min(left.x, right.x), Math.min(left.y, right.y), Math.min(left.z, right.z));
    }

    private static BlockPosition max(BlockPosition left, BlockPosition right) {
        return left == null
                ? right
                : new BlockPosition(Math.max(left.x, right.x), Math.max(left.y, right.y), Math.max(left.z, right.z));
    }

    private static final class Change {
        private final BlockPosition position;
        private final int blockId;
        private final int metadata;
        private final Map<String, LuaValue> tileData;
        private final WritePriority priority;

        private Change(BlockPosition position, int blockId, int metadata, Map<String, LuaValue> tileData,
                WritePriority priority) {
            this.position = position;
            this.blockId = blockId;
            this.metadata = metadata;
            this.tileData = tileData == null
                    ? java.util.Collections.<String, LuaValue>emptyMap()
                    : java.util.Collections.unmodifiableMap(new LinkedHashMap<String, LuaValue>(tileData));
            this.priority = priority;
        }
    }

    private static final class Original {
        private final BlockPosition position;
        private final int blockId;
        private final int metadata;
        private final NBTTagCompound tileData;

        private Original(BlockPosition position, int blockId, int metadata, NBTTagCompound tileData) {
            this.position = position;
            this.blockId = blockId;
            this.metadata = metadata;
            this.tileData = tileData;
        }
    }

    private static final class InventoryOriginal {
        private final IInventory inventory;
        private final ItemStack[] contents;

        private InventoryOriginal(IInventory inventory) {
            this.inventory = inventory;
            contents = new ItemStack[inventory.getSizeInventory()];
            for (int slot = 0; slot < contents.length; slot++) {
                contents[slot] = copy(inventory.getStackInSlot(slot));
            }
        }
    }

    public static final class PlannedBlock {
        public final BlockPosition position;
        public final int blockId;
        public final int metadata;

        private PlannedBlock(BlockPosition position, int blockId, int metadata) {
            this.position = position;
            this.blockId = blockId;
            this.metadata = metadata;
        }
    }
}
