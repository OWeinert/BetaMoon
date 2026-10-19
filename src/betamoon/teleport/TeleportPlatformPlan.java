package betamoon.teleport;

import betamoon.multiblock.ControllerlessMultiblockRuntime;
import betamoon.worldgen.WorldGenRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Block;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.Material;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagList;
import net.minecraft.src.World;

/**
 * Validate the whole non-destructive plan; persist its journal before applying
 * it.
 */
public final class TeleportPlatformPlan {
    private final List<Cell> cells;

    private TeleportPlatformPlan(List<Cell> cells) {
        this.cells = cells;
    }

    public static TeleportPlatformPlan plan(World world, EntityPlayer player, TeleportLocation suggestion,
            TeleportPlatformDefinition platform) throws IOException {
        int centerX = (int) Math.floor(suggestion.x);
        int centerZ = (int) Math.floor(suggestion.z);
        int radius = platform.width / 2;
        AxisAlignedBB volume = AxisAlignedBB.getBoundingBoxFromPool(centerX - radius, platform.y - platform.depth + 1,
                centerZ - radius, centerX + radius + 1, platform.y + platform.clearHeight + 1, centerZ + radius + 1);
        if (!ArrivalInspector.loaded(world, volume)) {
            throw new IOException("chunks_unavailable");
        }
        if (!world.getEntitiesWithinAABBExcludingEntity(player, volume).isEmpty()) {
            throw new IOException("unsafe_destination: platform would overlap an entity");
        }
        List<Cell> cells = new ArrayList<Cell>();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                for (int y = platform.y - platform.depth + 1; y <= platform.y + platform.clearHeight; y++) {
                    int oldId = world.getBlockId(x, y, z);
                    int oldMetadata = world.getBlockMetadata(x, y, z);
                    int newId = y <= platform.y ? platform.block.id : 0;
                    int newMetadata = y <= platform.y ? platform.block.metadata : 0;
                    if (oldId == newId && oldMetadata == newMetadata) {
                        continue;
                    }
                    Block old = Block.blocksList[oldId];
                    if (oldId == Block.bedrock.blockID || Block.isBlockContainer[oldId]
                            || ArrivalInspector.hazard(oldId)
                            || old != null && old.blockMaterial != Material.plants && old.blockMaterial != Material.snow
                            || WorldGenRegistry.reservedStructurePosition(world, x, y, z)
                            || !ControllerlessMultiblockRuntime.at(world, x, y, z, null).isEmpty()) {
                        throw new IOException(
                                "unsafe_destination: platform would replace protected or occupied terrain");
                    }
                    cells.add(new Cell(x, y, z, oldId, oldMetadata, newId, newMetadata));
                }
            }
        }
        return new TeleportPlatformPlan(cells);
    }

    public NBTTagList write() {
        NBTTagList saved = new NBTTagList();
        for (Cell cell : cells) {
            saved.setTag(cell.write());
        }
        return saved;
    }

    /**
     * Multiple explicit fallbacks share one write-ahead record and one total edit
     * budget.
     */
    public TeleportPlatformPlan combine(TeleportPlatformPlan next) throws IOException {
        Map<String, Cell> combined = new LinkedHashMap<String, Cell>();
        for (Cell cell : cells) {
            combined.put(cell.x + ":" + cell.y + ":" + cell.z, cell);
        }
        for (Cell cell : next.cells) {
            String key = cell.x + ":" + cell.y + ":" + cell.z;
            Cell previous = combined.get(key);
            if (previous != null) {
                if (previous.newId != cell.oldId || previous.newMetadata != cell.oldMetadata) {
                    throw new IOException("unsafe_destination: explicit platform plans conflict");
                }
                cell = new Cell(cell.x, cell.y, cell.z, previous.oldId, previous.oldMetadata, cell.newId,
                        cell.newMetadata);
            }
            combined.put(key, cell);
        }
        if (combined.size() > 512) {
            throw new IOException("budget_exhausted: combined platforms exceed 512 edits");
        }
        return new TeleportPlatformPlan(new ArrayList<Cell>(combined.values()));
    }

    public void apply(World world) throws IOException {
        for (Cell cell : cells) {
            if (!cell.matches(world, false)
                    || !world.setBlockAndMetadata(cell.x, cell.y, cell.z, cell.newId, cell.newMetadata)) {
                throw new IOException("unsafe_destination: platform terrain changed during preparation");
            }
        }
        for (Cell cell : cells) {
            if (!cell.matches(world, true)) {
                throw new IOException("unsafe_destination: platform was changed by a block callback");
            }
        }
    }

    /**
     * Intervening edits are preserved; only cells which still equal our planned new
     * state are restored.
     */
    public void rollback(World world) throws IOException {
        for (int index = cells.size() - 1; index >= 0; index--) {
            Cell cell = cells.get(index);
            if (cell.matches(world, true)
                    && !world.setBlockAndMetadata(cell.x, cell.y, cell.z, cell.oldId, cell.oldMetadata)) {
                throw new IOException("save_failed: platform rollback could not restore a cell");
            }
        }
    }

    public static TeleportPlatformPlan read(NBTTagList saved) throws IOException {
        if (saved.tagCount() > 512) {
            throw new IOException("Invalid saved platform cell budget");
        }
        List<Cell> cells = new ArrayList<Cell>();
        Set<String> positions = new HashSet<String>();
        for (int index = 0; index < saved.tagCount(); index++) {
            if (!(saved.tagAt(index) instanceof NBTTagCompound)) {
                throw new IOException("Invalid saved platform cell");
            }
            NBTTagCompound tag = (NBTTagCompound) saved.tagAt(index);
            int x = tag.getInteger("X");
            int y = tag.getInteger("Y");
            int z = tag.getInteger("Z");
            int oldId = tag.getInteger("OldId");
            int oldMetadata = tag.getInteger("OldMetadata");
            int newId = tag.getInteger("NewId");
            int newMetadata = tag.getInteger("NewMetadata");
            if (Math.abs((long) x) >= 30000000 || Math.abs((long) z) >= 30000000 || y < 0 || y > 127 || oldId < 0
                    || oldId > 255 || newId < 0 || newId > 255 || oldMetadata < 0 || oldMetadata > 15 || newMetadata < 0
                    || newMetadata > 15 || !positions.add(x + ":" + y + ":" + z)) {
                throw new IOException("Invalid saved platform cell coordinates/state");
            }
            cells.add(new Cell(x, y, z, oldId, oldMetadata, newId, newMetadata));
        }
        return new TeleportPlatformPlan(cells);
    }

    private static final class Cell {
        private final int x;
        private final int y;
        private final int z;
        private final int oldId;
        private final int oldMetadata;
        private final int newId;
        private final int newMetadata;

        private Cell(int x, int y, int z, int oldId, int oldMetadata, int newId, int newMetadata) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.oldId = oldId;
            this.oldMetadata = oldMetadata;
            this.newId = newId;
            this.newMetadata = newMetadata;
        }

        private boolean matches(World world, boolean after) throws IOException {
            if (!world.getIChunkProvider().chunkExists(x >> 4, z >> 4)) {
                throw new IOException("chunks_unavailable");
            }
            return world.getBlockId(x, y, z) == (after ? newId : oldId)
                    && world.getBlockMetadata(x, y, z) == (after ? newMetadata : oldMetadata);
        }

        private NBTTagCompound write() {
            NBTTagCompound saved = new NBTTagCompound();
            saved.setInteger("X", x);
            saved.setInteger("Y", y);
            saved.setInteger("Z", z);
            saved.setInteger("OldId", oldId);
            saved.setInteger("OldMetadata", oldMetadata);
            saved.setInteger("NewId", newId);
            saved.setInteger("NewMetadata", newMetadata);
            return saved;
        }
    }
}
