package betamoon.luaapi.block;

import betamoon.luaapi.utils.InteractionOutcome;
import betamoon.networking.LogicalNetworkRuntime;
import betamoon.tileentity.LuaTileEntity;
import betamoon.tileentity.TileEntityRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Block;
import net.minecraft.src.BlockContainer;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityItem;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IBlockAccess;
import net.minecraft.src.ItemStack;
import net.minecraft.src.Material;
import net.minecraft.src.MovingObjectPosition;
import net.minecraft.src.StepSound;
import net.minecraft.src.TileEntity;
import net.minecraft.src.Vec3D;
import net.minecraft.src.World;
import org.luaj.vm2.LuaValue;

public class LuaBlock extends BlockContainer implements forge.IConnectRedstone {
    private final int[] sideTextures = new int[6];
    private final boolean[] sideTextureSet = new boolean[6];

    /**
     * Creates a block implementation with the provided block id, texture index,
     * material, and internal name.
     *
     * @param id
     *            numeric block id
     * @param textureId
     *            terrain texture index
     * @param material
     *            base material for the block
     * @param name
     *            internal block name (unlocalized)
     */
    public LuaBlock(int id, int textureId, Material material, String name) {
        super(id, textureId, material);
        // Tile entities are opt-in, but Chunk requires their blocks to extend
        // BlockContainer.
        Block.isBlockContainer[id] = false;
        // Despite its mapped name, this enables render updates as well as neighbor
        // notifications when World.setBlockMetadataWithNotify changes our state.
        this.disableNeighborNotifyOnMetadataChange();
        this.setBlockName(name);
    }

    /**
     * Sets a specific texture index for a block face.
     *
     * @param side
     *            side index (0-5)
     * @param textureIndex
     *            texture index in the terrain atlas
     * @return this instance for chaining
     */
    public LuaBlock setSideTextureIndex(int side, int textureIndex) {
        if (side >= 0 && side < sideTextures.length) {
            sideTextures[side] = textureIndex;
            sideTextureSet[side] = true;
        }
        return this;
    }

    /**
     * Sets the same texture index for all block faces.
     *
     * @param textureIndex
     *            texture index in the terrain atlas
     * @return this instance for chaining
     */
    public LuaBlock setAllSideTextures(int textureIndex) {
        for (int i = 0; i < sideTextures.length; i++) {
            sideTextures[i] = textureIndex;
            sideTextureSet[i] = true;
        }
        return this;
    }

    /**
     * Sets the block hardness used for break speed.
     *
     * @param hardness
     *            hardness value
     * @return this instance for chaining
     */
    public LuaBlock setHardness(float hardness) {
        this.blockHardness = hardness;
        return this;
    }

    /**
     * Sets the explosion resistance for the block.
     *
     * @param resistance
     *            resistance value
     * @return this instance for chaining
     */
    public LuaBlock setResistance(float resistance) {
        this.blockResistance = resistance;
        return this;
    }

    /**
     * Sets the block's light emission value.
     *
     * @param lightValue
     *            light value to assign
     * @return this instance for chaining
     */
    public LuaBlock setLightValue(int lightValue) {
        Block.lightValue[this.blockID] = lightValue;
        return this;
    }

    /**
     * Sets the block's light opacity value.
     *
     * @param lightOpacity
     *            opacity value to assign
     * @return this instance for chaining
     */
    public LuaBlock setLightOpacity(int lightOpacity) {
        Block.lightOpacity[this.blockID] = lightOpacity;
        return this;
    }

    /**
     * Sets the step sound used when walking on the block.
     *
     * @param sound
     *            step sound instance
     * @return this instance for chaining
     */
    public LuaBlock setStepSound(StepSound sound) {
        this.stepSound = sound;
        return this;
    }

    /**
     * Marks the block as unbreakable by setting hardness to -1.
     *
     * @return this instance for chaining
     */
    public LuaBlock setBlockUnbreakable() {
        this.setHardness(-1.0F);
        return this;
    }

    /** Enables or disables Minecraft's random update selection for this block. */
    public LuaBlock setRandomTicks(boolean enabled) {
        this.setTickOnLoad(enabled);
        return this;
    }

    /** Starts any configured scheduled updates after this block is placed. */
    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        // BlockContainer's implementation installs unconditionally, including null for
        // ordinary blocks.
        TileEntity entity = getBlockEntity();
        if (entity != null) {
            world.setBlockTileEntity(x, y, z, entity);
        }
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        if (definition != null && !world.multiplayerWorld) {
            BlockCallbackRegistry.event(blockID, BlockCallback.ADDED, world, x, y, z, null, null);
        }
        BlockTickRegistry.onBlockAdded(this, world, x, y, z);
    }

    /**
     * Supplies the attached Lua definition when Minecraft creates a tile entity.
     */
    @Override
    protected TileEntity getBlockEntity() {
        return TileEntityRegistry.createForBlock(this.blockID);
    }

    /** Opens an attached standalone Lua container and GUI. */
    @Override
    public boolean blockActivated(World world, int x, int y, int z, EntityPlayer player) {
        InteractionOutcome result = BlockCallbackRegistry.activate(blockID, world, x, y, z, player);
        if (result != InteractionOutcome.PASS) {
            return true;
        }
        TileEntity entity = world.getBlockTileEntity(x, y, z);
        return entity instanceof LuaTileEntity && TileEntityRegistry.open(player, (LuaTileEntity) entity);
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, int neighborId) {
        super.onNeighborBlockChange(world, x, y, z, neighborId);
        TileEntityRegistry.neighborChanged(world, x, y, z, blockID, neighborId);
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        if (definition != null) {
            BlockCallbackRegistry.neighbor(world, x, y, z, blockID, neighborId);
        }
    }

    @Override
    public boolean canProvidePower() {
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        return definition != null && definition.redstone.configured
                ? definition.redstone.providesPower()
                : TileEntityRegistry.providesPower(blockID);
    }

    @Override
    public boolean isPoweringTo(IBlockAccess world, int x, int y, int z, int side) {
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        return definition != null && definition.redstone.configured
                ? definition.redstone.power(world, x, y, z, side, false, definition)
                : TileEntityRegistry.power(world, x, y, z, blockID, false) > 0;
    }

    @Override
    public boolean isIndirectlyPoweringTo(World world, int x, int y, int z, int side) {
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        return definition != null && definition.redstone.configured
                ? definition.redstone.power(world, x, y, z, side, true, definition)
                : TileEntityRegistry.power(world, x, y, z, blockID, true) > 0;
    }

    /** Drops stored items and removes an attached Lua tile entity. */
    @Override
    public void onBlockRemoval(World world, int x, int y, int z) {
        BlockCallbackRegistry.event(blockID, BlockCallback.REMOVED, world, x, y, z, null, null);
        BlockCallbackRegistry.removed(world, x, y, z);
        BlockTickRegistry.removed(world, x, y, z);
        TileEntity entity = world.getBlockTileEntity(x, y, z);
        if (entity instanceof LuaTileEntity && !world.multiplayerWorld) {
            LuaTileEntity lua = (LuaTileEntity) entity;
            LogicalNetworkRuntime.destroyed(lua);
            for (int slot = 0; slot < lua.getSizeInventory(); slot++) {
                ItemStack stack = lua.getStackInSlot(slot);
                if (stack == null) {
                    continue;
                }
                float ox = world.rand.nextFloat() * 0.8F + 0.1F;
                float oy = world.rand.nextFloat() * 0.8F + 0.1F;
                float oz = world.rand.nextFloat() * 0.8F + 0.1F;
                EntityItem dropped = new EntityItem(world, x + ox, y + oy, z + oz, stack.copy());
                world.entityJoinedWorld(dropped);
            }
        }
        super.onBlockRemoval(world, x, y, z);
    }

    /**
     * Marks this block ID as containing a tile entity in Minecraft's chunk format.
     */
    public void enableTileEntity() {
        Block.isBlockContainer[this.blockID] = true;
    }

    /**
     * Delegates Minecraft gameplay updates to the active script-owned definition.
     */
    @Override
    public void updateTick(World world, int x, int y, int z, Random random) {
        BlockCallbackRegistry.recheckNeighbors(world, x, y, z, blockID);
        BlockTickRegistry.update(this, world, x, y, z, random);
    }

    /**
     * Delegates nearby client display updates to the active script-owned
     * definition.
     */
    @Override
    public void randomDisplayTick(World world, int x, int y, int z, Random random) {
        BlockTickRegistry.display(this, world, x, y, z, random);
    }

    @Override
    public void dropBlockAsItemWithChance(World world, int x, int y, int z, int metadata, float chance) {
        if (!world.multiplayerWorld) {
            List<ItemStack> selected = BlockCallbackRegistry.drops(blockID, world, x, y, z, metadata, chance);
            if (selected != null) {
                for (ItemStack drop : selected) {
                    dropBlockAsItem_do(world, x, y, z, drop);
                }
                return;
            }
        }
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        if (definition != null && definition.drops.declared) {
            if (!world.multiplayerWorld) {
                for (ItemStack drop : definition.drops.sample(world.rand, chance)) {
                    dropBlockAsItem_do(world, x, y, z, drop);
                }
            }
            return;
        }
        super.dropBlockAsItemWithChance(world, x, y, z, metadata, chance);
    }

    @Override
    public int getBlockTextureFromSide(int side) {
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        if (definition != null && definition.placement.horizontal) {
            return getBlockTextureFromSideAndMetadata(side, definition.state.defaults);
        }
        return getBaseTextureFromSide(side);
    }

    private int getBaseTextureFromSide(int side) {
        if (side >= 0 && side < sideTextures.length && sideTextureSet[side]) {
            return sideTextures[side];
        }
        return this.blockIndexInTexture;
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, int x, int y, int z, int side) {
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        return super.canPlaceBlockAt(world, x, y, z)
                && (definition == null || BlockCallbackRegistry.canPlace(blockID, definition, world, x, y, z, side));
    }

    @Override
    public void onBlockPlaced(World world, int x, int y, int z, int side) {
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        if (definition != null && !world.multiplayerWorld) {
            int metadata = definition.state.defaults;
            if (definition.state.has("attachedFace")) {
                metadata = definition.state.set(metadata, "attachedFace",
                        LuaValue.valueOf(BlockFace.fromNative(side).luaName));
            }
            world.setBlockMetadataWithNotify(x, y, z, metadata);
            LogicalNetworkRuntime.dirtyAt(world, x, y, z);
        }
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLiving entity) {
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        if (definition != null && !world.multiplayerWorld) {
            definition.placement.placed(world, x, y, z, entity);
        }
        BlockCallbackRegistry.event(blockID, BlockCallback.PLACED, world, x, y, z,
                entity instanceof EntityPlayer ? (EntityPlayer) entity : null, entity);
    }

    @Override
    public void onBlockClicked(World world, int x, int y, int z, EntityPlayer player) {
        BlockCallbackRegistry.event(blockID, BlockCallback.CLICK, world, x, y, z, player, null);
    }

    @Override
    public void onBlockDestroyedByExplosion(World world, int x, int y, int z) {
        BlockCallbackRegistry.event(blockID, BlockCallback.EXPLODED, world, x, y, z, null, null);
    }

    @Override
    public void onEntityWalking(World world, int x, int y, int z, Entity entity) {
        BlockCallbackRegistry.event(blockID, BlockCallback.ENTITY_WALK, world, x, y, z, null, entity);
    }

    @Override
    public void onEntityCollidedWithBlock(World world, int x, int y, int z, Entity entity) {
        BlockCallbackRegistry.event(blockID, BlockCallback.ENTITY_COLLIDE, world, x, y, z, null, entity);
    }

    @Override
    public boolean isOpaqueCube() {
        BlockDefinition def = BlockCallbackRegistry.get(blockID);
        return def != null && def.shapes.opaque != null ? def.shapes.opaque.booleanValue() : super.isOpaqueCube();
    }

    @Override
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        if (!BlockModelRegistry.hasModels(blockID) && !isOpaqueCube() && definition != null
                && definition.visual.renderType == 0
                && (definition.visual.bounds == null || definition.visual.bounds.isFullCube())
                && world.getBlockId(x, y, z) == blockID) {
            return false;
        }
        return super.shouldSideBeRendered(world, x, y, z, side);
    }

    @Override
    public boolean renderAsNormalBlock() {
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        // Vanilla uses this flag for suffocation, player push-out and solid support.
        return definition == null ? super.renderAsNormalBlock() : definition.normalCube;
    }

    @Override
    public boolean isLadder() {
        BlockDefinition def = BlockCallbackRegistry.get(blockID);
        return def != null && def.shapes.climbable;
    }

    @Override
    public boolean isBlockReplaceable(World world, int x, int y, int z) {
        BlockDefinition def = BlockCallbackRegistry.get(blockID);
        return def != null && def.shapes.replaceable;
    }

    @Override
    public int getMobilityFlag() {
        BlockDefinition def = BlockCallbackRegistry.get(blockID);
        return def == null ? super.getMobilityFlag() : def.shapes.mobility;
    }

    @Override
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        BlockDefinition def = BlockCallbackRegistry.get(blockID);
        return def != null && def.shapes.selection != null
                ? def.orient(def.shapes.selection, world.getBlockMetadata(x, y, z)).boundsAt(x, y, z)
                : super.getSelectedBoundingBoxFromPool(world, x, y, z);
    }

    @Override
    public MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3D start, Vec3D end) {
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        if (definition == null || definition.shapes.selection == null) {
            return super.collisionRayTrace(world, x, y, z, start, end);
        }
        BlockBox oriented = definition.orient(definition.shapes.selection, world.getBlockMetadata(x, y, z));
        AxisAlignedBB selection = oriented.boundsAt(x, y, z);
        MovingObjectPosition hit = selection.func_1169_a(start, end);
        return hit == null ? null : new MovingObjectPosition(x, y, z, hit.sideHit, hit.hitVec);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        BlockDefinition def = BlockCallbackRegistry.get(blockID);
        if (def == null || def.shapes.boxes == null) {
            return super.getCollisionBoundingBoxFromPool(world, x, y, z);
        }
        return def.shapes.boxes.size() == 1
                ? def.orient(def.shapes.boxes.get(0), world.getBlockMetadata(x, y, z)).boundsAt(x, y, z)
                : null;
    }

    @Override
    @SuppressWarnings("unchecked") // Minecraft's collision callback exposes a raw output list.
    public void getCollidingBoundingBoxes(World world, int x, int y, int z, AxisAlignedBB query, ArrayList output) {
        BlockDefinition def = BlockCallbackRegistry.get(blockID);
        if (def == null || def.shapes.boxes == null) {
            super.getCollidingBoundingBoxes(world, x, y, z, query, output);
            return;
        }
        for (BlockBox box : def.shapes.boxes) {
            AxisAlignedBB bounds = def.orient(box, world.getBlockMetadata(x, y, z)).boundsAt(x, y, z);
            if (bounds.intersectsWith(query)) {
                output.add(bounds);
            }
        }
    }

    @Override
    public boolean canConnectRedstone(IBlockAccess world, int x, int y, int z, int side) {
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        return definition == null
                ? canProvidePower()
                : definition.redstone.connects(side, definition.facing(world.getBlockMetadata(x, y, z)));
    }

    @Override
    public int getRenderType() {
        if (BlockModelRegistry.hasModels(blockID) && !BlockModelRegistry.isOrdinaryRendering(blockID)) {
            return BlockModelRegistry.renderType();
        }
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        return definition == null ? super.getRenderType() : definition.visual.renderType;
    }

    @Override
    public int getRenderBlockPass() {
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        return definition == null ? super.getRenderBlockPass() : definition.visual.renderPass;
    }

    @Override
    public int getBlockTextureFromSideAndMetadata(int side, int metadata) {
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        if (definition != null && definition.placement.horizontal) {
            // Like vanilla furnaces, item models put the front on the visible south face.
            metadata = definition.state.set(metadata, "facing", LuaValue.valueOf("south"));
        }
        return definition == null
                ? getBaseTextureFromSide(side)
                : definition.visual.texture(metadata, side, getBaseTextureFromSide(side));
    }

    @Override
    public int getBlockTexture(IBlockAccess world, int x, int y, int z, int side) {
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        int fallback = getBaseTextureFromSide(side);
        return definition == null
                ? fallback
                : definition.visual.texture(world.getBlockMetadata(x, y, z), side, fallback);
    }

    @Override
    public int getRenderColor(int metadata) {
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        return definition == null ? super.getRenderColor(metadata) : definition.visual.color(metadata);
    }

    @Override
    public int colorMultiplier(IBlockAccess world, int x, int y, int z) {
        return getRenderColor(world.getBlockMetadata(x, y, z));
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        applyRenderBounds(world.getBlockMetadata(x, y, z));
    }

    @Override
    public void setBlockBoundsForItemRender() {
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        applyRenderBounds(definition == null ? 0 : definition.state.defaults);
    }

    private void applyRenderBounds(int metadata) {
        BlockDefinition definition = BlockCallbackRegistry.get(blockID);
        if (definition != null && definition.visual.bounds != null) {
            BlockBox bounds = definition.orient(definition.visual.bounds, metadata);
            setBlockBounds((float) bounds.minX, (float) bounds.minY, (float) bounds.minZ, (float) bounds.maxX,
                    (float) bounds.maxY, (float) bounds.maxZ);
        } else {
            setBlockBounds(0, 0, 0, 1, 1, 1);
        }
    }
}
