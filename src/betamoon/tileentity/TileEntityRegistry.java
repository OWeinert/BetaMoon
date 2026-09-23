package betamoon.tileentity;

import betamoon.BetaMoonCommon;

import betamoon.luamodloader.NonReloadableScriptRegistry;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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

    /** Immutable block-to-machine association for diagnostics. */
    public static final class BlockBindingDescription {
        public final int blockId;
        public final String tileEntity;
        public final String container;
        public final String gui;
        public final boolean redstone;

        private BlockBindingDescription(int blockId, BlockBinding binding) {
            this.blockId = blockId;
            tileEntity = binding.tile.name;
            container = binding.container == null ? null : binding.container.name;
            gui = binding.gui == null ? null : binding.gui.name;
            redstone = binding.redstone != null;
        }
    }

    public static final class TileDescription {
        public final String name;
        public final String owner;
        public final String inventoryName;
        public final Map<String, Integer> slots;
        public final List<FieldDescription> fields;
        public final int initialTickDelay;
        public final int repeatTickDelay;
        public final boolean randomTicks;
        public final double randomTickChance;

        private TileDescription(TileEntityDefinition definition) {
            name = definition.name;
            owner = definition.owner;
            inventoryName = definition.inventoryName;
            slots = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(definition.slots));
            List<FieldDescription> values = new ArrayList<FieldDescription>();
            for (TileEntityDefinition.Field field : definition.fields.values()) {
                values.add(new FieldDescription(field));
            }
            fields = Collections.unmodifiableList(values);
            initialTickDelay = definition.initialTickDelay;
            repeatTickDelay = definition.repeatTickDelay;
            randomTicks = definition.randomTicks;
            randomTickChance = definition.randomTickChance;
        }
    }

    public static final class FieldDescription {
        public final String name;
        public final String type;
        public final String defaultValue;
        public final boolean synchronizedToClient;

        private FieldDescription(TileEntityDefinition.Field field) {
            name = field.name;
            type = field.type.getLuaName();
            defaultValue = String.valueOf(field.defaultValue);
            synchronizedToClient = field.sync;
        }
    }

    public static final class ContainerDescription {
        public final String name;
        public final String owner;
        public final String tileEntity;
        public final List<SlotDescription> slots;
        public final int playerX;
        public final int playerY;
        public final boolean includeHotbar;

        private ContainerDescription(ContainerDefinition definition) {
            name = definition.name;
            owner = definition.owner;
            tileEntity = definition.tileEntity.name;
            List<SlotDescription> values = new ArrayList<SlotDescription>();
            for (ContainerDefinition.SlotDefinition slot : definition.slots) {
                values.add(new SlotDescription(slot));
            }
            slots = Collections.unmodifiableList(values);
            playerX = definition.playerX;
            playerY = definition.playerY;
            includeHotbar = definition.includeHotbar;
        }
    }

    public static final class SlotDescription {
        public final String name;
        public final int index;
        public final int x;
        public final int y;
        public final boolean outputOnly;
        public final String acceptedFuelSet;

        private SlotDescription(ContainerDefinition.SlotDefinition slot) {
            name = slot.name;
            index = slot.index;
            x = slot.x;
            y = slot.y;
            outputOnly = slot.outputOnly;
            acceptedFuelSet = slot.acceptedFuelSet == null ? null : slot.acceptedFuelSet.toString();
        }
    }

    public static final class GuiDescription {
        public final String name;
        public final String owner;
        public final String container;
        public final int width;
        public final int height;
        public final boolean pauseGame;
        public final String backgroundStyle;
        public final boolean drawSlotFrames;
        public final List<ElementDescription> elements;

        private GuiDescription(ContainerGuiDefinition definition) {
            name = definition.name;
            owner = definition.owner;
            container = definition.container.name;
            width = definition.width;
            height = definition.height;
            pauseGame = definition.pauseGame;
            backgroundStyle = definition.background.style;
            drawSlotFrames = definition.background.drawSlotFrames;
            List<ElementDescription> values = new ArrayList<ElementDescription>();
            for (ContainerGuiDefinition.Element element : definition.elements) {
                values.add(new ElementDescription(element));
            }
            elements = Collections.unmodifiableList(values);
        }
    }

    public static final class ElementDescription {
        public final String type;
        public final int x;
        public final int y;
        public final int layer;
        public final String anchor;

        private ElementDescription(ContainerGuiDefinition.Element element) {
            type = element.getClass().getSimpleName();
            x = element.x;
            y = element.y;
            layer = element.layer;
            anchor = element.anchor.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static synchronized List<TileDescription> tileEntityDescriptions() {
        List<TileDescription> result = new ArrayList<TileDescription>();
        for (TileEntityDefinition definition : TILE_ENTITIES.values()) {
            result.add(new TileDescription(definition));
        }
        return Collections.unmodifiableList(result);
    }

    public static synchronized List<ContainerDescription> containerDescriptions() {
        List<ContainerDescription> result = new ArrayList<ContainerDescription>();
        for (ContainerDefinition definition : CONTAINERS.values()) {
            result.add(new ContainerDescription(definition));
        }
        return Collections.unmodifiableList(result);
    }

    public static synchronized List<GuiDescription> guiDescriptions() {
        List<GuiDescription> result = new ArrayList<GuiDescription>();
        for (ContainerGuiDefinition definition : GUIS.values()) {
            result.add(new GuiDescription(definition));
        }
        return Collections.unmodifiableList(result);
    }

    public static synchronized List<BlockBindingDescription> blockBindings() {
        List<BlockBindingDescription> result = new ArrayList<BlockBindingDescription>();
        for (Map.Entry<Integer, BlockBinding> entry : BLOCKS.entrySet()) {
            result.add(new BlockBindingDescription(entry.getKey().intValue(), entry.getValue()));
        }
        return Collections.unmodifiableList(result);
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
        try (LuaTileEntity.Context context = ((LuaTileEntity) entity).createScopedContext()) {
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
