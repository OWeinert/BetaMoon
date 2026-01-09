package betamoon.wrappers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.Material;
import net.minecraft.src.StepSound;
import net.minecraft.src.World;

public class BlockWrapper extends Block {
    private static final List<PendingDrop> PENDING_DROPS = new ArrayList<PendingDrop>();
    private final List<CustomDrop> customDrops = new ArrayList<CustomDrop>();

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
     * Creates a block wrapper with the provided block id, texture index, and material.
     *
     * @param id numeric block id
     * @param textureId terrain texture index
     * @param material base material for the block
     */
    public BlockWrapper(int id, int textureId, Material material) {
        super(id, textureId, material);
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
