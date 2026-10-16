package betamoon.luamodloader;

import betamoon.multiblock.MultiblockDefinition;
import betamoon.multiblock.MultiblockRuntime;
import betamoon.multiblock.MultiblockMatchJob;
import betamoon.multiblock.MultiblockReport;
import betamoon.multiblock.MultiblockPosition;
import betamoon.multiblock.MultiblockRegistry;
import betamoon.multiblock.MultiblockWorldData;
import betamoon.multiblock.MultiblockLimits;
import betamoon.multiblock.MultiblockOrientation;

import betamoon.instrumentation.hooks.multiblock.MultiblockChunkCallbacks;
import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.BlockDefinition;
import betamoon.luaapi.block.LuaBlock;
import betamoon.luaapi.multiblock.MultiblockDeclarations;
import betamoon.luaapi.multiblock.MultiblocksApi;
import betamoon.luaapi.resource.ResourceApi;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecutionScope;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.MapDataBase;
import net.minecraft.src.Material;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.Varargs;
import betamoon.luaapi.world.LuaWorldActionAccess;

/**
 * Real Lua declarations and native chunk snapshots exercise matcher and
 * lifecycle contracts together.
 */
public final class MultiblockApiTest {
    private static final String OWNER = "multiblocks/main.lua";
    private static Globals lua;

    private MultiblockApiTest() {
    }

    public static void main(String[] args) {
        System.setProperty("betamoon.runtime.side", "unknown");

        lua = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        ResourceApi.attach(module);
        MultiblocksApi.attach(module);
        lua.set("betamoon", module);
        Block controller = new LuaBlock(240, 1, Material.rock, "multiblock_fixture");
        lua.set("controller", ResourceApi.blockReference(controller));
        lua.set("stone", ResourceApi.blockReference(Block.stone));
        lua.set("glass", ResourceApi.blockReference(Block.glass));
        try (ScriptExecutionScope ignored = ScriptExecutionScope.open(OWNER)) {
            validation();
            rotationsAndMetadata();
            reportsAndConstraints();
            repeatsAndSparse();
            lifecycle();
            exclusivityAndReload();
            selfMutationAndQueries();
            incrementalAndProfile();
            manyControllersProfile();
            longRunningJob();
            dimensionIsolation();
            if (AgentRuntime.isRegistered()) {
                nativeMetadataObserver();
            }
            System.out.println("Multiblock API passed: strict compilation, transforms, loaded-only matching, "
                    + "constraints, repeats, lifecycle, persistence, reload, scopes, exclusivity and bounded work.");
        } finally {
            MultiblockRegistry.retainOwners(Collections.emptySet());

        }
    }

    private static MultiblockDefinition parse(String fields) {
        return MultiblockDeclarations.parse(lua.load("return {key='test:multiblock/check'," + fields + "}").call());
    }

    private static void validation() {
        String base = "facing='north',palette={['@']=stone,C=stone},";
        for (String layers : new String[]{"{}", "{{''}}", "{{'@@'}}", "{{'CC'}}", "{{'@C','C'}}", "{{'@X'}}",
                "{{'@ '}}", "{{'@é'}}"}) {
            expectError(() -> parse(base + "layers=" + layers));
        }
        expectError(() -> parse(base + "layers={{'@'}},typo=true"));
        expectError(() -> parse("controller=controller,layers={{'@'}}"));
        expectError(() -> parse(base + "layers={{'@C'}},constraints={{cells='X',rule=stone}}"));
        expectError(() -> parse("facing='north',palette={['@']=stone,C={block=stone,damage=1.5}},layers={{'@C'}}"));
        expectError(() -> parse("facing='north',palette={['@']=stone,C={block=stone,bad=true}},layers={{'@C'}}"));
        lua.load("cycle={notRule=false};cycle.notRule=cycle").call();
        expectError(() -> parse("palette={['@']=cycle},layers={{'@'}}"));
        expectError(() -> parse("palette={['@']={id=1,exists=true}},layers={{'@'}}"));
        expectError(() -> parse("palette={['@']=betamoon.items:getRequired(1)},layers={{'@'}}"));
        expectError(() -> parse(base + "layers={{'@'},{'C'}},repeatLayers={first=1,last=1,min=1,max=2}"));
        expectError(() -> parse(base + "cells={{x=0,y=0,z=0,symbol='@'},{x=0,y=0,z=0,symbol='C'}}"));
    }

    private static MultiblockReport check(MultiblockDefinition definition, Fixture world, int x, int y, int z,
            int facing, boolean mirror, Integer repeats) {
        MultiblockMatchJob job = new MultiblockMatchJob(definition, MultiblockRuntime.reader(world), x, y, z, facing,
                mirror, true, repeats);
        while (!job.done()) {
            int spent = job.advance(7);
            require(spent <= 7, "A job exceeded its visit slice");
        }
        return job.report();
    }

    private static void rotationsAndMetadata() {
        MultiblockDefinition pattern = parse("controller=controller,facing='north',mirror=true,"
                + "layers={{'@C','.?'}},palette={C={block=stone,damage=3}}");
        Fixture world = new Fixture();
        world.add(0, 0);
        for (int facing = 0; facing < 4; facing++) {
            for (boolean mirror : new boolean[]{false, true}) {
                world.clear();
                world.raw(8, 64, 8, 240, 0);
                int x = 8 + MultiblockOrientation.x(1, 0, facing, mirror);
                int z = 8 + MultiblockOrientation.z(1, 0, facing, mirror);
                world.raw(x, 64, z, 1, 3);
                require(check(pattern, world, 8, 64, 8, facing, mirror, null).status.equals("complete"),
                        "Asymmetric rotation/mirror failed");
                world.raw(x, 64, z, 1, 2);
                require(check(pattern, world, 8, 64, 8, facing, mirror, null).status.equals("incomplete"),
                        "Raw metadata must remain literal");
            }
        }
        BlockDefinition state = new BlockDefinition(
                lua.load("return {state={facing={type='enum'," + "values={'north','east','south','west'}}}}").call());
        BlockCallbackRegistry.install(240, state);
        MultiblockDefinition semantic = parse("controller=controller,layers={{'@C'}},"
                + "palette={C={block=controller,state={facing='east'}}},mirror=true");
        for (int facing = 0; facing < 4; facing++) {
            world.clear();
            world.raw(8, 64, 8, 240, facing);
            world.raw(8 + MultiblockOrientation.x(1, 0, facing, false), 64,
                    8 + MultiblockOrientation.z(1, 0, facing, false), 240, (facing + 1) % 4);
            require(check(semantic, world, 8, 64, 8, facing, false, null).status.equals("complete"),
                    "Semantic facing must rotate with the pattern");
        }
        MultiblockRuntime.unload(world);
    }

    private static void reportsAndConstraints() {
        Fixture world = new Fixture();
        world.add(0, 0);
        world.raw(15, 64, 4, 1, 0);
        MultiblockDefinition ignored = parse("layers={{'@?'}},palette={['@']=stone}");
        require(check(ignored, world, 15, 64, 4, 0, false, null).status.equals("complete"),
                "Ignored cells must not require neighboring chunks");
        MultiblockDefinition air = parse("layers={{'@.'}},palette={['@']=stone}");
        require(check(air, world, 15, 64, 4, 0, false, null).status.equals("unavailable"),
                "Unloaded cells must never become air");
        MultiblockDefinition ambiguous = parse(
                "layers={{'@?'},{'?C'}},palette={['@']=stone,C=stone}," + "repeatLayers={first=2,last=2,min=0,max=1}");
        require(check(ambiguous, world, 15, 64, 4, 0, false, null).status.equals("unavailable"),
                "An unavailable preferred repeat must not form a shorter pattern");
        require(check(ambiguous, world, 15, 64, 4, 0, false, 0).status.equals("complete"),
                "An explicit zero repeat must not read the omitted layer");
        world.add(1, 0);
        require(check(air, world, 15, 64, 4, 0, false, null).status.equals("complete"), "Loaded air must match");
        MultiblockDefinition height = parse("layers={{'@'},{'.'}},palette={['@']=stone}");
        require(check(height, world, 15, 127, 4, 0, false, null).reason.equals("out_of_bounds"),
                "World-height errors must be concrete mismatches");
        require(check(height, world, 15, -1, 4, 0, false, null).status.equals("incomplete"),
                "An invalid anchor height must be a mismatch without a chunk read");
        MultiblockDefinition counts = parse("layers={{'@CC'}},palette={['@']=stone,"
                + "C={anyOf={stone,glass}}},constraints={{cells='C',rule=stone,min=2,label='casing'}}");
        world.raw(16, 64, 4, 1, 0);
        world.raw(17, 64, 4, 20, 0);
        MultiblockReport report = check(counts, world, 15, 64, 4, 0, false, null);
        require(report.status.equals("incomplete") && report.constraints.get(0).found == 1,
                "Count constraints must not turn OR into a child count limit");
        MultiblockDefinition consistent = parse("layers={{'@CC'}},palette={['@']=stone,"
                + "C={anyOf={stone,glass}}},constraints={{cells='C',allSame='block'}}");
        require(check(consistent, world, 15, 64, 4, 0, false, null).status.equals("incomplete"),
                "Mixed materials must fail allSame");
        String row = "@CCCCCCCCCCCCCCC";
        String rows = "{'" + row + "','CCCCCCCCCCCCCCCC','CCCCCCCCCCCCCCCC','CCCCCCCCCCCCCCCC','CCCCCCCCCCCCCCCC'}";
        MultiblockDefinition many = parse("layers={" + rows + "},palette={['@']=stone,C=glass}");
        report = check(many, world, 0, 64, 0, 0, false, null);
        require(report.truncated && report.issues.size() == 64, "Detailed issue lists must be bounded and marked");
        MultiblockRuntime.unload(world);
    }

    private static void repeatsAndSparse() {
        Fixture world = new Fixture();
        world.add(0, 0);
        world.raw(4, 64, 4, 1, 0);
        world.raw(4, 65, 4, 1, 0);
        world.raw(4, 66, 4, 1, 0);
        MultiblockDefinition repeat = parse(
                "layers={{'@'},{'C'}},palette={['@']=stone,C=stone}," + "repeatLayers={first=2,last=2,min=1,max=3}");
        MultiblockReport report = check(repeat, world, 4, 64, 4, 0, false, null);
        require(report.status.equals("complete") && report.repeats == 2, "Repeats must prefer the longest match");
        require(check(repeat, world, 4, 64, 4, 0, false, 1).repeats == 1, "Explicit repeats must avoid size search");
        MultiblockDefinition sparse = parse("cells={{x=0,y=0,z=0,symbol='@'},"
                + "{x=-3,y=2,z=4,symbol='C'}},palette={['@']=stone,C=glass},captures={pylons='C'}");
        world.raw(1, 66, 8, 20, 0);
        require(check(sparse, world, 4, 64, 4, 0, false, null).status.equals("complete"),
                "Sparse offsets must use the same matcher");
        MultiblockRuntime.unload(world);
    }

    private static void publish(String body) {
        try (MultiblockRegistry.Batch batch = MultiblockRegistry.begin(OWNER)) {
            lua.load(body).call();
            batch.publish();
        }
    }

    private static String managed(String extra) {
        return "m=betamoon.multiblocks:add{key='test:multiblock/machine',controller=controller,facing='north',"
                + "layers={{'@?C'}},palette={C={anyOf={stone,glass}}},data={runs={type='integer',default=0}},"
                + "onFormed=function(ctx) formed=formed+1; retained=ctx.multiblock; "
                + "ctx.data:set('runs',ctx.data:get('runs')+1); if mutate then mutate() end end,"
                + "onInvalidated=function(ctx) invalidated=invalidated+1; removedReason=ctx.reason end,"
                + "onSuspended=function(ctx) suspended=suspended+1 end,"
                + "onResumed=function(ctx) resumed=resumed+1 end" + extra + "}";
    }

    private static void lifecycle() {
        lua.load("formed=0;invalidated=0;suspended=0;resumed=0").call();
        publish(managed(""));
        Fixture world = new Fixture();
        world.add(0, 0);
        world.add(1, 0);
        world.edit(14, 64, 3, 240, 0);
        MultiblockRuntime.tick(world);
        MultiblockRuntime.Instance instance = MultiblockRuntime.get(world, 14, 64, 3);
        require(instance != null && !instance.isFormed(), "An incomplete controller must be watched");
        world.edit(16, 64, 3, 1, 0);
        MultiblockRuntime.tick(world);
        require(instance.isFormed() && lua.get("formed").toint() == 1, "Distant final block must complete structure");
        expectError(() -> lua.get("retained").get("isFormed").call());
        world.edit(16, 64, 3, 20, 0);
        require(!instance.isFormed(), "Relevant edits must immediately invalidate cached usability");
        MultiblockRuntime.tick(world);
        require(instance.isFormed() && lua.get("formed").toint() == 1 && lua.get("invalidated").toint() == 0,
                "Valid-to-valid replacement must not spam lifecycle callbacks");
        world.edit(16, 64, 3, 0, 0);
        MultiblockRuntime.tick(world);
        require(!instance.isFormed() && lua.get("invalidated").toint() == 1, "Distant break must invalidate");
        world.edit(16, 64, 3, 1, 0);
        MultiblockRuntime.tick(world);
        world.remove(1, 0);
        require(!instance.isFormed(), "Chunk unload must invalidate the cached gate immediately");
        MultiblockRuntime.tick(world);
        require(instance.status.equals("suspended") && lua.get("suspended").toint() == 1,
                "Usable structures must suspend while required chunks are absent");
        world.reload(1, 0);
        MultiblockRuntime.tick(world);
        require(instance.isFormed() && lua.get("resumed").toint() == 1, "Returning chunks must resume");
        String identity = instance.identity;
        MultiblockWorldData saved = (MultiblockWorldData) world.saved.get("betamoon_multiblocks_0");
        NBTTagCompound nbt = new NBTTagCompound();
        saved.writeToNBT(nbt);
        MultiblockWorldData restored = new MultiblockWorldData("betamoon_multiblocks_0");
        restored.readFromNBT(nbt);
        MultiblockRuntime.unload(world);
        world.saved.put("betamoon_multiblocks_0", restored);
        world.notifyLoads();
        MultiblockRuntime.Instance rejoined = MultiblockRuntime.get(world, 14, 64, 3);
        require(!rejoined.isFormed() && rejoined.identity.equals(identity), "Saved formation must not be trusted");
        MultiblockRuntime.tick(world);
        require(rejoined.isFormed() && lua.get("resumed").toint() == 2 && rejoined.data.get("runs").toint() == 2,
                "Rejoin must preserve identity/data and avoid rewards");
        world.edit(14, 64, 3, 0, 0);
        world.edit(14, 64, 3, 240, 0);
        MultiblockRuntime.tick(world);
        MultiblockRuntime.Instance replacement = MultiblockRuntime.get(world, 14, 64, 3);
        require(!replacement.identity.equals(identity), "Removal/replacement must create a distinct identity");
        expectState(rejoined::requireCurrent);
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaValue access = MultiblocksApi.instance(scope, replacement, false);
            LuaValue matches = access.get("getMatches").call(LuaValue.valueOf("C"));
            require(matches.length() == 1 && matches.get(1).get("x").toint() == 16,
                    "Matches must be detached coordinates");
            matches.get(1).set("x", 99);
            require(access.get("getMatches").call(LuaValue.valueOf("C")).get(1).get("x").toint() == 16,
                    "Mutating a returned match must not corrupt cached membership");
        }
        MultiblockRuntime.unload(world);
    }

    private static void exclusivityAndReload() {
        lua.load("formed=0;invalidated=0;suspended=0;resumed=0").call();
        publish(managed(",exclusive=true"));
        Fixture world = new Fixture();
        world.add(0, 0);
        world.raw(4, 64, 4, 240, 0);
        world.raw(6, 64, 4, 1, 0);
        MultiblockRuntime.Instance instance = MultiblockRuntime.get(world, 4, 64, 4);
        MultiblockRuntime.tick(world);
        require(instance.isFormed(), "Fixture must form");
        try (MultiblockRegistry.Batch batch = MultiblockRegistry.begin(OWNER)) {
            lua.load("betamoon.multiblocks:add{key='test:multiblock/machine',controller=controller,"
                    + "facing='north',layers={{'@'}}}").call();
            // No publish models an initialization failure.
        }
        require(instance.isFormed(), "Failed publication must retain the previous active definition and instance");
        publish(managed(",exclusive=true"));
        expectState(instance::requireCurrent);
        MultiblockRuntime.tick(world);
        MultiblockRuntime.Instance reloaded = MultiblockRuntime.get(world, 4, 64, 4);
        require(reloaded.isFormed() && lua.get("formed").toint() == 1,
                "Reload must not grant another formation reward");
        try (MultiblockRegistry.Batch batch = MultiblockRegistry.begin(OWNER)) {
            lua.load(managed("") + " betamoon.multiblocks:add{key='test:multiblock/duplicate',"
                    + "controller=controller,facing='north',layers={{'@'}}}").call();
            expectState(batch::validate);
        }
        MultiblockRuntime.unload(world);

        publish(managed(",exclusive=true").replace("facing='north',", ""));
        Fixture overlap = new Fixture();
        overlap.add(0, 0);
        overlap.raw(4, 64, 4, 240, 0);
        overlap.raw(6, 64, 4, 1, 0);
        overlap.raw(8, 64, 4, 240, 2);
        MultiblockRuntime.Instance first = MultiblockRuntime.get(overlap, 4, 64, 4);
        MultiblockRuntime.Instance second = MultiblockRuntime.get(overlap, 8, 64, 4);
        MultiblockRuntime.tick(overlap);
        require(first.isFormed() && !second.isFormed() && second.conflict.equals(first.position),
                "Exclusive candidates in the same batch must use stable anchor ordering");
        overlap.edit(4, 64, 4, 0, 0);
        MultiblockRuntime.tick(overlap);
        require(second.isFormed(), "Releasing an exclusive claim must wake conflicting candidates");
        MultiblockRuntime.unload(overlap);

        publish(managed(",mirror=true"));
        Fixture reflected = new Fixture();
        reflected.add(0, 0);
        reflected.raw(4, 64, 4, 240, 0);
        reflected.raw(2, 64, 4, 1, 0);
        MultiblockRuntime.Instance mirrored = MultiblockRuntime.get(reflected, 4, 64, 4);
        MultiblockRuntime.tick(reflected);
        require(mirrored.isFormed() && mirrored.mirror, "Managed opt-in mirrors must match and report reflection");
        reflected.edit(2, 64, 4, 0, 0);
        require(!mirrored.isFormed(), "Mirrored member coordinates must be included in dependency tracking");
        MultiblockRuntime.unload(reflected);
    }

    private static void selfMutationAndQueries() {
        lua.load("formed=0;invalidated=0;suspended=0;resumed=0").call();
        publish(managed(""));
        Fixture world = new Fixture();
        world.add(0, 0);
        world.raw(4, 64, 4, 240, 0);
        world.raw(6, 64, 4, 1, 0);
        lua.set("mutate", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                world.edit(6, 64, 4, 0, 0);
                return NONE;
            }
        });
        MultiblockRuntime.Instance instance = MultiblockRuntime.get(world, 4, 64, 4);
        MultiblockRuntime.tick(world);
        require(!instance.isFormed() && lua.get("formed").toint() == 1 && lua.get("invalidated").toint() == 0,
                "Self-mutation must revoke usability without recursive lifecycle calls");
        MultiblockRuntime.tick(world);
        require(lua.get("invalidated").toint() == 1, "Self-mutation must recheck at the next boundary");
        lua.set("mutate", LuaValue.NIL);
        publish(managed("").replace("formed=formed+1;", "formed=formed+1; error('expected callback failure');"));
        world.edit(6, 64, 4, 1, 0);
        MultiblockRuntime.tick(world);
        instance = MultiblockRuntime.get(world, 4, 64, 4);
        require(instance.isFormed() && instance.callbackError != null,
                "Lua errors must not corrupt verified physical state");
        try (LuaCallbackScope scope = new LuaCallbackScope(false)) {
            LuaTable api = LuaWorldActionAccess.create(scope, world, 4, 64, 4);
            LuaValue result = api.get("checkMultiblock").invoke(LuaValue.varargsOf(
                    new LuaValue[]{lua.get("m"), LuaValue.valueOf(4), LuaValue.valueOf(64), LuaValue.valueOf(4)}))
                    .arg1();
            require(result.get("status").tojstring().equals("complete"), "World check must expose detached reports");
            LuaValue access = api.get("getMultiblock").call(LuaValue.valueOf(4), LuaValue.valueOf(64),
                    LuaValue.valueOf(4));
            expectError(() -> access.get("requestCheck").call());
        }
        world.multiplayerWorld = true;
        require(MultiblockRuntime.get(world, 4, 64, 4) == null,
                "Remote worlds must not expose authoritative instances");
        MultiblockRuntime.unload(world);
    }

    private static void incrementalAndProfile() {
        MultiblockLimits.configure(4096, 256, 4096, 1024);
        lua.load("formed=0;invalidated=0;suspended=0;resumed=0").call();
        String row = "CCCCCCCCCCCCCCCC";
        String layer = "{'@CCCCCCCCCCCCCCC','" + row + "','" + row + "','" + row + "'}";
        publish("m=betamoon.multiblocks:add{key='test:multiblock/machine',controller=controller,facing='north',"
                + "layers={" + layer + ",{'" + row + "','" + row + "','" + row + "','" + row + "'}},"
                + "palette={C=stone},onFormed=function(ctx) formed=formed+1 end}");
        Fixture world = new Fixture();
        world.add(0, 0);
        for (int x = 0; x < 16; x++) {
            for (int y = 64; y < 66; y++) {
                for (int z = 0; z < 4; z++) {
                    world.raw(x, y, z, 1, 0);
                }
            }
        }
        world.raw(0, 64, 0, 240, 0);
        MultiblockRuntime.Instance instance = MultiblockRuntime.get(world, 0, 64, 0);
        MultiblockRuntime.tick(world);
        world.edit(15, 65, 3, 0, 0);
        for (int tick = 0; tick < 5; tick++) {
            MultiblockRuntime.tick(world);
        }
        require(!instance.isFormed() && lua.get("formed").toint() == 0,
                "A stale partial scan must never publish formation");
        world.edit(15, 65, 3, 1, 0);
        for (int tick = 0; tick < 5; tick++) {
            MultiblockRuntime.tick(world);
        }
        require(instance.isFormed(), "A restarted bounded scan must eventually finish");
        long start = System.nanoTime();
        for (int i = 0; i < 200; i++) {
            instance.requestCheck();
            MultiblockRuntime.tick(world);
        }
        System.out.println("Multiblock profile: 200 bounded ticks, " + ((System.nanoTime() - start) / 1000000.0)
                + " ms; informational Java 8 fixture.");
        MultiblockRuntime.unload(world);
        MultiblockLimits.configure(4096, 65536, 4096, 1024);
    }

    private static void manyControllersProfile() {
        publish(managed(""));
        Fixture world = new Fixture();
        for (int chunk = 0; chunk < 8; chunk++) {
            world.add(chunk, 0);
            for (int x = 0; x < 16; x += 4) {
                for (int z = 0; z < 16; z += 4) {
                    world.raw(chunk * 16 + x, 64, z, 240, 0);
                    MultiblockRuntime.get(world, chunk * 16 + x, 64, z);
                }
            }
        }
        long start = System.nanoTime();
        long before = MultiblockRuntime.statistics(world).get("visits");
        long allocated = allocatedBytes();
        int latency = 0;
        for (int tick = 1; tick <= 40; tick++) {
            long prior = MultiblockRuntime.statistics(world).get("visits");
            MultiblockRuntime.tick(world);
            Map<String, Long> stats = MultiblockRuntime.statistics(world);
            require(stats.get("visits") - prior <= MultiblockLimits.visitsPerTick(),
                    "Discovery and recognition together must obey the shared tick budget");
            if (latency == 0 && stats.get("pending") == 0) {
                latency = tick;
            }
        }
        require(latency > 0, "Many incomplete controllers must not remain queued indefinitely");
        long visits = MultiblockRuntime.statistics(world).get("visits") - before;
        System.out.println("Multiblock load profile: 128 incomplete controllers, 40 ticks, " + visits
                + " visits, initial queue latency " + latency + " tick(s), " + ((System.nanoTime() - start) / 1000000.0)
                + " ms, allocated " + (allocated < 0 ? "unavailable" : Long.toString(allocatedBytes() - allocated))
                + " bytes; informational.");
        MultiblockRuntime.unload(world);
    }

    private static void longRunningJob() {
        MultiblockLimits.configure(4096, 256, 4096, 1024);
        String row = "CCCCCCCCCCCCCCCC";
        StringBuilder layers = new StringBuilder();
        for (int y = 0; y < 16; y++) {
            if (y > 0) {
                layers.append(',');
            }
            layers.append('{');
            for (int z = 0; z < 16; z++) {
                if (z > 0) {
                    layers.append(',');
                }
                layers.append("'").append(y == 0 && z == 0 ? "@CCCCCCCCCCCCCCC" : row).append("'");
            }
            layers.append('}');
        }
        publish("m=betamoon.multiblocks:add{key='test:multiblock/machine',controller=controller,facing='north',"
                + "layers={" + layers + "},palette={C=stone}}");
        Fixture world = new Fixture();
        world.add(0, 0);
        for (int x = 0; x < 16; x++) {
            for (int y = 64; y < 80; y++) {
                for (int z = 0; z < 16; z++) {
                    world.raw(x, y, z, 1, 0);
                }
            }
        }
        world.raw(0, 64, 0, 240, 0);
        MultiblockRuntime.Instance instance = MultiblockRuntime.get(world, 0, 64, 0);
        for (int tick = 0; tick < 50; tick++) {
            MultiblockRuntime.tick(world);
        }
        require(instance.isFormed(), "Maintenance must not restart and starve a job longer than its audit interval");
        MultiblockRuntime.unload(world);
        MultiblockLimits.configure(4096, 65536, 4096, 1024);
    }

    private static void dimensionIsolation() {
        lua.load("formed=0;invalidated=0;suspended=0;resumed=0").call();
        publish(managed(""));
        Fixture overworld = new Fixture();
        overworld.add(0, 0);
        overworld.raw(4, 64, 4, 240, 0);
        overworld.raw(6, 64, 4, 1, 0);
        MultiblockRuntime.Instance surface = MultiblockRuntime.get(overworld, 4, 64, 4);
        MultiblockRuntime.tick(overworld);
        surface.data.set("runs", LuaValue.valueOf(9), true);

        Fixture nether = new Fixture();
        nether.worldProvider.worldType = -1;
        nether.saved.putAll(overworld.saved);
        nether.add(0, 0);
        nether.raw(4, 64, 4, 240, 0);
        nether.raw(6, 64, 4, 1, 0);
        MultiblockRuntime.Instance underground = MultiblockRuntime.get(nether, 4, 64, 4);
        MultiblockRuntime.tick(nether);
        require(!surface.identity.equals(underground.identity) && underground.data.get("runs").toint() == 1
                && surface.data.get("runs").toint() == 9,
                "Equal controller coordinates in different dimensions must not share saved identity or data");
        MultiblockRuntime.unload(overworld);
        MultiblockRuntime.unload(nether);
    }

    private static void nativeMetadataObserver() {
        publish("m=betamoon.multiblocks:add{key='test:multiblock/machine',controller=controller,facing='north',"
                + "layers={{'@?C'}},palette={C={block=stone,damage=0}}}");
        Fixture world = new Fixture();
        world.add(0, 0);
        world.raw(4, 64, 4, 240, 0);
        world.raw(6, 64, 4, 1, 0);
        MultiblockRuntime.Instance instance = MultiblockRuntime.get(world, 4, 64, 4);
        MultiblockRuntime.tick(world);
        require(instance.isFormed(), "Native observer fixture must form");
        Chunk chunk = world.getChunkFromChunkCoords(0, 0);
        chunk.setBlockMetadata(6, 64, 4, 1);
        require(!instance.isFormed(), "The actual instrumented native setter must immediately invalidate");
        MultiblockRuntime.tick(world);
        require(instance.status.equals("unformed"), "Native metadata mismatch must reach the matcher");
        chunk.setBlockMetadata(6, 64, 4, 0);
        MultiblockRuntime.tick(world);
        require(instance.isFormed(), "Native metadata repair must restore formation");
        chunk.setBlockMetadata(6, 64, 4, 0);
        require(instance.isFormed(), "A native metadata no-op must not dirty formed geometry");
        chunk.onChunkUnload();
        require(!instance.isFormed(), "The actual instrumented unload must immediately revoke usability");
        MultiblockRuntime.unload(world);
        System.out.println("Multiblock native Java-agent execution passed: metadata edits, no-ops and chunk unload.");
    }

    private static long allocatedBytes() {
        Object bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof ThreadMXBean) {
            ThreadMXBean extended = (ThreadMXBean) bean;
            if (extended.isThreadAllocatedMemorySupported() && extended.isThreadAllocatedMemoryEnabled()) {
                return extended.getThreadAllocatedBytes(Thread.currentThread().getId());
            }
        }
        return -1;
    }

    private static void expectError(Runnable run) {
        try {
            run.run();
        } catch (LuaError expected) {
            return;
        }
        throw new AssertionError("Expected Lua validation or scope error");
    }

    private static void expectState(Runnable run) {
        try {
            run.run();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Expected publication or stale-instance error");
    }

    private static void require(boolean passed, String message) {
        if (!passed) {
            throw new AssertionError(message);
        }
    }

    private static final class Fixture extends World {
        private final Map<Long, Chunk> chunks = new HashMap<>();
        private final Map<Long, Chunk> unloaded = new HashMap<>();
        private final Map<String, MapDataBase> saved = new HashMap<>();

        private Fixture() {
            super(null, "multiblock_test", new WorldProvider() {
            }, 0L);
        }

        protected IChunkProvider getChunkProvider() {
            return null;
        }

        private void add(int x, int z) {
            Chunk chunk = new Chunk(this, new byte[32768], x, z);
            chunk.isChunkLoaded = true;
            chunks.put(MultiblockPosition.chunk(x, z), chunk);
            MultiblockRuntime.chunkLoaded(chunk);
        }

        private void notifyLoads() {
            for (Chunk chunk : chunks.values()) {
                MultiblockRuntime.chunkLoaded(chunk);
            }
        }

        private void remove(int x, int z) {
            long key = MultiblockPosition.chunk(x, z);
            Chunk chunk = chunks.remove(key);
            unloaded.put(key, chunk);
            MultiblockRuntime.chunkUnloaded(chunk);
        }

        private void reload(int x, int z) {
            long key = MultiblockPosition.chunk(x, z);
            Chunk chunk = unloaded.remove(key);
            chunks.put(key, chunk);
            MultiblockRuntime.chunkLoaded(chunk);
        }

        private void clear() {
            for (Chunk chunk : chunks.values()) {
                Arrays.fill(chunk.blocks, (byte) 0);
            }
        }

        private void raw(int x, int y, int z, int id, int metadata) {
            Chunk chunk = getChunkFromChunkCoords(x >> 4, z >> 4);
            chunk.blocks[(x & 15) << 11 | (z & 15) << 7 | y] = (byte) id;
            chunk.setBlockMetadata(x & 15, y, z & 15, metadata);
        }

        private void edit(int x, int y, int z, int id, int metadata) {
            Chunk chunk = getChunkFromChunkCoords(x >> 4, z >> 4);
            int old = MultiblockChunkCallbacks.state(chunk, x & 15, y, z & 15, id, metadata);
            raw(x, y, z, id, metadata);
            MultiblockChunkCallbacks.changed(chunk, x & 15, y, z & 15, old);
        }

        public boolean blockExists(int x, int y, int z) {
            return y >= 0 && y < 128 && chunks.containsKey(MultiblockPosition.chunk(x >> 4, z >> 4));
        }

        public Chunk getChunkFromChunkCoords(int x, int z) {
            Chunk chunk = chunks.get(MultiblockPosition.chunk(x, z));
            if (chunk == null) {
                throw new AssertionError("Attempted to provide an unloaded chunk");
            }
            return chunk;
        }

        public int getBlockId(int x, int y, int z) {
            return getChunkFromChunkCoords(x >> 4, z >> 4).getBlockID(x & 15, y, z & 15);
        }

        public int getBlockMetadata(int x, int y, int z) {
            return getChunkFromChunkCoords(x >> 4, z >> 4).getBlockMetadata(x & 15, y, z & 15);
        }

        public MapDataBase loadItemData(Class type, String name) {
            return saved.get(name);
        }

        public void setItemData(String name, MapDataBase data) {
            saved.put(name, data);
        }
    }
}
