package betamoon.wrappers;

import betamoon.BetaMoonMain;
import betamoon.luaapi.item.ItemBehavior;
import betamoon.luaapi.item.ItemCallback;
import betamoon.luaapi.item.ItemCallbackRegistry;
import betamoon.luaapi.utils.InteractionOutcome;
import betamoon.resources.LuaTextureResources;
import forge.IArmorTextureProvider;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.src.Block;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemArmor;
import net.minecraft.src.ItemStack;
import net.minecraft.src.RenderPlayer;
import net.minecraft.src.World;

public class ItemArmorWrapper extends ItemArmor implements IArmorTextureProvider {
    private static final Logger LOGGER = BetaMoonMain.LOGGER;
    private static Field RENDER_INDEX_FIELD = resolveRenderIndexField();
    private int armorRenderIndex;
    private String customArmorTexture;

    /**
     * Creates an armor wrapper with the provided id, material, render index, slot
     * type, and internal name.
     *
     * @param id
     *            numeric item id (unshifted)
     * @param material
     *            armor material index
     * @param renderIndex
     *            armor render index
     * @param armorType
     *            armor slot type (0-3)
     * @param name
     *            internal armor name (unlocalized)
     */
    public ItemArmorWrapper(int id, int material, int renderIndex, int armorType, String name) {
        super(id, material, renderIndex, armorType);
        this.armorRenderIndex = renderIndex;
        setItemName(name);
        setIconCoord(0, 0);
    }

    /**
     * Overrides the armor render index to use a vanilla armor texture prefix.
     *
     * @param renderIndex
     *            armor render index (0+)
     * @return this wrapper for chaining
     */
    public ItemArmorWrapper setRenderIndex(int renderIndex) {
        this.armorRenderIndex = renderIndex;
        LuaTextureResources.release(this.customArmorTexture);
        this.customArmorTexture = null;
        if (RENDER_INDEX_FIELD == null) {
            RENDER_INDEX_FIELD = resolveRenderIndexField();
        }
        if (RENDER_INDEX_FIELD != null) {
            try {
                RENDER_INDEX_FIELD.setInt(this, renderIndex);
            } catch (IllegalAccessException ignored) {
                LOGGER.log(Level.WARNING, "Failed to set armor render index to " + renderIndex + ".", ignored);
            }
        }
        return this;
    }

    /**
     * Returns the armor texture path used by the renderer.
     *
     * @return texture path including layer suffix
     */
    public String getArmorTextureFile() {
        if (customArmorTexture != null) {
            return customArmorTexture;
        }
        String texture = "armor/" + resolveArmorTextureName(armorRenderIndex);
        return "/" + texture + "_" + (armorType == 2 ? 2 : 1) + ".png";
    }

    /**
     * Selects a standalone texture for the armor model. This deliberately bypasses
     * renderIndex.
     *
     * @param texture
     *            virtual texture resource path
     * @return this wrapper for chaining
     */
    public ItemArmorWrapper setArmorTexture(String texture) {
        if (texture != null && texture.equals(this.customArmorTexture)) {
            // register() acquired another reference for the same resource; balance it
            // immediately.
            LuaTextureResources.release(texture);
            return this;
        }
        LuaTextureResources.release(this.customArmorTexture);
        this.customArmorTexture = texture;
        return this;
    }

    /**
     * Clears a custom model texture while retaining the current vanilla render
     * index.
     */
    public ItemArmorWrapper useVanillaArmorTexture() {
        LuaTextureResources.release(this.customArmorTexture);
        this.customArmorTexture = null;
        return this;
    }

    /**
     * Resolves the vanilla armor texture base name for a render index.
     *
     * @param index
     *            vanilla render index
     * @return texture base name (e.g. "iron")
     */
    private static String resolveArmorTextureName(int index) {
        String[] names = null;
        try {
            Field field = RenderPlayer.class.getDeclaredFields()[3];
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof String[]) {
                names = (String[]) value;
            }
        } catch (Exception e) {
            names = null;
        }
        if (names == null || names.length == 0) {
            names = new String[]{"cloth", "chain", "iron", "diamond", "gold"};
        }
        if (index < 0 || index >= names.length) {
            index = 0;
        }
        return names[index];
    }

    /**
     * Attempts to resolve the render index field across mapped and obfuscated
     * names.
     *
     * @return resolved field or null when unavailable
     */
    private static Field resolveRenderIndexField() {
        final String[] candidates = new String[]{"renderIndex", "field_77883_b", "b", "c", "d"};
        for (int i = 0; i < candidates.length; i++) {
            Field field = tryResolveRenderIndexField(candidates[i]);
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    /**
     * Resolves and prepares a render index field by name.
     *
     * @param name
     *            candidate field name
     * @return prepared field or null
     */
    private static Field tryResolveRenderIndexField(String name) {
        try {
            Field field = ItemArmor.class.getDeclaredField(name);
            if (field.getType() != Integer.TYPE) {
                return null;
            }
            field.setAccessible(true);
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            return field;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public int getIconFromDamage(int metadata) {
        return ItemBehavior.icon(shiftedIndex, metadata, () -> super.getIconFromDamage(metadata));
    }

    @Override
    public int getColorFromDamage(int metadata) {
        return ItemBehavior.color(shiftedIndex, metadata, () -> super.getColorFromDamage(metadata));
    }

    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side) {
        return ItemCallbackRegistry.interact(ItemCallback.USE_FIRST, stack, player, world, x, y, z, side,
                null) != InteractionOutcome.PASS;
    }

    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side) {
        InteractionOutcome result = ItemCallbackRegistry.interact(ItemCallback.USE_ON_BLOCK, stack, player, world, x, y,
                z, side, null);
        return result != InteractionOutcome.PASS || super.onItemUse(stack, player, world, x, y, z, side);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        return ItemBehavior.use(stack, world, player, () -> super.onItemRightClick(stack, world, player));
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.onUpdate(stack, world, entity, slot, selected);
        ItemBehavior.inventoryTick(stack, world, entity, slot, selected);
    }

    @Override
    public void onCreated(ItemStack stack, World world, EntityPlayer player) {
        super.onCreated(stack, world, player);
        ItemBehavior.crafted(stack, world, player);
    }

    @Override
    public boolean hitEntity(ItemStack stack, EntityLiving target, EntityLiving attacker) {
        return ItemBehavior.hit(shiftedIndex, stack, target, attacker, () -> super.hitEntity(stack, target, attacker));
    }

    @Override
    public boolean onBlockDestroyed(ItemStack stack, int id, int x, int y, int z, EntityLiving entity) {
        return ItemBehavior.destroyedBlock(shiftedIndex, stack, x, y, z, entity,
                () -> super.onBlockDestroyed(stack, id, x, y, z, entity));
    }

    public boolean canHarvestBlock(Block block) {
        return ItemCallbackRegistry.canHarvest(shiftedIndex, block, 0, super.canHarvestBlock(block));
    }

    public float getStrVsBlock(ItemStack stack, Block block, int metadata) {
        return ItemCallbackRegistry.miningSpeed(stack, block, metadata, super.getStrVsBlock(stack, block, metadata));
    }
}
