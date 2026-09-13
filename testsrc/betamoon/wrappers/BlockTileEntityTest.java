package betamoon.wrappers;

import betamoon.tileentity.LuaTileEntity;
import betamoon.tileentity.TileEntityDefinition;
import betamoon.tileentity.TileEntityRegistry;
import java.util.HashMap;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.Material;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.LuaValue;

/**
 * Exercises vanilla chunk acceptance and lazy creation without starting the
 * client.
 */
public final class BlockTileEntityTest {
    public static void main(String[] args) {
        int id = 255;
        while (id > 0 && Block.blocksList[id] != null) {
            id--;
        }
        if (id == 0) {
            throw new AssertionError("No free test block ID");
        }
        BlockWrapper block = new BlockWrapper(id, 0, Material.rock, "tile_entity_test");
        TestWorld world = new TestWorld();
        world.chunk.blocks[64] = (byte) id;

        require(!Block.isBlockContainer[id], "Ordinary blocks must not enable tile entities");
        block.onBlockAdded(world, 0, 64, 0);
        require(world.getBlockTileEntity(0, 64, 0) == null, "Ordinary block created a tile entity");
        require(world.loadedTileEntityList.isEmpty(), "Ordinary block added a ticking tile entity");

        TileEntityDefinition definition = new TileEntityDefinition("test", "test", "test", new HashMap(), new HashMap(),
                LuaValue.NIL, LuaValue.NIL, 0, 0, false, 0);
        TileEntityRegistry.attachBlock(id, definition, null, null, null);
        block.enableTileEntity();
        block.onBlockAdded(world, 0, 64, 0);
        TileEntity placed = world.getBlockTileEntity(0, 64, 0);
        require(placed instanceof LuaTileEntity, "Chunk rejected the placed Lua tile entity");
        require(placed.worldObj == world && placed.xCoord == 0 && placed.yCoord == 64 && placed.zCoord == 0,
                "Chunk did not initialize tile entity location");
        require(world.loadedTileEntityList.size() == 1, "Placement created duplicate tile entities");

        world.removeBlockTileEntity(0, 64, 0);
        require(world.chunk.chunkTileEntityMap.isEmpty(), "Tile entity removal failed");
        // The renderer uses this path when a chunk has no tile entity cached yet.
        TileEntity recreated = world.chunk.getChunkBlockTileEntity(0, 64, 0);
        require(recreated instanceof LuaTileEntity && recreated != placed,
                "Chunk could not lazily recreate the Lua tile entity");
        require(world.loadedTileEntityList.size() == 1, "Lazy creation did not register exactly one entity");

        block.onBlockRemoval(world, 0, 64, 0);
        require(world.chunk.chunkTileEntityMap.isEmpty() && world.loadedTileEntityList.isEmpty(),
                "Block removal left a tile entity behind");
        System.out.println("Custom block tile entity placement, lookup, and removal checks passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * Uses real World tile entity methods with one in-memory chunk and no save
     * directory.
     */
    private static final class TestWorld extends World {
        private final Chunk chunk;

        TestWorld() {
            super(null, "tile_entity_test", new WorldProvider() {
            }, 0L);
            chunk = new Chunk(this, new byte[32768], 0, 0);
            chunk.isChunkLoaded = true;
        }

        protected IChunkProvider getChunkProvider() {
            return null;
        }

        public Chunk getChunkFromChunkCoords(int x, int z) {
            return chunk;
        }
    }
}
