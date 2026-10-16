package betamoon.luamodloader;

import betamoon.instrumentation.hooks.multiblock.MultiblockChunkCallbacks;
import betamoon.luaapi.BetaMoonModule;
import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.BlockDefinition;
import betamoon.luaapi.block.BlockCallback;
import betamoon.luaapi.block.BlockTickRegistry;
import betamoon.luaapi.block.LuaBlock;
import betamoon.luaapi.resource.ResourceApi;
import betamoon.luaapi.tileentity.TileEntityApi;
import betamoon.multiblock.MultiblockPosition;
import betamoon.multiblock.MultiblockRegistry;
import betamoon.multiblock.MultiblockRuntime;
import betamoon.multiblock.ControllerlessMultiblockRuntime;
import betamoon.multiblock.MultiblockInstance;
import betamoon.luaapi.item.ItemCallbackOverrides;
import net.minecraft.src.EntityPlayer;
import betamoon.tileentity.ContainerDefinition;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.LuaTileEntity;
import betamoon.tileentity.TileEntityDefinition;
import betamoon.tileentity.TileEntityRegistry;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.Item;
import net.minecraft.src.ItemBlock;
import net.minecraft.src.ItemStack;
import net.minecraft.src.MapDataBase;
import net.minecraft.src.Material;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Runs the actual tutorial callbacks with real recipes, inventories, patterns
 * and GUI declarations.
 */
public final class MultiblockExamplesTest {
    private MultiblockExamplesTest() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("betamoon.runtime.side", "unknown");
        require(Block.stone != null && Item.coal != null, "Vanilla registries must initialize");
        Field registered = TileEntityRegistry.class.getDeclaredField("minecraftTypeRegistered");
        registered.setAccessible(true);
        registered.setBoolean(null, true);
        Globals lua = JsePlatform.standardGlobals();
        new BetaMoonModule().call(LuaValue.NIL, lua);
        installNativeRegistrationFixture(lua);
        load(lua, "03_adv_06_visual_portal.lua");
        load(lua, "03_adv_12_charcoal_kiln.lua");
        portal();
        kiln();
        kilnGui();
        System.out.println("Multiblock examples passed: fire ignition, owned visual cleanup, log variants, "
                + "fuel-free atomic processing, formation gates, two slots, flame placement and warning tooltip.");
    }

    private static void load(Globals lua, String name) throws Exception {
        try (ScriptExecutionScope ignored = ScriptExecutionScope.open(name);
                MultiblockRegistry.Batch batch = MultiblockRegistry.begin(name);
                Reader reader = Files.newBufferedReader(Paths.get("examples", name), StandardCharsets.UTF_8)) {
            lua.load(reader, "@examples/" + name).call();
            lua.get("modInit").call();
            batch.publish();
        }
    }

    private static void installNativeRegistrationFixture(Globals lua) {
        LuaValue blocks = lua.get("betamoon").get("blocks");
        blocks.set("add", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaValue value = args.arg(args.arg1() == blocks ? 2 : 1);
                int id = value.get("id").checkint();
                Material material = "portal".equals(value.get("material").tojstring())
                        ? Material.portal
                        : Material.rock;
                LuaBlock block = new LuaBlock(id, value.get("texture").optint(1), material,
                        value.get("key").checkjstring());
                new ItemBlock(id - 256);
                BlockCallbackRegistry.install(id, new BlockDefinition(value));
                BlockTickRegistry.register(block, value.get("onTick"), value.get("onDisplayTick"));
                if (!value.get("tileEntity").isnil()) {
                    TileEntityDefinition tile = ((TileEntityApi.TileEntityHandle) value.get("tileEntity")).definition;
                    ContainerDefinition container = value.get("container").isnil()
                            ? null
                            : ((TileEntityApi.ContainerHandle) value.get("container")).definition;
                    ContainerGuiDefinition gui = value.get("gui").isnil()
                            ? null
                            : ((TileEntityApi.GuiHandle) value.get("gui")).definition;
                    TileEntityRegistry.attachBlock(id, tile, container, gui, null);
                    block.enableTileEntity();
                }
                return ResourceApi.blockReference(block);
            }
        });
    }

    private static void portal() {
        require(!Block.isBlockContainer[243] && !Block.isBlockContainer[244],
                "Controllerless frame and surface must not require member tile entities");
        Fixture world = new Fixture();
        for (int x = 7; x <= 10; x++) {
            for (int y = 64; y <= 68; y++) {
                if (x == 7 || x == 10 || y == 64 || y == 68) {
                    world.put(x, y, 8, 243, 0);
                }
            }
        }
        world.put(7, 64, 8, 0, 0);
        world.put(10, 64, 8, 0, 0);
        world.put(7, 68, 8, 0, 0);
        world.put(10, 68, 8, 0, 0);
        world.put(8, 64, 8, 243, 0);
        world.step(3);
        require(world.getBlockId(8, 65, 8) == 0, "A complete frame must wait for flint and steel");
        light(world, 8, 64, 8);
        world.step(3);
        for (int x = 8; x <= 9; x++) {
            for (int y = 65; y <= 67; y++) {
                require(world.getBlockId(x, y, 8) == 244, "Fire must light all six visual panels");
            }
        }
        String identity = portalInstance(world, 8, 64, 8).identity;
        MultiblockRuntime.unload(world);
        MultiblockRuntime.chunkLoaded(world.chunk);
        world.step(3);
        require(world.getBlockId(8, 65, 8) == 244 && portalInstance(world, 8, 64, 8).identity.equals(identity),
                "A relit world must retain portal ownership without duplicating activation");
        require(BlockCallbackRegistry.get(244).shapes.boxes.isEmpty(), "Visual panels must not collide");
        Block surface = Block.blocksList[244];
        surface.setBlockBoundsBasedOnState(world, 8, 65, 8);
        require(surface.minX == 0 && surface.maxX == 1 && surface.minZ == 0.4375 && surface.maxZ == 0.5625,
                "The default portal surface must span the frame width");
        require(!BlockCallbackRegistry.get(244).callbacks.has(BlockCallback.ENTITY_COLLIDE),
                "Visual panels must not install teleport/collision behavior");
        world.put(9, 66, 8, 20, 0);
        world.step(3);
        require(world.getBlockId(9, 66, 8) == 20 && world.getBlockId(8, 65, 8) == 0,
                "Extinguishing must remove owned panels and preserve a player's replacement");
        world.put(9, 66, 8, 0, 0);
        light(world, 8, 64, 8);
        world.step(3);
        require(world.getBlockId(8, 65, 8) == 244, "A repaired portal must relight");
        world.put(8, 64, 8, 1, 0);
        world.step(3);
        require(world.getBlockId(8, 65, 8) == 0, "Removing a frame block must clean its visual panels");
        MultiblockRuntime.unload(world);
        Fixture rotated = new Fixture();
        for (int z = 7; z <= 10; z++) {
            for (int y = 64; y <= 68; y++) {
                if (z == 7 || z == 10 || y == 64 || y == 68) {
                    rotated.put(8, y, z, 243, 0);
                }
            }
        }
        rotated.put(8, 64, 8, 243, 1);
        light(rotated, 8, 64, 8);
        rotated.step(3);
        require(rotated.getBlockId(8, 65, 9) == 244, "Both portal orientations must use the same block ID");
        surface.setBlockBoundsBasedOnState(rotated, 8, 65, 9);
        require(surface.minX == 0.4375 && surface.maxX == 0.5625 && surface.minZ == 0 && surface.maxZ == 1,
                "Saved facing must rotate the portal surface bounds");
        MultiblockRuntime.unload(rotated);
        MultiblockRuntime.chunkLoaded(rotated.chunk);
        rotated.step(3);
        require(rotated.getBlockId(8, 65, 9) == 244 && rotated.getBlockMetadata(8, 65, 9) == 1,
                "A rotated portal must retain its single block ID and facing after reload");
        surface.setBlockBoundsForItemRender();
        require(surface.minX == 0 && surface.maxX == 1 && surface.minZ == 0.4375 && surface.maxZ == 0.5625,
                "Inventory rendering must restore the declared default facing");
        MultiblockRuntime.unload(rotated);
    }

    private static MultiblockInstance portalInstance(Fixture world, int x, int y, int z) {
        return ControllerlessMultiblockRuntime.at(world, x, y, z, null).get(0);
    }

    private static void light(Fixture world, int x, int y, int z) {
        EntityPlayer player = new EntityPlayer(world) {
            public void func_6420_o() {
            }
        };
        ItemStack tool = new ItemStack(Item.flintAndSteel);
        player.inventory.mainInventory[0] = tool;
        require(ItemCallbackOverrides.useOnBlock(Item.flintAndSteel, tool, player, world, x, y, z, 1),
                "Flint and steel must activate the discovered frame");
        require(tool.getItemDamage() == 1, "Ignition charges one tool use");
    }

    private static void kiln() {
        Fixture world = new Fixture();
        String[][] layers = {{"BBB", "BBB", "BBB"}, {"B@B", "B.B", "BBB"}, {"BBB", "B.B", "BBB"}};
        for (int y = 0; y < 3; y++) {
            for (int z = 0; z < 3; z++) {
                for (int x = 0; x < 3; x++) {
                    char symbol = layers[y][z].charAt(x);
                    if (symbol != '.') {
                        world.put(7 + x, 64 + y, 8 + z, symbol == '@' ? 246 : 45, 0);
                    }
                }
            }
        }
        LuaTileEntity tile = (LuaTileEntity) world.getBlockTileEntity(8, 65, 8);
        world.step(3);
        require(tile.getSizeInventory() == 2 && tile.getDataLua("formed").toboolean(), "Kiln needs exactly two slots");
        int duration = tile.getDataLua("duration").toint();
        require(duration == 400, "The kiln must use the configured 400-tick duration");
        for (int variant = 0; variant < 3; variant++) {
            tile.setInventorySlotContents(0, new ItemStack(17, 1, variant));
            tile.setInventorySlotContents(1, null);
            world.step(duration - 1);
            require(tile.getStackInSlot(1) == null && tile.getStackInSlot(0) != null,
                    "The kiln must not finish before the configured duration");
            world.step(1);
            ItemStack output = tile.getStackInSlot(1);
            require(output != null && output.itemID == 263 && output.getItemDamage() == 1 && output.stackSize == 1
                    && tile.getStackInSlot(0) == null, "Every vanilla log must yield one charcoal without fuel");
        }
        tile.setInventorySlotContents(0, new ItemStack(17, 2, 0));
        tile.setInventorySlotContents(1, null);
        world.step(50);
        tile.setInventorySlotContents(0, new ItemStack(17, 2, 1));
        world.step(1);
        require(tile.getDataLua("progress").toint() == 1, "Changing log species must reset partial progress");
        int paused = tile.getDataLua("progress").toint();
        tile.setInventorySlotContents(1, new ItemStack(263, 64, 1));
        world.step(duration + 10);
        require(tile.getStackInSlot(0).stackSize == 2 && tile.getDataLua("blocked").toboolean()
                && tile.getDataLua("progress").toint() == paused, "Full output must not consume input");
        tile.setInventorySlotContents(1, null);
        world.put(8, 66, 9, 1, 0);
        world.step(duration + 10);
        require(tile.getStackInSlot(1) == null && tile.getStackInSlot(0).stackSize == 2
                && !tile.getDataLua("formed").toboolean(), "Filling either required air cell must stop processing");
        world.put(8, 66, 9, 0, 0);
        world.step(duration + 5);
        require(tile.getStackInSlot(1) != null, "Repairing the exact shell must restore processing");
        MultiblockRuntime.unload(world);
    }

    private static void kilnGui() {
        ContainerGuiDefinition gui = TileEntityRegistry.getGui("example:block/charcoal_kiln");
        require(gui.container.slots.size() == 2 && gui.background.texture == null,
                "The GUI must not paint a furnace fuel slot or a static arrow");
        boolean flame = false;
        boolean flameBackground = false;
        boolean warning = false;
        for (ContainerGuiDefinition.Element element : gui.elements) {
            if (element instanceof ContainerGuiDefinition.ProgressElement) {
                ContainerGuiDefinition.ProgressElement progress = (ContainerGuiDefinition.ProgressElement) element;
                flame = progress.x == 80 && progress.y == 36 && progress.image.u == 176 && progress.image.v == 0;
            }
            if (element instanceof ContainerGuiDefinition.ImageElement) {
                ContainerGuiDefinition.ImageElement image = (ContainerGuiDefinition.ImageElement) element;
                flameBackground |= image.x == 80 && image.y == 36 && image.image.u == 56 && image.image.v == 36;
            }
            if (element instanceof ContainerGuiDefinition.TooltipElement) {
                warning |= element.visibleWhen != null && !element.tooltip.isEmpty();
            }
        }
        require(flame && flameBackground && warning,
                "The GUI needs a flame at the arrow origin and a conditional warning tooltip");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Fixture extends World {
        private final Map<MultiblockPosition, TileEntity> tiles = new LinkedHashMap<>();
        private final Map<String, MapDataBase> saved = new HashMap<>();
        private final Chunk chunk;

        private Fixture() {
            super(null, "multiblock_examples_test", new WorldProvider() {
            }, 0L);
            chunk = new Chunk(this, new byte[32768], 0, 0);
            chunk.isChunkLoaded = true;
            MultiblockRuntime.chunkLoaded(chunk);
        }

        protected IChunkProvider getChunkProvider() {
            return null;
        }

        private void put(int x, int y, int z, int id, int metadata) {
            int oldId = getBlockId(x, y, z);
            int old = MultiblockChunkCallbacks.state(chunk, x, y, z, id, metadata);
            chunk.blocks[x << 11 | z << 7 | y] = (byte) id;
            chunk.setBlockMetadata(x, y, z, metadata);
            if (oldId != id) {
                tiles.remove(new MultiblockPosition(x, y, z));
                if (Block.blocksList[id] instanceof LuaBlock) {
                    Block.blocksList[id].onBlockAdded(this, x, y, z);
                }
            }
            MultiblockChunkCallbacks.changed(chunk, x, y, z, old);
        }

        private void step(int ticks) {
            for (int tick = 0; tick < ticks; tick++) {
                for (TileEntity tile : new ArrayList<TileEntity>(tiles.values())) {
                    if (tiles.get(new MultiblockPosition(tile.xCoord, tile.yCoord, tile.zCoord)) == tile) {
                        tile.updateEntity();
                    }
                }
                MultiblockRuntime.tick(this);
            }
        }

        public boolean blockExists(int x, int y, int z) {
            return x >= 0 && x < 16 && z >= 0 && z < 16 && y >= 0 && y < 128;
        }

        public int getBlockId(int x, int y, int z) {
            return chunk.getBlockID(x, y, z);
        }

        public int getBlockMetadata(int x, int y, int z) {
            return chunk.getBlockMetadata(x, y, z);
        }

        public boolean setBlockAndMetadataWithNotify(int x, int y, int z, int id, int metadata) {
            boolean changed = getBlockId(x, y, z) != id || getBlockMetadata(x, y, z) != metadata;
            put(x, y, z, id, metadata);
            return changed;
        }

        public void setBlockMetadataWithNotify(int x, int y, int z, int metadata) {
            put(x, y, z, getBlockId(x, y, z), metadata);
        }

        public TileEntity getBlockTileEntity(int x, int y, int z) {
            return tiles.get(new MultiblockPosition(x, y, z));
        }

        public void setBlockTileEntity(int x, int y, int z, TileEntity tile) {
            tile.worldObj = this;
            tile.xCoord = x;
            tile.yCoord = y;
            tile.zCoord = z;
            tiles.put(new MultiblockPosition(x, y, z), tile);
        }

        public void func_698_b(int x, int y, int z, TileEntity tile) {
        }

        public Chunk getChunkFromChunkCoords(int x, int z) {
            require(x == 0 && z == 0, "Example fixture must not provide unavailable chunks");
            return chunk;
        }

        public MapDataBase loadItemData(Class type, String name) {
            return saved.get(name);
        }

        public void setItemData(String name, MapDataBase value) {
            saved.put(name, value);
        }
    }
}
