package betamoon.tileentity;

import betamoon.BetaMoonCommon;

import betamoon.luamodloader.NonReloadableScriptRegistry;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.src.Block;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IBlockAccess;
import net.minecraft.src.ModLoader;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import org.luaj.vm2.LuaTable;

/**
 * Stores startup-only Lua tile entity structures and their block associations.
 */
public final class TileEntityRegistry {
    private static final Map<String, TileEntityDefinition> TILE_ENTITIES = new HashMap<>();
    private static final Map<String, ContainerDefinition> CONTAINERS = new HashMap<>();
    private static final Map<String, ContainerGuiDefinition> GUIS = new HashMap<>();
    private static final Map<Integer, BlockBinding> BLOCKS = new HashMap<>();
    private static boolean minecraftTypeRegistered;

    private TileEntityRegistry() {
    }

    public static synchronized void register(TileEntityDefinition definition) {
        rejectDuplicate(TILE_ENTITIES, definition.name, "tile entity");
        if (!minecraftTypeRegistered) {
            ModLoader.RegisterTileEntity(LuaTileEntity.class, "BetaMoonLua");
            minecraftTypeRegistered = true;
        }
        TILE_ENTITIES.put(definition.name, definition);
        NonReloadableScriptRegistry.mark(definition.owner, "tile entities or related content");
    }

    public static synchronized void register(ContainerDefinition definition) {
        rejectDuplicate(CONTAINERS, definition.name, "container");
        CONTAINERS.put(definition.name, definition);
        NonReloadableScriptRegistry.mark(definition.owner, "tile entities or related content");
    }

    public static synchronized void register(ContainerGuiDefinition definition) {
        rejectDuplicate(GUIS, definition.name, "container GUI");
        GUIS.put(definition.name, definition);
        NonReloadableScriptRegistry.mark(definition.owner, "tile entities or related content");
    }

    public static synchronized TileEntityDefinition getTileEntity(String name) {
        return TILE_ENTITIES.get(name);
    }

    public static synchronized ContainerDefinition getContainer(String name) {
        return CONTAINERS.get(name);
    }

    public static synchronized ContainerGuiDefinition getGui(String name) {
        return GUIS.get(name);
    }

    /** Attaches three validated standalone definitions to a custom block. */
    public static synchronized void attachBlock(int blockId, TileEntityDefinition tile, ContainerDefinition container,
            ContainerGuiDefinition gui, RedstoneDefinition redstone) {
        if (container != null && container.tileEntity != tile) {
            throw new IllegalArgumentException("Container tile entity does not match the block.");
        }
        if (gui != null && gui.container != container) {
            throw new IllegalArgumentException("Container GUI does not match the block container.");
        }
        BLOCKS.put(Integer.valueOf(blockId), new BlockBinding(tile, container, gui, redstone));
    }

    public static synchronized LuaTileEntity createForBlock(int blockId) {
        BlockBinding binding = BLOCKS.get(Integer.valueOf(blockId));
        return binding == null ? null : new LuaTileEntity(binding.tile.name);
    }

    public static synchronized boolean hasBlock(int blockId) {
        return BLOCKS.containsKey(Integer.valueOf(blockId));
    }

    public static synchronized boolean providesPower(int blockId) {
        BlockBinding binding = BLOCKS.get(Integer.valueOf(blockId));
        return binding != null && binding.redstone != null && binding.redstone.providesPower();
    }

    public static int power(IBlockAccess world, int x, int y, int z, int blockId, boolean strong) {
        BlockBinding binding;
        synchronized (TileEntityRegistry.class) {
            binding = BLOCKS.get(Integer.valueOf(blockId));
        }
        if (binding == null || binding.redstone == null) {
            return 0;
        }
        String field = strong ? binding.redstone.strongPowerField : binding.redstone.weakPowerField;
        if (field == null) {
            return 0;
        }
        TileEntity entity = world.getBlockTileEntity(x, y, z);
        return entity instanceof LuaTileEntity
                ? Math.max(0, Math.min(15, ((LuaTileEntity) entity).getDataInt(field)))
                : 0;
    }

    public static void neighborChanged(World world, int x, int y, int z, int blockId, int neighborId) {
        BlockBinding binding;
        synchronized (TileEntityRegistry.class) {
            binding = BLOCKS.get(Integer.valueOf(blockId));
        }
        if (binding == null || binding.redstone == null || binding.redstone.neighborAction.isnil()
                || !binding.redstone.neighborActionEnabled || world.multiplayerWorld) {
            return;
        }
        TileEntity entity = world.getBlockTileEntity(x, y, z);
        if (!(entity instanceof LuaTileEntity)) {
            return;
        }
        try {
            LuaTable context = ((LuaTileEntity) entity).createContext();
            context.set("neighborId", neighborId);
            context.set("powered", org.luaj.vm2.LuaValue.valueOf(world.isBlockIndirectlyGettingPowered(x, y, z)));
            binding.redstone.neighborAction.call(context);
        } catch (Throwable error) {
            binding.redstone.neighborActionEnabled = false;
            String message = "redstone onNeighborChanged was disabled after an error: " + error.getMessage();
            betamoon.luamodloader.LuaScriptErrors.add(binding.tile.owner, message);
            BetaMoonCommon.LOGGER.warning(binding.tile.owner + ": " + message);
        }
    }

    /** Rolls back structural definitions when their owning modInit fails. */
    public static synchronized void removeOwned(String owner) {
        removeOwned(TILE_ENTITIES, owner);
        removeOwned(CONTAINERS, owner);
        removeOwned(GUIS, owner);
        Iterator<Map.Entry<Integer, BlockBinding>> bindings = BLOCKS.entrySet().iterator();
        while (bindings.hasNext()) {
            Map.Entry<Integer, BlockBinding> entry = bindings.next();
            BlockBinding binding = entry.getValue();
            if (!owner.equals(binding.tile.owner)) {
                continue;
            }
            int blockId = entry.getKey().intValue();
            Block.isBlockContainer[blockId] = false;
            bindings.remove();
        }
        NonReloadableScriptRegistry.unmark(owner);
    }

    private static void removeOwned(Map<String, ?> registry, String owner) {
        Iterator<?> entries = registry.values().iterator();
        while (entries.hasNext()) {
            Object value = entries.next();
            String valueOwner = value instanceof TileEntityDefinition
                    ? ((TileEntityDefinition) value).owner
                    : value instanceof ContainerDefinition
                            ? ((ContainerDefinition) value).owner
                            : ((ContainerGuiDefinition) value).owner;
            if (owner.equals(valueOwner)) {
                entries.remove();
            }
        }
    }

    /** Opens the matching client container in single-player. */
    public static boolean open(EntityPlayer player, LuaTileEntity entity) {
        if (player == null || entity == null || entity.worldObj == null || entity.worldObj.multiplayerWorld) {
            return false;
        }
        BlockBinding binding;
        synchronized (TileEntityRegistry.class) {
            binding = BLOCKS
                    .get(Integer.valueOf(entity.worldObj.getBlockId(entity.xCoord, entity.yCoord, entity.zCoord)));
        }
        if (binding == null || binding.container == null || binding.gui == null) {
            return false;
        }
        try {
            ModLoader.OpenGUI(player, new GuiLuaContainer(player.inventory, entity, binding.gui));
            return true;
        } catch (Throwable error) {
            BetaMoonCommon.LOGGER.warning("Could not open Lua container GUI: " + error.getMessage());
            return false;
        }
    }

    private static void rejectDuplicate(Map<String, ?> registry, String name, String type) {
        if (registry.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate " + type + " name: " + name);
        }
    }

    private static final class BlockBinding {
        private final TileEntityDefinition tile;
        private final ContainerDefinition container;
        private final ContainerGuiDefinition gui;
        private final RedstoneDefinition redstone;
        private BlockBinding(TileEntityDefinition tile, ContainerDefinition container, ContainerGuiDefinition gui,
                RedstoneDefinition redstone) {
            this.tile = tile;
            this.container = container;
            this.gui = gui;
            this.redstone = redstone;
        }
    }
}
