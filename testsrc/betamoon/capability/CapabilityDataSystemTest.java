package betamoon.capability;

import betamoon.luaapi.capability.CapabilitiesApi;
import betamoon.luaapi.networking.LogicalNetworksApi;
import betamoon.luaapi.system.SystemsApi;
import betamoon.luaapi.tileentity.TileEntityApi;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.data.DataSchema;
import betamoon.data.DataStore;
import betamoon.networking.LogicalNetworkRuntime;
import betamoon.system.WorldServiceRuntime;
import betamoon.tileentity.LuaTileEntity;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/** End-to-end contract checks for structured tile data, capabilities, services, and logical networks. */
public final class CapabilityDataSystemTest {
    private CapabilityDataSystemTest() {
    }

    public static void main(String[] arguments) throws Exception {
        Method mapping = TileEntity.class.getDeclaredMethod("addMapping", Class.class, String.class);
        mapping.setAccessible(true);
        java.lang.reflect.Field registrationHook = net.minecraft.src.ModLoader.class
                .getDeclaredField("method_RegisterTileEntity");
        registrationHook.setAccessible(true);
        registrationHook.set(null, mapping);
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "capability_data_test.lua");
        Globals globals = JsePlatform.standardGlobals();
        LuaTable api = new LuaTable();
        CapabilitiesApi.attach(api);
        TileEntityApi.attach(api);
        SystemsApi.attach(api);
        LogicalNetworksApi.attach(api);
        globals.set("betamoon", api);
        globals.load(script(), "capability_data_test.lua").call();
        owner.invoke(null, new Object[]{null});
        testStructuredDataFoundation(globals);

        TestWorld world = new TestWorld();
        LuaTileEntity sender = tile("test:block/sender", world, 0, 64, 0);
        LuaTileEntity receiver = tile("test:block/receiver", world, 1, 64, 0);
        world.put(sender);
        world.put(receiver);

        LuaValue escapedInventory;
        try (LuaTileEntity.Context context = sender.createScopedContext()) {
            globals.set("ctx", context);
            globals.load("local c=ctx.entity.capabilities:getRequired(energy)\n"
                    + "assert(c.data:get('stored') == 0)\n"
                    + "local r=c:call('receive',{amount=40})\n"
                    + "assert(r.accepted == 40 and c.data:get('stored') == 40)\n"
                    + "local s=c:call('status',{})\n"
                    + "assert(s.stored == 40 and s.capacity == 100)\n"
                    + "ctx.entity.data:set('profile',{label='sender', samples={2,4,6}})\n"
                    + "ctx.entity.networks:getRequired(explicit):link('receiver')\n"
                    + "ctx.entity.networks:getRequired(wireless):publish(7)\n"
                    + "ctx.entity.networks:getRequired(wireless):pulse(9)").call();
            escapedInventory = context.get("entity").get("inventory");
        }
        globals.set("escapedInventory", escapedInventory);
        expectLuaError(() -> globals.load("escapedInventory:get('buffer')").call());

        NBTTagCompound saved = new NBTTagCompound();
        sender.writeToNBT(saved);
        LuaTileEntity restored = new LuaTileEntity();
        restored.readFromNBT(saved);
        restored.worldObj = world;
        require("sender".equals(restored.snapshotData().get("profile").get("label").tojstring()),
                "Structured tile data did not persist");
        require(restored.getCapability(CapabilitiesApi.definition(globals.get("energy"), "energy").key)
                .data.get("stored").checkint() == 40, "Capability state did not persist");

        try (LuaTileEntity.Context context = sender.createScopedContext()) {
            globals.set("ctx", context);
            expectLuaError(() -> globals.load("ctx.entity.capabilities:getRequired(energy):call('badQuery',{})").call());
            require(sender.getCapability(CapabilitiesApi.definition(globals.get("energy"), "energy").key)
                    .data.get("stored").checkint() == 40, "Failed query partially mutated capability state");
        }

        LogicalNetworkRuntime.added(sender);
        LogicalNetworkRuntime.added(receiver);
        LogicalNetworkRuntime.tick(world);
        require(globals.get("adjacentTicks").checkint() == 1 && globals.get("adjacentSize").checkint() == 2,
                "Adjacent network did not build one deterministic component");
        require(globals.get("wirelessTicks").checkint() == 1 && globals.get("wirelessSize").checkint() == 2,
                "Wireless network did not join compatible channel endpoints");
        require(globals.get("explicitTicks").checkint() == 1 && globals.get("explicitSize").checkint() == 2,
                "Explicit network did not join linked endpoint identifiers");
        require(globals.get("hybridTicks").checkint() == 1 && globals.get("hybridSize").checkint() == 2,
                "Hybrid network did not compose its topology providers");
        require(globals.get("wirelessSignal").checkint() == 7 && globals.get("pulseValue").checkint() == 9,
                "Wireless state and pulse delivery changed");

        LogicalNetworkRuntime.unload(world);
        world.remove(sender);
        LogicalNetworkRuntime.load(world);
        globals.set("wirelessSignal", LuaValue.ZERO);
        LogicalNetworkRuntime.tick(world);
        require(globals.get("wirelessSignal").checkint() == 7,
                "Retained wireless state did not survive transmitter unload and runtime reload");
        world.put(sender);
        LogicalNetworkRuntime.added(sender);
        LogicalNetworkRuntime.destroyed(sender);
        world.remove(sender);
        globals.set("wirelessSignal", LuaValue.valueOf(99));
        LogicalNetworkRuntime.tick(world);
        require(globals.get("wirelessSignal").checkint() == 0,
                "Destroyed transmitter left retained wireless state behind");

        WorldServiceRuntime.load(world);
        WorldServiceRuntime.tick(world);
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable worldApi = LuaWorldActionAccess.create(scope, world, 0, 64, 0);
            globals.set("worldApi", worldApi);
            globals.load("local data=worldApi:getSystemData(powerSystem)\n"
                    + "assert(data.loads == 1 and data.ticks == 1)\n"
                    + "local info=worldApi:getInfo()\n"
                    + "assert(info.day == 0 and info.timeOfDay == 0 and info.height == 128)\n"
                    + "assert(type(info.celestialAngle) == 'number' and type(info.daytime) == 'boolean')\n"
                    + "assert(not pcall(function() info.day=4 end))\n"
                    + "assert(not pcall(function() rawset(info,'day',4) end))").call();
        }

        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable worldApi = LuaWorldActionAccess.create(scope, world, 0, 64, 0);
            globals.set("worldApi", worldApi);
            LuaValue proxy = globals.load("return worldApi:getCapability({x=1,y=64,z=0},energy,'west')").call();
            require(!proxy.isnil(), "External capability discovery failed");
            world.tiles.remove("1,64,0");
            globals.set("external", proxy);
            expectLuaError(() -> globals.load("external:call('status',{})").call());
        }

        WorldServiceRuntime.unload(world);
        System.out.println("Capability/data system passed: structured persistence, typed calls, rollback, services, "
                + "all topology modes, retained signals, pulses, and stale handles.");
    }

    private static LuaTileEntity tile(String type, TestWorld world, int x, int y, int z) {
        LuaTileEntity tile = new LuaTileEntity(type);
        tile.worldObj = world;
        tile.xCoord = x;
        tile.yCoord = y;
        tile.zCoord = z;
        return tile;
    }

    private static String script() {
        return "energy=betamoon.capabilities:add{key='test:capability/energy',"
                + "state={stored={type='integer',default=0}},"
                + "config={capacity={type='integer'},role={type='string'},channel={type='string'},"
                + "owner={type='string'},range={type='number'},endpoint={type='string'}},"
                + "operations={"
                + "receive={mode='action',request={amount={type='integer'}},"
                + "response={accepted={type='integer'}}},"
                + "status={mode='query',response={stored={type='integer'},capacity={type='integer'}}},"
                + "badQuery={mode='query',response={stored={type='integer'}}}}}\n"
                + "local function tile(name,role,endpoint) return betamoon.tileEntities:add{"
                + "name=name,"
                + "data={profile={type='record',fields={label={type='string'},"
                + "samples={type='list',element={type='integer'},maxLength=8}}}},"
                + "capabilities={{capability=energy,config={capacity=100,role=role,channel='factory',"
                + "owner='team',range=32,endpoint=endpoint},ports={east='both',west='both'},operations={"
                + "receive=function(ctx,r) local old=ctx.capability.data:get('stored') "
                + "local accepted=math.min(r.amount,ctx.capability.config.capacity-old) "
                + "ctx.capability.data:set('stored',old+accepted) return {accepted=accepted} end,"
                + "status=function(ctx) return {stored=ctx.capability.data:get('stored'),"
                + "capacity=ctx.capability.config.capacity} end,"
                + "badQuery=function(ctx) ctx.capability.data:set('stored',99) return {stored=99} end}}}} end\n"
                + "senderType=tile('test:block/sender','transmitter','sender')\n"
                + "receiverType=tile('test:block/receiver','receiver','receiver')\n"
                + "adjacentTicks=0 adjacentSize=0 wirelessTicks=0 wirelessSize=0 wirelessSignal=0 pulseValue=0 "
                + "explicitTicks=0 explicitSize=0 hybridTicks=0 hybridSize=0\n"
                + "adjacent=betamoon.logicalNetworks:add{key='test:network/cable',capability=energy,"
                + "topology={type='adjacent',directions='orthogonal'},tick={interval=1},"
                + "onTick=function(ctx) adjacentTicks=adjacentTicks+1 adjacentSize=ctx.network.size end}\n"
                + "wireless=betamoon.logicalNetworks:add{key='test:network/wireless',capability=energy,"
                + "topology={type='wireless',scope='dimension',range=64,channelField='channel',"
                + "roleField='role',rangeField='range',compatibilityFields={'owner'}},"
                + "signal={mode='both',value={type='integer',default=0},aggregate='maximum',"
                + "retainWithoutTransmitters=true},"
                + "tick={interval=1},onTick=function(ctx) wirelessTicks=wirelessTicks+1 "
                + "wirelessSize=ctx.network.size wirelessSignal=ctx.signal or 0 end,"
                + "onPulse=function(ctx) pulseValue=ctx.pulse end}\n"
                + "explicit=betamoon.logicalNetworks:add{key='test:network/explicit',capability=energy,"
                + "topology={type='explicit',endpointField='endpoint'},tick={interval=1},"
                + "onTick=function(ctx) explicitTicks=explicitTicks+1 explicitSize=ctx.network.size end}\n"
                + "hybrid=betamoon.logicalNetworks:add{key='test:network/hybrid',capability=energy,"
                + "topology={type='hybrid',channelField='channel',roleField='role',endpointField='endpoint'},"
                + "tick={interval=1},onTick=function(ctx) hybridTicks=hybridTicks+1 hybridSize=ctx.network.size end}\n"
                + "powerSystem=betamoon.systems:add{key='test:system/power',"
                + "data={loads={type='integer',default=0},ticks={type='integer',default=0}},"
                + "tick={interval=1},onLoad=function(ctx) ctx.data:set('loads',ctx.data:get('loads')+1) end,"
                + "onTick=function(ctx) ctx.data:set('ticks',ctx.data:get('ticks')+1) end}";
    }

    private static void testStructuredDataFoundation(Globals globals) {
        LuaTable declarations = globals.load("return {position={type='block_position'},"
                + "key={type='content_key'},values={type='list',element={type='integer'},maxLength=3}}").call()
                .checktable();
        DataSchema schema = DataSchema.parse(declarations, "test.data");
        DataStore store = new DataStore(schema, null);
        store.set("position", globals.load("return {x=12,y=64,z=-4,dimension=2}").call(), true);
        store.set("key", LuaValue.valueOf("test:block/machine"), true);
        store.set("values", globals.load("return {1,2,3}").call(), true);
        store.raw().setString("UnknownFutureField", "preserved");

        DataStore restored = new DataStore(schema, null);
        restored.load(store.raw());
        require("preserved".equals(restored.raw().getString("UnknownFutureField")),
                "Unknown structured-data fields were discarded");
        require(restored.get("position").get("dimension").checkint() == 2,
                "Block-position data did not round-trip through NBT");
        expectLuaError(() -> restored.set("key", LuaValue.valueOf("Invalid Key"), true));
        expectLuaError(() -> restored.set("values", globals.load("return {1,2,3,4}").call(), true));

        NBTTagCompound incompatible = new NBTTagCompound();
        incompatible.setString("position", "wrong-type");
        DataStore unavailable = new DataStore(schema, null);
        unavailable.load(incompatible);
        require(unavailable.error() != null, "An incompatible saved field was silently coerced");
    }

    private static void expectLuaError(Runnable operation) {
        try {
            operation.run();
        } catch (LuaError expected) {
            return;
        }
        throw new AssertionError("Expected a Lua error");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestWorld extends World {
        private final Map<String, TileEntity> tiles = new HashMap<>();
        private TestWorld() {
            super(null, "capability_data_test", new WorldProvider() {
            }, 0L);
        }
        protected IChunkProvider getChunkProvider() {
            return null;
        }
        private void put(LuaTileEntity tile) {
            tiles.put(key(tile.xCoord, tile.yCoord, tile.zCoord), tile);
            loadedTileEntityList.add(tile);
        }
        private void remove(LuaTileEntity tile) {
            tiles.remove(key(tile.xCoord, tile.yCoord, tile.zCoord));
            loadedTileEntityList.remove(tile);
        }
        public boolean blockExists(int x, int y, int z) {
            return y >= 0 && y < 128;
        }
        public TileEntity getBlockTileEntity(int x, int y, int z) {
            return tiles.get(key(x, y, z));
        }
        public int getBlockId(int x, int y, int z) {
            return 1;
        }
        public int getBlockMetadata(int x, int y, int z) {
            return 0;
        }
        public void func_698_b(int x, int y, int z, TileEntity entity) {
        }
        private static String key(int x, int y, int z) {
            return x + "," + y + "," + z;
        }
    }
}
