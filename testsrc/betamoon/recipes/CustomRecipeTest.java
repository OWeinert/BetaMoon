package betamoon.recipes;

import betamoon.luaapi.BetaMoonModule;
import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.BlockComponents;
import betamoon.luaapi.block.BlockDefinition;
import betamoon.luaapi.module.ModuleRegistry;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.tileentity.LuaTileEntity;
import betamoon.tileentity.RecipeCommitResult;
import betamoon.tileentity.TileEntityRegistry;
import betamoon.wrappers.BlockWrapper;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.src.Block;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.ItemStack;
import net.minecraft.src.Material;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Runs Lua against the real public API and authoritative in-memory tile
 * inventories.
 */
public final class CustomRecipeTest {
    public static void main(String[] args) throws Exception {
        if (Block.stone == null) {
            throw new AssertionError("Vanilla items were not initialized");
        }
        // Initialize the reflection hook normally installed by ModLoader.init().
        Method mapping = TileEntity.class.getDeclaredMethod("addMapping", Class.class, String.class);
        mapping.setAccessible(true);
        java.lang.reflect.Field hook = net.minecraft.src.ModLoader.class.getDeclaredField("method_RegisterTileEntity");
        hook.setAccessible(true);
        hook.set(null, mapping);
        final Globals globals = JsePlatform.standardGlobals();
        new BetaMoonModule().call(LuaValue.NIL, globals);
        final Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        final TestWorld world = new TestWorld();
        globals.set("owner", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                try {
                    owner.invoke(null, args.arg1().checkjstring());
                    return NIL;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        globals.set("unload", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                ScriptResourceTracker.unload(args.arg1().checkjstring());
                return NIL;
            }
        });
        globals.set("publish", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                ModuleRegistry.publish(args.arg1().checkjstring());
                return NIL;
            }
        });
        globals.set("entity", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                world.tile = new LuaTileEntity(args.arg1().checkjstring());
                world.tile.worldObj = world;
                return world.tile.createContext();
            }
        });
        globals.set("saveLoad", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                NBTTagCompound tag = new NBTTagCompound();
                world.tile.writeToNBT(tag);
                LuaTileEntity restored = new LuaTileEntity();
                restored.readFromNBT(tag);
                restored.worldObj = world;
                world.tile = restored;
                return restored.createContext();
            }
        });
        globals.set("tick", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                world.tile.updateEntity();
                return NIL;
            }
        });
        globals.set("detach", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                world.tile = null;
                return NIL;
            }
        });
        globals.set("nativeSmelting", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                // Simulates another mod bypassing all BetaMoon revision counters.
                net.minecraft.src.FurnaceRecipes.smelting().getSmeltingList().put(Integer.valueOf(15),
                        new ItemStack(265, args.arg1().checkint(), 0));
                return NIL;
            }
        });
        globals.set("opaqueRecipe", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                net.minecraft.src.CraftingManager.getInstance().getRecipeList().add(new net.minecraft.src.IRecipe() {
                    public boolean matches(net.minecraft.src.InventoryCrafting grid) {
                        return false;
                    }

                    public ItemStack getCraftingResult(net.minecraft.src.InventoryCrafting grid) {
                        return new ItemStack(264, 7, 0);
                    }

                    public int getRecipeSize() {
                        return 1;
                    }

                    public ItemStack getRecipeOutput() {
                        return new ItemStack(264, 7, 0);
                    }
                });
                return NIL;
            }
        });
        FileInputStream stream = new FileInputStream(args[0]);
        try {
            globals.load(stream, args[0], "t", globals).call();
            if (args.length > 1) {
                examples(globals, owner, world, new File(args[1]));
            }
            verifyPreparedCommit(world.tile);
        } finally {
            stream.close();
            owner.invoke(null, new Object[]{null});
        }
        System.out.println("Custom recipe API, inventory, native compatibility, and lifecycle checks passed.");
    }

    private static void examples(final Globals globals, Method owner, TestWorld world, File directory)
            throws Exception {
        final java.util.List<LuaValue> blocks = new java.util.ArrayList<LuaValue>();
        final LuaValue blockRegistry = globals.get("betamoon").get("blocks");
        final LuaValue originalAdd = blockRegistry.get("add");
        // Block/chunk registration has its own regression test. Here substitute only
        // client registration; tile definitions, containers, GUI parsing and recipes
        // are real.
        blockRegistry.set("add", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                blocks.add(args.arg(args.arg1() == blockRegistry ? 2 : 1));
                return blockRegistry.get("getRequired").call(blockRegistry, LuaValue.valueOf(1));
            }
        });
        try {
            packageExample(globals, owner, new File(directory, "03_adv_03_basic_storage"));
            example(globals, owner, new File(directory, "03_adv_04_custom_furnace.lua"));
            example(globals, owner, new File(directory, "03_adv_05_tile_redstone_controller.lua"));
            packageExample(globals, owner, new File(directory, "03_adv_08_simple_alloy"));
            packageExample(globals, owner, new File(directory, "03_adv_09_contextual_processor"));
            packageExample(globals, owner, new File(directory, "03_adv_10_advanced_fabrication"));
            packageExample(globals, owner, new File(directory, "03_adv_11_matcher_cookbook"));
            require(blocks.size() == 7, "Examples did not declare all seven tested machines");
            for (LuaValue declaration : blocks) {
                int id = declaration.get("id").checkint();
                String file = ownerForBlock(id);
                owner.invoke(null, file);
                new BlockWrapper(id, 45, Material.rock, declaration.get("key").checkjstring())
                        .setSideTextureIndex(0, 62).setSideTextureIndex(1, 62);
                BlockDefinition definition = new BlockDefinition(declaration);
                BlockCallbackRegistry.install(id, definition);
                BlockComponents components = new BlockComponents(declaration);
                if (components.tile != null) {
                    TileEntityRegistry.attachBlock(id, components.tile, components.container, components.gui,
                            components.redstone);
                }
            }
            world.blockId = 204;
            world.metadata = 1;
            require(betamoon.luamodloader.NonReloadableScriptRegistry.contains("03_adv_04_custom_furnace.lua"),
                    "Machine callback was not pinned");
            require(betamoon.luamodloader.NonReloadableScriptRegistry
                    .contains("03_adv_08_simple_alloy/main.lua"), "Packaged machine owner was not pinned");
            world.tile = tileFor(blocks, 204);
            world.tile.worldObj = world;
            world.tile.setInventorySlotContents(0, new ItemStack(12, 1, 0));
            world.tile.setInventorySlotContents(1, new ItemStack(263, 1, 0));
            for (int i = 0; i < 100; i++) {
                world.tile.updateEntity();
                requireFurnaceTexture(world, true);
            }
            require(world.tile.getStackInSlot(0) == null && world.tile.getStackInSlot(2).itemID == 20
                    && world.tile.getStackInSlot(2).stackSize == 1,
                    "Advanced example 02 did not smelt sand into one glass after 100 ticks");
            require(world.tile.getDataInt("cookDuration") == 100,
                    "Advanced example 02 GUI duration did not synchronize");
            require(world.renderUpdates == 1, "Burning furnace requested redundant redraws");
            world.tile.setInventorySlotContents(0, new ItemStack(14, 1, 0));
            world.tile.setInventorySlotContents(2, null);
            for (int i = 0; i < 100; i++) {
                world.tile.updateEntity();
            }
            require(world.tile.getStackInSlot(2).itemID == 266 && world.tile.getStackInSlot(2).stackSize == 1,
                    "Pinned advanced example 02 callback failed after recipe/type reload");
            while (world.tile.getDataInt("burnTime") > 0) {
                world.tile.updateEntity();
            }
            requireFurnaceTexture(world, false);
            require(world.renderUpdates == 2, "Fuel exhaustion did not redraw exactly once");
            world.blockId = 209;
            world.metadata = 1;
            world.tile = tileFor(blocks, 209);
            world.tile.worldObj = world;
            world.tile.setInventorySlotContents(0, new ItemStack(14, 1, 0));
            world.tile.setInventorySlotContents(1, new ItemStack(263, 1, 1));
            world.tile.setInventorySlotContents(2, new ItemStack(280, 1, 0));
            world.tile.setInventorySlotContents(4, new ItemStack(4, 64, 0));
            world.tile.updateEntity();
            requireFurnaceTexture(world, false);
            require(world.tile.getStackInSlot(0).stackSize == 1 && world.tile.getDataInt("blocked") == 1,
                    "Full secondary output did not block advanced example 04c");
            world.tile.setInventorySlotContents(4, null);
            for (int i = 0; i < 200; i++) {
                world.tile.updateEntity();
                requireFurnaceTexture(world, true);
            }
            require(world.tile.getStackInSlot(0) == null && world.tile.getStackInSlot(1) == null,
                    "Gold recipe did not consume its inputs");
            require(world.tile.getStackInSlot(3).itemID == 266 && world.tile.getStackInSlot(3).stackSize == 3
                    && world.tile.getStackInSlot(4).stackSize == 1 && world.tile.getStackInSlot(2).stackSize == 1,
                    "Unpowered gold recipe did not produce both outputs and retain its mold");
            world.tile.updateEntity();
            requireFurnaceTexture(world, false);

            world.blockId = 216;
            world.metadata = 0;
            world.tile = tileFor(blocks, 216);
            world.tile.worldObj = world;
            world.tile.setInventorySlotContents(0, new ItemStack(265, 1, 0));
            world.tile.setInventorySlotContents(1, new ItemStack(263, 1, 0));
            world.tile.updateEntity();
            require(world.tile.getStackInSlot(0) == null && world.tile.getStackInSlot(1) == null
                    && world.tile.getStackInSlot(10).itemID == 266,
                    "Advanced sequence matcher did not consume adjacent ordered inputs");

            clearInventory(world.tile);
            for (int slot : new int[]{0, 2, 6, 8}) {
                world.tile.setInventorySlotContents(slot, new ItemStack(265, 1, 0));
            }
            world.tile.setInventorySlotContents(4, new ItemStack(280, 1, 0));
            world.tile.updateEntity();
            require(world.tile.getStackInSlot(10).itemID == 42,
                    "Advanced grid recipe did not match its declared pattern");

            clearInventory(world.tile);
            world.tile.setInventorySlotContents(0, new ItemStack(12, 2, 0));
            world.tile.setInventorySlotContents(8, new ItemStack(13, 2, 0));
            world.tile.updateEntity();
            require(world.tile.getStackInSlot(0) == null && world.tile.getStackInSlot(8) == null
                    && world.tile.getStackInSlot(10).itemID == 337 && world.tile.getStackInSlot(10).stackSize == 4
                    && world.tile.getStackInSlot(11).itemID == 318 && world.tile.getStackInSlot(12).itemID == 4,
                    "Advanced pool recipe did not commit its primary and pooled outputs");
            verifyBasicStorageExample(blocks, world);
            verifyTileRedstoneExample(blocks, world);
            verifyContextualRecipeExample(blocks, world);
            verifyMatcherCookbookExample(blocks, world);
            System.out.println("Advanced examples executed: storage, tile redstone, contextual matching, timing, "
                    + "private package modules, simple roles, pools, grids, custom allocation and pooled outputs "
                    + "passed.");
        } finally {
            blockRegistry.set("add", originalAdd);
        }
    }

    private static void verifyBasicStorageExample(java.util.List<LuaValue> blocks, TestWorld world) {
        world.blockId = 224;
        world.metadata = 0;
        world.powered = false;
        world.tile = tileFor(blocks, 224);
        world.tile.worldObj = world;
        world.tile.setInventorySlotContents(0, new ItemStack(1, 1, 0));
        world.tile.setInventorySlotContents(8, new ItemStack(4, 1, 0));
        require(world.tile.getDataInt("occupied") == 2, "Basic storage did not synchronize its occupied-slot count");
    }

    private static void verifyTileRedstoneExample(java.util.List<LuaValue> blocks, TestWorld world) {
        world.blockId = 225;
        world.metadata = 0;
        world.powered = false;
        world.tile = tileFor(blocks, 225);
        world.tile.worldObj = world;
        world.tile.setInventorySlotContents(0, new ItemStack(331, 1, 0));

        world.powered = true;
        TileEntityRegistry.neighborChanged(world, 0, 0, 0, 225, 1);
        TileEntityRegistry.neighborChanged(world, 0, 0, 0, 225, 1);
        require(world.tile.getDataInt("output") == 1 && world.tile.getDataInt("pulseCount") == 1,
                "Tile redstone controller did not debounce a held input");

        world.powered = false;
        TileEntityRegistry.neighborChanged(world, 0, 0, 0, 225, 1);
        world.powered = true;
        TileEntityRegistry.neighborChanged(world, 0, 0, 0, 225, 1);
        require(world.tile.getDataInt("output") == 0 && world.tile.getDataInt("pulseCount") == 2,
                "Tile redstone controller did not persist and toggle its second pulse");
    }

    private static void verifyContextualRecipeExample(java.util.List<LuaValue> blocks, TestWorld world) {
        world.blockId = 226;
        world.metadata = 0;
        world.powered = false;
        world.tile = tileFor(blocks, 226);
        world.tile.worldObj = world;
        world.tile.setInventorySlotContents(0, new ItemStack(3, 1, 0));
        for (int i = 0; i < 60; i++) {
            world.tile.updateEntity();
        }
        require(world.tile.getStackInSlot(0) == null && world.tile.getStackInSlot(2).itemID == 337
                && world.tile.getStackInSlot(2).stackSize == 4,
                "Cold contextual recipe did not match its unpowered heat range");

        clearInventory(world.tile);
        world.tile.setDataInt("heat", 0);
        world.tile.setInventorySlotContents(0, new ItemStack(12, 1, 0));
        world.tile.setInventorySlotContents(1, new ItemStack(263, 1, 0));
        world.powered = true;
        for (int i = 0; i < 300; i++) {
            world.tile.updateEntity();
        }
        require(world.tile.getStackInSlot(0) == null && world.tile.getStackInSlot(2).itemID == 20
                && world.tile.getStackInSlot(2).stackSize == 2 && world.tile.getDataInt("heat") >= 400,
                "Hot contextual recipe did not wait for heat and power before applying");
    }

    private static void verifyMatcherCookbookExample(java.util.List<LuaValue> blocks, TestWorld world) {
        world.blockId = 227;
        world.metadata = 0;
        world.powered = false;
        world.tile = tileFor(blocks, 227);
        world.tile.worldObj = world;
        world.tile.setInventorySlotContents(0, new ItemStack(4, 4, 0));
        world.tile.setInventorySlotContents(4, new ItemStack(4, 8, 0));
        for (int i = 0; i < 60; i++) {
            world.tile.updateEntity();
        }
        require(world.tile.getStackInSlot(0) == null && world.tile.getStackInSlot(4).stackSize == 8
                && world.tile.getStackInSlot(6).itemID == 1,
                "Custom matcher did not prefer slot one while preserving extra pool inputs");

        clearInventory(world.tile);
        world.tile.setInventorySlotContents(4, new ItemStack(12, 4, 0));
        world.tile.setInventorySlotContents(5, new ItemStack(280, 1, 0));
        world.powered = true;
        for (int i = 0; i < 100; i++) {
            world.tile.updateEntity();
        }
        require(world.tile.getStackInSlot(4) == null && world.tile.getStackInSlot(5).stackSize == 1
                && world.tile.getStackInSlot(6).itemID == 20 && world.tile.getStackInSlot(6).stackSize == 2,
                "Custom matcher did not prefer slot five and retain its declared die");
    }

    private static LuaTileEntity tileFor(java.util.List<LuaValue> blocks, int id) {
        for (LuaValue declaration : blocks) {
            if (declaration.get("id").checkint() == id) {
                LuaTileEntity tile = new LuaTileEntity(declaration.get("tileEntity").get("name").checkjstring());
                return tile;
            }
        }
        throw new AssertionError("Missing block declaration " + id);
    }

    private static String ownerForBlock(int id) {
        switch (id) {
            case 204:
                return "03_adv_04_custom_furnace.lua";
            case 209:
                return "03_adv_08_simple_alloy/main.lua";
            case 216:
                return "03_adv_10_advanced_fabrication/main.lua";
            case 224:
                return "03_adv_03_basic_storage/main.lua";
            case 225:
                return "03_adv_05_tile_redstone_controller.lua";
            case 226:
                return "03_adv_09_contextual_processor/main.lua";
            case 227:
                return "03_adv_11_matcher_cookbook/main.lua";
            default:
                throw new AssertionError("Unknown example block " + id);
        }
    }

    private static void clearInventory(LuaTileEntity tile) {
        for (int slot = 0; slot < tile.getSizeInventory(); slot++) {
            tile.setInventorySlotContents(slot, null);
        }
    }

    private static void requireFurnaceTexture(TestWorld world, boolean lit) {
        BlockDefinition definition = BlockCallbackRegistry.get(world.blockId);
        require(definition.state.get(world.metadata, "lit").toboolean() == lit,
                "Furnace lit state does not match processing state");
        require(definition.state.get(world.metadata, "facing").tojstring().equals("east"),
                "Changing furnace activity overwrote its facing");
        Block block = Block.blocksList[world.blockId];
        for (int side = 0; side < 6; side++) {
            int texture = side < 2 ? 62 : side == 5 ? (lit ? 61 : 44) : 45;
            require(block.getBlockTexture(world, 0, 0, 0, side) == texture,
                    "Furnace activity selected an incorrect face texture");
        }
        require(block.getBlockTextureFromSideAndMetadata(3, 0) == 44,
                "Furnace activity changed the default inventory front");
    }

    private static void verifyPreparedCommit(LuaTileEntity tile) {
        ItemStack[] stale = tile.copyRecipeInventory();
        ItemStack[] rejected = tile.copyRecipeInventory();
        int changedId = stale[0] != null && stale[0].itemID == 1 ? 2 : 1;
        int plannedId = changedId == 2 ? 3 : 2;
        rejected[0] = new ItemStack(plannedId, 1, 0);
        tile.setInventorySlotContents(0, new ItemStack(changedId, 1, 0));
        require(tile.commitRecipeInventory(stale, rejected) == RecipeCommitResult.INVENTORY_CHANGED,
                "A prepared commit accepted a changed source inventory");
        require(tile.getStackInSlot(0).itemID == changedId, "A rejected prepared commit partially changed inventory");

        ItemStack[] current = tile.copyRecipeInventory();
        ItemStack[] accepted = tile.copyRecipeInventory();
        accepted[0] = new ItemStack(plannedId, 1, 0);
        require(tile.commitRecipeInventory(current, accepted) == RecipeCommitResult.APPLIED,
                "A fresh prepared commit was rejected");
        accepted[0].itemID = changedId;
        require(tile.getStackInSlot(0).itemID == plannedId,
                "A committed inventory retained the caller's mutable array");
    }

    private static void example(Globals globals, Method owner, File file) throws Exception {
        example(globals, owner, file, file.getName());
    }

    private static void packageExample(Globals globals, Method owner, File directory) throws Exception {
        File[] files = directory.listFiles((parent, name) -> name.endsWith(".lua"));
        if (files == null) {
            throw new AssertionError("Missing example package " + directory.getName());
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        LuaValue packageTable = globals.get("package");
        LuaValue loaded = packageTable.get("loaded");
        String previousPath = packageTable.get("path").checkjstring();
        String packagePath = new File(directory, "?.lua").getAbsolutePath().replace('\\', '/');
        packageTable.set("path", LuaValue.valueOf(packagePath + ";" + previousPath));
        try {
            for (File file : files) {
                String module = file.getName().substring(0, file.getName().length() - ".lua".length());
                loaded.set(module, LuaValue.NIL);
            }
            example(globals, owner, new File(directory, "main.lua"), directory.getName() + "/main.lua");
        } finally {
            packageTable.set("path", LuaValue.valueOf(previousPath));
        }
    }

    private static void example(Globals globals, Method owner, File file, String sourceName) throws Exception {
        owner.invoke(null, sourceName);
        FileInputStream input = new FileInputStream(file);
        try {
            globals.load(input, sourceName, "t", globals).call();
            globals.get("modInit").call();
            ModuleRegistry.publish(sourceName);
        } finally {
            input.close();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
    private static final class TestWorld extends World {
        LuaTileEntity tile;
        boolean powered;
        int blockId;
        int metadata;
        int renderUpdates;
        TestWorld() {
            super(null, "custom_recipe_test", new WorldProvider() {
            }, 0L);
        }

        protected IChunkProvider getChunkProvider() {
            return null;
        }

        public TileEntity getBlockTileEntity(int x, int y, int z) {
            return tile;
        }

        public int getBlockId(int x, int y, int z) {
            return blockId;
        }

        public int getBlockMetadata(int x, int y, int z) {
            return metadata;
        }

        public void setBlockMetadataWithNotify(int x, int y, int z, int value) {
            metadata = value;
            renderUpdates++;
        }

        public boolean isBlockIndirectlyGettingPowered(int x, int y, int z) {
            return powered;
        }

        public void notifyBlocksOfNeighborChange(int x, int y, int z, int id) {
            // The controller's explicit notification is the behavior under test;
            // this isolated world has no neighboring chunks to notify.
        }

        public void func_698_b(int x, int y, int z, TileEntity entity) {
        }
    }
}
