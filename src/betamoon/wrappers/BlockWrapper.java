package betamoon.wrappers;

import betamoon.luaapi.block.BlockTickRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.EntityItem;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.Material;
import net.minecraft.src.StepSound;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import net.minecraft.src.IBlockAccess;
import betamoon.tileentity.LuaTileEntity;
import betamoon.tileentity.TileEntityRegistry;

public class BlockWrapper extends Block {
    private static final List<PendingDrop> PENDING_DROPS = new ArrayList<PendingDrop>();
    private final List<CustomDrop> customDrops = new ArrayList<CustomDrop>();
    private final int[] sideTextures = new int[6];
    private final boolean[] sideTextureSet = new boolean[6];

    private static final class PendingDrop {
        private final int blockId;
        private final int itemId;

        private PendingDrop(int blockId, int itemId) {
            this.blockId = blockId;
            this.itemId = itemId;
        }
    }

    private static final class CustomDrop {
        private final int itemId;
        private final int minQuantity;
        private final int maxQuantity;

        private CustomDrop(int itemId, int minQuantity, int maxQuantity) {
            this.itemId = itemId;
            this.minQuantity = minQuantity;
            this.maxQuantity = maxQuantity;
        }
    }

    /**
     * Creates a block wrapper with the provided block id, texture index, material, and internal name.
     *
     * @param id numeric block id
     * @param textureId terrain texture index
     * @param material base material for the block
     * @param name internal block name (unlocalized)
     */
    public BlockWrapper(int id, int textureId, Material material, String name) {
        super(id, textureId, material);
        this.setBlockName(name);
    }

    /**
     * Sets a specific texture index for a block face.
     *
     * @param side side index (0-5)
     * @param textureIndex texture index in the terrain atlas
     * @return this wrapper for chaining
     */
    public BlockWrapper setSideTextureIndex(int side, int textureIndex) {
        if (side >= 0 && side < sideTextures.length) {
            sideTextures[side] = textureIndex;
            sideTextureSet[side] = true;
        }
        return this;
    }

    /**
     * Sets the same texture index for all block faces.
     *
     * @param textureIndex texture index in the terrain atlas
     * @return this wrapper for chaining
     */
    public BlockWrapper setAllSideTextures(int textureIndex) {
        for (int i = 0; i < sideTextures.length; i++) {
            sideTextures[i] = textureIndex;
            sideTextureSet[i] = true;
        }
        return this;
    }

    /**
     * Sets the block hardness used for break speed.
     *
     * @param hardness hardness value
     * @return this wrapper for chaining
     */
    public BlockWrapper setHardness(float hardness) {
        this.blockHardness = hardness;
        return this;
    }

    /**
     * Sets the explosion resistance for the block.
     *
     * @param resistance resistance value
     * @return this wrapper for chaining
     */
    public BlockWrapper setResistance(float resistance) {
        this.blockResistance = resistance;
        return this;
    }
    
    /**
     * Sets the block's light emission value.
     *
     * @param lightValue light value to assign
     * @return this wrapper for chaining
     */
    public BlockWrapper setLightValue(int lightValue) {
        Block.lightValue[this.blockID] = lightValue;
        return this;
    }

    /**
     * Sets the block's light opacity value.
     *
     * @param lightOpacity opacity value to assign
     * @return this wrapper for chaining
     */
    public BlockWrapper setLightOpacity(int lightOpacity) {
        Block.lightOpacity[this.blockID] = lightOpacity;
        return this;
    }

    /**
     * Sets the step sound used when walking on the block.
     *
     * @param sound step sound instance
     * @return this wrapper for chaining
     */
    public BlockWrapper setStepSound(StepSound sound) {
        this.stepSound = sound;
        return this;
    }

    /**
     * Marks the block as unbreakable by setting hardness to -1.
     *
     * @return this wrapper for chaining
     */
    public BlockWrapper setBlockUnbreakable() {
        this.setHardness(-1.0F);
		return this;
    }

    /** Enables or disables Minecraft's random update selection for this block. */
    public BlockWrapper setRandomTicks(boolean enabled) {
        this.setTickOnLoad(enabled);
        return this;
    }

    /** Starts any configured scheduled updates after this block is placed. */
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        LuaTileEntity entity = TileEntityRegistry.createForBlock(this.blockID);
        if (entity != null) world.setBlockTileEntity(x, y, z, entity);
        BlockTickRegistry.onBlockAdded(this, world, x, y, z);
    }

    /** Opens an attached standalone Lua container and GUI. */
    public boolean blockActivated(World world, int x, int y, int z, EntityPlayer player) {
        TileEntity entity = world.getBlockTileEntity(x, y, z);
        return entity instanceof LuaTileEntity && TileEntityRegistry.open(player, (LuaTileEntity) entity);
    }

    public void onNeighborBlockChange(World world, int x, int y, int z, int neighborId) {
        super.onNeighborBlockChange(world, x, y, z, neighborId);
        TileEntityRegistry.neighborChanged(world, x, y, z, blockID, neighborId);
    }

    public boolean canProvidePower() {
        return TileEntityRegistry.providesPower(blockID);
    }

    public boolean isPoweringTo(IBlockAccess world, int x, int y, int z, int side) {
        return TileEntityRegistry.power(world, x, y, z, blockID, false) > 0;
    }

    public boolean isIndirectlyPoweringTo(World world, int x, int y, int z, int side) {
        return TileEntityRegistry.power(world, x, y, z, blockID, true) > 0;
    }

    /** Drops stored items and removes an attached Lua tile entity. */
    public void onBlockRemoval(World world, int x, int y, int z) {
        TileEntity entity = world.getBlockTileEntity(x, y, z);
        if (entity instanceof LuaTileEntity && !world.multiplayerWorld) {
            LuaTileEntity lua = (LuaTileEntity) entity;
            for (int slot = 0; slot < lua.getSizeInventory(); slot++) {
                ItemStack stack = lua.getStackInSlot(slot);
                if (stack == null) continue;
                float ox = world.rand.nextFloat() * 0.8F + 0.1F;
                float oy = world.rand.nextFloat() * 0.8F + 0.1F;
                float oz = world.rand.nextFloat() * 0.8F + 0.1F;
                EntityItem dropped = new EntityItem(world, x + ox, y + oy, z + oz, stack.copy());
                world.entityJoinedWorld(dropped);
            }
        }
        world.removeBlockTileEntity(x, y, z);
        super.onBlockRemoval(world, x, y, z);
    }

    /** Marks this block ID as containing a tile entity in Minecraft's chunk format. */
    public void enableTileEntity() {
        Block.isBlockContainer[this.blockID] = true;
    }

    /** Delegates Minecraft gameplay updates to the active script-owned definition. */
    public void updateTick(World world, int x, int y, int z, Random random) {
        BlockTickRegistry.update(this, world, x, y, z, random);
    }

    /** Delegates nearby client display updates to the active script-owned definition. */
    public void randomDisplayTick(World world, int x, int y, int z, Random random) {
        BlockTickRegistry.display(this, world, x, y, z, random);
    }

    /**
     * Adds a custom drop definition for this block.
     *
     * @param itemId item id to drop (block id or item shifted index)
     * @param minQuantity minimum quantity to drop
     * @param maxQuantity maximum quantity to drop
     * @return this wrapper for chaining
     */
    public BlockWrapper addCustomDrop(int itemId, int minQuantity, int maxQuantity) {
        customDrops.add(new CustomDrop(itemId, minQuantity, maxQuantity));
        PENDING_DROPS.add(new PendingDrop(this.blockID, itemId));
        return this;
    }

    public void dropBlockAsItemWithChance(World world, int x, int y, int z, int metadata, float chance) {
        if (!customDrops.isEmpty()) {
            if (!world.multiplayerWorld) {
                Random rand = world.rand;
                for (int i = 0; i < customDrops.size(); i++) {
                    CustomDrop drop = customDrops.get(i);
                    if (rand.nextFloat() <= chance) {
                        int qty = drop.minQuantity;
                        if (drop.maxQuantity > drop.minQuantity) {
                            qty = drop.minQuantity + rand.nextInt(drop.maxQuantity - drop.minQuantity + 1);
                        }
                        if (qty > 0) {
                            dropBlockAsItem_do(world, x, y, z, new ItemStack(drop.itemId, qty, 0));
                        }
                    }
                }
            }
            return;
        }
        super.dropBlockAsItemWithChance(world, x, y, z, metadata, chance);
    }

    public int getBlockTextureFromSide(int side) {
        if (side >= 0 && side < sideTextures.length && sideTextureSet[side]) {
            return sideTextures[side];
        }
        return this.blockIndexInTexture;
    }

    public static void validatePendingDrops(List errors) {
        for (int i = 0; i < PENDING_DROPS.size(); i++) {
            PendingDrop drop = PENDING_DROPS.get(i);
            int id = drop.itemId;
            boolean validBlock = id >= 0 && id < Block.blocksList.length && Block.blocksList[id] != null;
            boolean validItem = id >= 0 && id < Item.itemsList.length && Item.itemsList[id] != null;
            if (!validBlock && !validItem) {
                errors.add("Custom drop id not registered (block " + drop.blockId + "): " + id);
            }
        }
    }
}
