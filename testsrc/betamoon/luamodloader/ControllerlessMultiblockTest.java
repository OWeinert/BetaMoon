package betamoon.luamodloader;

import betamoon.instrumentation.hooks.multiblock.MultiblockChunkCallbacks;
import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.BlockDefinition;
import betamoon.luaapi.block.LuaBlock;
import betamoon.luaapi.item.ItemCallbackOverrides;
import betamoon.luaapi.item.LuaItemActionContext;
import betamoon.luaapi.multiblock.MultiblocksApi;
import betamoon.luaapi.resource.ResourceApi;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.multiblock.ControllerlessMultiblockRuntime;
import betamoon.multiblock.MultiblockInstance;
import betamoon.multiblock.MultiblockPosition;
import betamoon.multiblock.MultiblockRegistry;
import betamoon.multiblock.MultiblockRuntime;
import java.util.ArrayList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import betamoon.multiblock.MultiblockWorldData;
import net.minecraft.src.NBTBase;
import net.minecraft.src.NBTTagCompound;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.MapDataBase;
import net.minecraft.src.Material;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Exercises user-visible placement, activation and ownership against real
 * native chunk snapshots.
 */
public final class ControllerlessMultiblockTest {
    private static Globals lua;
    private static int sequence;

    public static void main(String[] arguments) {
        System.setProperty("betamoon.runtime.side", "unknown");
        require(Block.brick != null && Item.flintAndSteel != null, "Native registries initialize");
        lua = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        ResourceApi.attach(module);
        MultiblocksApi.attach(module);
        lua.set("betamoon", module);
        lua.set("frame", ResourceApi.blockReference(Block.brick));
        lua.set("tool", ResourceApi.itemReference(Item.flintAndSteel));
        Block panel = new LuaBlock(242, 14, Material.portal, "managed_surface");
        lua.set("panel", ResourceApi.blockReference(panel));
        BlockCallbackRegistry.install(242,
                new BlockDefinition(
                        lua.load("return {" + "state={facing={type='enum',values={'north','east','south','west'}}},"
                                + "placement={facing='horizontal'},collision={boxes={}}}").call()));
        automaticAndDeactivation();
        itemAndOwnership();
        manualSearchAndActivation();
        suspensionAndRejoin();
        validationAndAmbiguity();
        callbackFailuresAndRollback();
        searchResultsAndAmbiguity();
        mirrorsAndRepeatedLayers();
        if (AgentRuntime.isRegistered()) {
            nativeItemRoute();
        }
        MultiblockRegistry.retainOwners(Collections.emptySet());
        System.out.println("Controllerless multiblocks passed: discovery, symmetry, activation, manual control, "
                + "search bounds, costs, ownership, suspension, rejoin and declarations.");
    }

    private static void define(String policy) {
        MultiblockRegistry.retainOwners(Collections.emptySet());
        lua.load("formed=0;activated=0;deactivated=0;resumed=0;invalidated=0").call();
        try (ScriptExecutionScope ignored = ScriptExecutionScope.open("controllerless_" + (++sequence) + ".lua");
                MultiblockRegistry.Batch batch = MultiblockRegistry.begin(LuaScriptRegistry.getCurrentScriptFile())) {
            lua.load("structure=betamoon.multiblocks:add {key='fixture:multiblock/frame',"
                    + "layers={{'@FF'},{'FIF'},{'FFF'}},palette={['@']=frame,F=frame,I={air=true}},"
                    + "captures={frame='@F',interior='I'},data={value={type='integer',default=0}},"
                    + "onFormed=function(c) formed=formed+1 end," + "onActivated=function(c) activated=activated+1 end,"
                    + "onDeactivated=function(c) deactivated=deactivated+1 end,"
                    + "onResumed=function(c) resumed=resumed+1 end,"
                    + "onInvalidated=function(c) invalidated=invalidated+1 end," + policy + "}").call();
            batch.publish();
        }
    }

    private static void automaticAndDeactivation() {
        define("discovery={cells='frame'},activation={mode='automatic'}");
        Fixture world = new Fixture();
        world.frame(8, 64, 8, false);
        world.tick(12);
        MultiblockInstance instance = one(world, 9, 65, 8);
        require(instance.isFormed() && instance.isActive(), "Completed structures activate automatically");
        require(lua.get("formed").toint() == 1 && lua.get("activated").toint() == 1,
                "Symmetric frame cells produce one formation/activation");
        require(instance.deactivate().reason.equals("deactivated"), "Explicit deactivation works");
        world.tick(4);
        require(instance.isFormed() && !instance.isActive(), "Deactivation preserves formation and stays inactive");
        require(instance.deactivate().reason.equals("already_inactive") && lua.get("deactivated").toint() == 1,
                "Repeated deactivation must not repeat effects");
        require(instance.activate().activated, "Automatic structures can be explicitly reactivated");
        world.edit(8, 65, 8, 0, 0);
        require(!instance.isFormed() && !instance.isActive(), "Edits revoke usability synchronously");
        world.tick(4);
        require(lua.get("invalidated").toint() == 1 && instance.expired, "Confirmed break retires the identity");
        MultiblockRuntime.unload(world);
    }

    private static void itemAndOwnership() {
        define("discovery={cells='frame'},activation={mode='item',item=tool,target={cells='frame',position='clicked'},"
                + "cost={damage=1},fill={cells='interior',block=panel,facingState='facing'}}");
        Fixture world = new Fixture();
        world.frame(8, 64, 8, false);
        Player player = new Player(world);
        ItemStack tool = new ItemStack(Item.flintAndSteel);
        player.inventory.mainInventory[0] = tool;
        world.tick(12);
        MultiblockInstance instance = one(world, 8, 65, 8);
        require(instance.isFormed() && !instance.isActive(), "Complete item-mode frame waits for activation");
        require(ItemCallbackOverrides.useOnBlock(Item.flintAndSteel, tool, player, world, 8, 65, 8, 1),
                "Native item routing handles a direct clicked-frame activation");
        require(instance.isActive() && world.getBlockId(9, 65, 8) == 242 && tool.getItemDamage() == 1,
                "Activation fills the opening and charges durability exactly once");
        ItemCallbackOverrides.useOnBlock(Item.flintAndSteel, tool, player, world, 10, 65, 8, 1);
        require(tool.getItemDamage() == 1 && lua.get("activated").toint() == 1,
                "Using another frame cell resolves the same already-active instance");
        world.edit(9, 65, 8, 20, 0);
        world.tick(4);
        require(world.getBlockId(9, 65, 8) == 20 && instance.expired,
                "Foreign replacement invalidates without erasing the player's block");
        MultiblockRuntime.unload(world);
    }

    private static void manualSearchAndActivation() {
        define("discovery={mode='manual'},activation={mode='item',item=tool,target={cells='frame'}}");
        Fixture world = new Fixture();
        world.frame(8, 64, 8, true);
        Player player = new Player(world);
        ItemStack tool = new ItemStack(Item.flintAndSteel);
        player.inventory.mainInventory[0] = tool;
        world.tick(8);
        require(ControllerlessMultiblockRuntime.at(world, 8, 65, 8, null).isEmpty(),
                "Manual mode creates no scanned instances");
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            lua.set("world", LuaWorldActionAccess.create(scope, world, 8, 65, 8));
            lua.load("found=world:findMultiblocks(structure,{near={x=8,y=65,z=8,cells='frame'},"
                    + "bounds={min={x=7,y=63,z=7},max={x=12,y=68,z=12}}});"
                    + "assert(found.complete and #found.matches==1)").call();
        }
        LuaValue found = lua.get("found").get("matches").get(1);
        LuaValue anchor = found.get("anchor");
        try (LuaItemActionContext context = new LuaItemActionContext(world, 8, 65, 8, player, tool, 1, true)) {
            lua.set("use", context);
            lua.load("local m=found.matches[1]; outcome=use:activateMultiblock(structure,"
                    + "m.anchor.x,m.anchor.y,m.anchor.z,{facing=m.facing,mirror=m.mirror,repeats=m.repeats});"
                    + "assert(outcome.activated)").call();
        }
        require(one(world, 8, 65, 8).isActive(), "Manual search and exact item activation share the managed lifecycle");
        MultiblockRuntime.unload(world);
    }

    private static void suspensionAndRejoin() {
        define("discovery={cells='frame'},activation={mode='automatic',fill={cells='interior',block=panel,facingState='facing'}}");
        Fixture world = new Fixture();
        world.frame(15, 64, 8, false);
        world.tick(25);
        MultiblockInstance instance = one(world, 16, 65, 8);
        String identity = instance.identity;
        world.remove(1, 0);
        require(!instance.isActive(), "Chunk unload immediately revokes active usability");
        world.tick(4);
        require(instance.status.equals("suspended") && instance.activationSaved(),
                "Unload preserves activation intent");
        world.reload(1, 0);
        world.tick(4);
        require(instance.isActive() && lua.get("activated").toint() == 1, "Resume does not replay activation");
        instance.deactivate();
        world.tick(4);
        MultiblockRuntime.unload(world);
        world.roundTrip();
        world.notifyLoads();
        world.tick(10);
        MultiblockInstance restored = one(world, 16, 65, 8);
        require(restored.identity.equals(identity) && restored.isFormed() && !restored.isActive(),
                "Manual inhibition and identity survive world rejoin");
        require(restored.activate().activated, "Restored inactive instance can reactivate");
        MultiblockRuntime.unload(world);
    }

    private static void validationAndAmbiguity() {
        boolean rejected = false;
        try {
            define("discovery={cells='interior'}");
        } catch (LuaError expected) {
            rejected = true;
        }
        require(rejected, "Air-only discovery seeds must be rejected");
        define("discovery={cells='frame'},activation={mode='item',item=tool,target={cells='interior',position='adjacent'}}");
        Fixture world = new Fixture();
        world.frame(8, 64, 8, false);
        Player player = new Player(world);
        ItemStack tool = new ItemStack(Item.flintAndSteel);
        player.inventory.mainInventory[0] = tool;
        require(ControllerlessMultiblockRuntime.use(world, player, tool, 9, 64, 8, 1).activated,
                "Adjacent targeting activates from an ignition-style click without placing fire");
        MultiblockRuntime.unload(world);
    }

    private static void callbackFailuresAndRollback() {
        define("discovery={cells='frame'},activation={mode='item',item=tool,target={cells='frame'},"
                + "cost={damage=1},fill={cells='interior',block=panel}},canActivate=function(c) return false end");
        Fixture denied = new Fixture();
        denied.frame(8, 64, 8, false);
        Player player = new Player(denied);
        ItemStack stack = new ItemStack(Item.flintAndSteel);
        player.inventory.mainInventory[0] = stack;
        require(ControllerlessMultiblockRuntime.use(denied, player, stack, 8, 65, 8, 1).reason.equals("denied")
                && stack.getItemDamage() == 0 && denied.getBlockId(9, 65, 8) == 0,
                "Eligibility rejection spends nothing and writes no output");
        MultiblockRuntime.unload(denied);
        define("discovery={cells='frame'},activation={mode='item',item=tool,target={cells='frame'},"
                + "cost={damage=1},fill={cells='interior',block=panel}},"
                + "onActivated=function(c) error('postcommit failure') end");
        Fixture failing = new Fixture();
        failing.frame(8, 64, 8, false);
        player = new Player(failing);
        stack = new ItemStack(Item.flintAndSteel);
        player.inventory.mainInventory[0] = stack;
        failing.failNextWrite = true;
        require(ControllerlessMultiblockRuntime.use(failing, player, stack, 8, 65, 8, 1).reason.equals("fill_failed")
                && stack.getItemDamage() == 0 && failing.getBlockId(9, 65, 8) == 0,
                "A failed fill never charges the item and preserves the original opening");
        failing.tick(3);
        require(ControllerlessMultiblockRuntime.use(failing, player, stack, 8, 65, 8, 1).activated,
                "A failed fill can be retried");
        MultiblockInstance instance = one(failing, 9, 65, 8);
        require(instance.isActive() && stack.getItemDamage() == 1 && instance.callbackError.contains("postcommit"),
                "Post-commit callback errors do not undo or duplicate activation");
        failing.edit(9, 65, 8, 0, 0);
        failing.tick(3);
        require(instance.isFormed() && !instance.isActive(), "Removing an owned panel to air clears activation");
        MultiblockRuntime.unload(failing);
    }

    private static void searchResultsAndAmbiguity() {
        define("discovery={cells='frame'},activation={mode='item',item=tool,target={cells='frame'}}");
        Fixture world = new Fixture();
        world.frame(0, 64, 0, false);
        Player player = new Player(world);
        ItemStack stack = new ItemStack(Item.flintAndSteel);
        player.inventory.mainInventory[0] = stack;
        require(ControllerlessMultiblockRuntime.use(world, player, stack, 1, 64, 0, 1).activated,
                "Known mismatches in other planes must not make border activation falsely unavailable");
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            lua.set("world", LuaWorldActionAccess.create(scope, world, 0, 65, 0));
            lua.load("local r=world:findMultiblocks(structure,{near={x=0,y=65,z=0,cells='frame'},"
                    + "bounds={min={x=8,y=64,z=8},max={x=10,y=66,z=10}}});" + "assert(r.complete and #r.matches==0)")
                    .call();
        }
        try (ScriptExecutionScope ignored = ScriptExecutionScope.open("ambiguous.lua");
                MultiblockRegistry.Batch batch = MultiblockRegistry.begin("ambiguous.lua")) {
            lua.load("other=betamoon.multiblocks:add {key='fixture:multiblock/other',"
                    + "layers={{'@FF'},{'FIF'},{'FFF'}},palette={['@']=frame,F=frame,I={air=true}},"
                    + "captures={frame='@F'},discovery={cells='frame'},"
                    + "activation={mode='item',item=tool,target={cells='frame'}}}").call();
            batch.publish();
        }
        world.tick(3);
        require(ControllerlessMultiblockRuntime.use(world, player, stack, 1, 64, 0, 1).reason.equals("ambiguous"),
                "Two different matching definitions must not pick the first registration");
        MultiblockRuntime.unload(world);
    }

    private static void mirrorsAndRepeatedLayers() {
        define("mirror=true,discovery={cells='frame',facings={'north'}},layers={{'@FI'},{'FFF'}}");
        Fixture reflected = new Fixture();
        reflected.edit(8, 64, 8, 45, 0);
        reflected.edit(7, 64, 8, 45, 0);
        for (int x = 6; x <= 8; x++) {
            reflected.edit(x, 65, 8, 45, 0);
        }
        reflected.tick(8);
        MultiblockInstance mirror = one(reflected, 8, 64, 8);
        require(mirror.isActive() && mirror.mirror, "Controllerless discovery must retain a required reflection");
        String identity = mirror.identity;
        MultiblockRuntime.unload(reflected);
        reflected.notifyLoads();
        reflected.tick(8);
        require(one(reflected, 8, 64, 8).identity.equals(identity), "Mirrored placements retain identity on rejoin");
        MultiblockRuntime.unload(reflected);
        define("discovery={cells='frame',facings={'north'}},layers={{'@I'},{'FF'},{'FI'}},"
                + "repeatLayers={first=2,last=2,min=0,max=2}");
        Fixture repeated = new Fixture();
        for (int y = 64; y <= 67; y++) {
            repeated.edit(8, y, 8, 45, 0);
        }
        repeated.edit(9, 65, 8, 45, 0);
        repeated.edit(9, 66, 8, 45, 0);
        repeated.tick(12);
        MultiblockInstance instance = one(repeated, 8, 64, 8);
        require(instance.isActive() && instance.report.repeats == 2,
                "Discovery must select the complete repeated-layer placement");
        MultiblockRuntime.unload(repeated);
    }

    private static void nativeItemRoute() {
        define("discovery={cells='frame'},activation={mode='item',item=tool,target={cells='interior',position='adjacent'},"
                + "cost={damage=1},fill={cells='interior',block=panel}}");
        Fixture world = new Fixture();
        world.frame(8, 64, 8, false);
        Player player = new Player(world);
        ItemStack stack = new ItemStack(Item.flintAndSteel);
        player.inventory.mainInventory[0] = stack;
        require(stack.useItem(player, world, 9, 64, 8, 1), "Transformed ItemStack must reach managed activation");
        require(stack.getItemDamage() == 1 && world.getBlockId(9, 65, 8) == 242 && one(world, 9, 65, 8).isActive(),
                "Native item call must fill once without spawning fire");
        MultiblockRuntime.unload(world);
    }

    private static MultiblockInstance one(Fixture world, int x, int y, int z) {
        List<MultiblockInstance> found = ControllerlessMultiblockRuntime.at(world, x, y, z, null);
        require(found.size() == 1, "Expected one canonical instance, found " + found.size());
        return found.get(0);
    }

    private static void require(boolean passed, String message) {
        if (!passed) {
            throw new AssertionError(message);
        }
    }

    private static final class Player extends EntityPlayer {
        Player(World world) {
            super(world);
        }

        public void func_6420_o() {
        }
    }

    private static final class Fixture extends World {
        private final Map<Long, Chunk> chunks = new LinkedHashMap<>();
        private final Map<Long, Chunk> unloaded = new HashMap<>();
        private final Map<String, MapDataBase> saved = new HashMap<>();
        boolean failNextWrite;

        Fixture() {
            super(null, "controllerless", new WorldProvider() {
            }, 0L);
            add(0, 0);
            add(1, 0);
        }

        protected IChunkProvider getChunkProvider() {
            return null;
        }

        void add(int x, int z) {
            Chunk chunk = new Chunk(this, new byte[32768], x, z);
            chunks.put(MultiblockPosition.chunk(x, z), chunk);
            MultiblockRuntime.chunkLoaded(chunk);
        }

        void frame(int x, int y, int z, boolean rotated) {
            for (int horizontal = 0; horizontal < 3; horizontal++) {
                for (int height = 0; height < 3; height++) {
                    if (horizontal == 0 || horizontal == 2 || height == 0 || height == 2) {
                        edit(x + (rotated ? 0 : horizontal), y + height, z + (rotated ? horizontal : 0), 45, 0);
                    }
                }
            }
        }

        void edit(int x, int y, int z, int id, int metadata) {
            Chunk chunk = getChunkFromChunkCoords(x >> 4, z >> 4);
            int old = MultiblockChunkCallbacks.state(chunk, x & 15, y, z & 15, id, metadata);
            chunk.blocks[(x & 15) << 11 | (z & 15) << 7 | y] = (byte) id;
            chunk.setBlockMetadata(x & 15, y, z & 15, metadata);
            MultiblockChunkCallbacks.changed(chunk, x & 15, y, z & 15, old);
        }

        void tick(int count) {
            for (int index = 0; index < count; index++) {
                MultiblockRuntime.tick(this);
            }
        }

        void remove(int x, int z) {
            long key = MultiblockPosition.chunk(x, z);
            Chunk chunk = chunks.remove(key);
            unloaded.put(key, chunk);
            MultiblockRuntime.chunkUnloaded(chunk);
        }

        void reload(int x, int z) {
            long key = MultiblockPosition.chunk(x, z);
            Chunk chunk = unloaded.remove(key);
            chunks.put(key, chunk);
            MultiblockRuntime.chunkLoaded(chunk);
        }

        void roundTrip() {
            for (Map.Entry<String, MapDataBase> entry : new ArrayList<>(saved.entrySet())) {
                NBTTagCompound tag = new NBTTagCompound();
                entry.getValue().writeToNBT(tag);
                try {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    NBTBase.writeTag(tag, new DataOutputStream(bytes));
                    MultiblockWorldData restored = new MultiblockWorldData(entry.getKey());
                    restored.readFromNBT((NBTTagCompound) NBTBase
                            .readTag(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
                    saved.put(entry.getKey(), restored);
                } catch (IOException error) {
                    throw new AssertionError(error);
                }
            }
        }

        void notifyLoads() {
            for (Chunk chunk : chunks.values()) {
                MultiblockRuntime.chunkLoaded(chunk);
            }
        }

        public boolean blockExists(int x, int y, int z) {
            return y >= 0 && y < 128 && chunks.containsKey(MultiblockPosition.chunk(x >> 4, z >> 4));
        }

        public Chunk getChunkFromChunkCoords(int x, int z) {
            Chunk chunk = chunks.get(MultiblockPosition.chunk(x, z));
            if (chunk == null) {
                throw new AssertionError("Must not load unavailable chunks");
            }
            return chunk;
        }

        public int getBlockId(int x, int y, int z) {
            return getChunkFromChunkCoords(x >> 4, z >> 4).getBlockID(x & 15, y, z & 15);
        }

        public int getBlockMetadata(int x, int y, int z) {
            return getChunkFromChunkCoords(x >> 4, z >> 4).getBlockMetadata(x & 15, y, z & 15);
        }

        public boolean setBlockAndMetadataWithNotify(int x, int y, int z, int id, int metadata) {
            if (failNextWrite) {
                failNextWrite = false;
                throw new IllegalStateException("simulated write failure");
            }
            edit(x, y, z, id, metadata);
            return true;
        }

        public MapDataBase loadItemData(Class type, String name) {
            return saved.get(name);
        }

        public void setItemData(String name, MapDataBase data) {
            saved.put(name, data);
        }
    }
}
