package betamoon.luaapi.entity;

import betamoon.entity.motion.EntitySimulationThread;
import betamoon.entity.motion.NativeEntityFields;
import betamoon.luaapi.player.PlayersApi;
import betamoon.luaapi.utils.LuaCallbackDeclarations;
import betamoon.luaapi.utils.LuaCallbackDispatcher;
import betamoon.luaapi.utils.LuaCallbackKey;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaOverrideActionDefinition;
import betamoon.luaapi.utils.LuaOverrideCallback;
import betamoon.luaapi.utils.LuaOverrideLayers;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptExecution;
import betamoon.player.LocalPlayerAccess;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Actual native swept collision and Lua scope/transaction checks. */
public final class EntityTransformApiTest {
    private EntityTransformApiTest() {
    }

    public static void main(String[] arguments) throws Exception {
        require(Block.stone != null, "Native blocks initialize");
        EntitySimulationThread.initialize();
        transforms();
        collisionAndLoadedPaths();
        supportAndSnapshots();
        spatialIndex();
        saveParity();
        permissions();
        playerAccess();
        callbackOwnership();
        System.out.println("Entity transforms passed: atomic requests, native collision, loaded paths, "
                + "support, scoped access and thread authority.");
    }

    private static void transforms() {
        FixtureWorld world = new FixtureWorld();
        Body body = new Body(world);
        body.setPosition(8, 64, 8);
        body.motionX = 2;
        body.motionY = -1;
        NativeEntityFields.fallDistance(body, 7);
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable api = LuaEntityActionAccess.create(scope, null, body);
            Varargs result = call(api, "setTransform", lua("{position={x=10},rotation={yaw=90}}"));
            require(!result.arg1().toboolean() && result.arg(2).tojstring().equals("blocked"),
                    "Occupied relocation was not rejected");
            near(body.posX, 8);
            near(body.rotationYaw, 0);
            near(body.motionX, 2);
            near(NativeEntityFields.fallDistance(body), 7);

            world.available = false;
            result = call(api, "setTransform", lua("{rotation={yaw=120,pitch=180}}"));
            require(result.arg1().toboolean(), "Rotation-only request performed occupancy/chunk checks");
            near(body.rotationPitch, 180);
            near(body.prevRotationYaw, 120);
            world.available = true;

            result = call(api, "setTransform", lua("{position={x=7},resetVelocity=true,resetFallDistance=true}"));
            require(result.arg1().toboolean(), "Valid relocation failed");
            near(body.posX, 7);
            near(body.posY, 64);
            near(body.prevPosX, 7);
            near(body.lastTickPosX, 7);
            near(body.motionX, 0);
            near(body.motionY, 0);
            near(NativeEntityFields.fallDistance(body), 0);

            body.prevPosX = 5;
            call(api, "setTransform", lua("{position={x=6},interpolation='preserve'}"));
            near(body.prevPosX, 5);
            near(body.posX, 6);
            expectLua(() -> call(api, "setTransform", lua("{position={x=8},rotation={pitch=1e300}}")));
            near(body.posX, 6);
            expectLua(() -> call(api, "setTransform", lua("{position={x=8},resetVelocity='yes'}")));
            near(body.posX, 6);
            expectLua(() -> call(api, "setTransform", lua("{position={}}")));
            expectLua(() -> call(api, "setTransform", lua("{postion={x=3}}")));

            body.motionX = 15;
            expectLua(() -> call(api, "addVelocity", LuaValue.valueOf(2), LuaValue.ZERO, LuaValue.ZERO));
            near(body.motionX, 15);
            require(call(api, "addVelocity", LuaValue.valueOf(-1), LuaValue.valueOf(0.25), LuaValue.ZERO)
                    .arg1().toboolean(), "Valid impulse failed");
            near(body.motionX, 14);
            near(body.motionY, 0.25);
        }
    }

    private static void collisionAndLoadedPaths() {
        FixtureWorld world = new FixtureWorld();
        Body body = new Body(world);
        body.setPosition(8, 64, 8);
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable api = LuaEntityActionAccess.create(scope, null, body);
            Varargs result = call(api, "moveBy", LuaValue.valueOf(4), LuaValue.ZERO, LuaValue.ZERO);
            require(result.arg1().toboolean(), "Swept movement was unavailable");
            near(body.boundingBox.maxX, 10);
            require(result.arg(2).get("collidedHorizontally").toboolean(), "Wall collision not reported");
            require(result.arg(2).get("appliedDelta").get("x").todouble() < 2,
                    "Movement tunneled through a wall");
            double beforeX = body.posX;
            world.available = false;
            int beforeReads = world.blockReads;
            result = call(api, "moveBy", LuaValue.ONE, LuaValue.ZERO, LuaValue.ZERO);
            require(!result.arg1().toboolean() && result.arg(2).tojstring().equals("unloaded_chunk"),
                    "Unloaded path was not rejected");
            near(body.posX, beforeX);
            require(world.blockReads == beforeReads, "Rejected path touched unloaded block data");
            world.available = true;
            body.noClip = true;
            require(!call(api, "moveBy", LuaValue.ONE, LuaValue.ZERO, LuaValue.ZERO).arg1().toboolean(),
                    "Collision action accepted an unsupported no-clip body");
            body.noClip = false;
            world.multiplayerWorld = true;
            require(!call(api, "addVelocity", LuaValue.ONE, LuaValue.ZERO, LuaValue.ZERO).arg1().toboolean(),
                    "Remote world was mutated");
        }
    }

    private static void supportAndSnapshots() {
        FixtureWorld world = new FixtureWorld();
        Body body = new Body(world);
        body.setPosition(8, 64, 8);
        body.onGround = false;
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable api = LuaEntityActionAccess.create(scope, null, body);
            LuaValue support = call(api, "getSupport").arg1();
            require(support.get("supported").toboolean() && !body.onGround,
                    "Support was inferred from or changed the native flag");
            LuaValue snapshot = call(api, "getTransform").arg1();
            snapshot.get("position").set("x", -999);
            near(body.posX, 8);
            LuaValue movement = call(api, "getMovementState").arg1();
            require(movement.get("support").get("supported").toboolean(), "Snapshot omitted support");
            world.available = false;
            Varargs result = call(api, "getSupport");
            require(result.arg1().isnil() && result.arg(2).tojstring().equals("unloaded_chunk"),
                    "Unavailable support pretended to be unsupported ground");
        }
    }

    private static void permissions() throws Exception {
        Body body = new Body(new FixtureWorld());
        body.setPosition(8, 64, 8);
        LuaTable expired;
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            expired = LuaEntityActionAccess.create(scope, null, body);
            try (LuaCallbackScope ignored = new LuaCallbackScope(false)) {
                expectLua(() -> call(expired, "addVelocity", LuaValue.ONE, LuaValue.ZERO, LuaValue.ZERO));
            }
            try (LuaCallbackScope ignored = LuaCallbackScope.binding()) {
                expectLua(() -> call(expired, "setTransform", lua("{rotation={yaw=30}}")));
            }
            AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
            Thread other = new Thread(() -> {
                try {
                    call(expired, "getSupport");
                } catch (Throwable error) {
                    failure.set(error);
                }
            });
            other.start();
            other.join();
            require(failure.get() instanceof LuaError, "Wrong-thread live access was accepted");
        }
        expectLua(() -> call(expired, "getTransform"));
    }

    private static void spatialIndex() {
        FixtureWorld world = new FixtureWorld();
        Chunk first = world.addChunk(0, 0);
        Chunk second = world.addChunk(1, 0);
        Body body = new Body(world);
        body.setPosition(8, 64, 8);
        first.addEntity(body);
        world.loadedEntityList.add(body);
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable api = LuaEntityActionAccess.create(scope, null, body);
            require(call(api, "setTransform", lua("{position={x=17}}")).arg1().toboolean(),
                    "Cross-chunk checked relocation failed");
            require(!first.entities[4].contains(body) && second.entities[4].contains(body),
                    "Relocation left the entity in the old chunk index");
            require(body.chunkCoordX == 1 && body.ticksExisted == 0, "Relocation simulated another tick");
            call(api, "moveBy", LuaValue.valueOf(-4), LuaValue.ZERO, LuaValue.ZERO);
            require(first.entities[4].contains(body) && !second.entities[4].contains(body),
                    "Immediate collision movement did not update its spatial index");
            require(world.loadedEntityList.size() == 1, "Movement duplicated the world entity registration");
        }
    }

    private static void playerAccess() {
        System.setProperty("betamoon.runtime.side", "client");
        FixtureWorld world = new FixtureWorld();
        EntityPlayer player = new EntityPlayer(world) {
            public void func_6420_o() {
            }
        };
        player.setPosition(8, 64, 8);
        final EntityPlayer[] current = {player};
        LocalPlayerAccess.initialize(new LocalPlayerAccess.Provider() {
            public EntityPlayer player() {
                return current[0];
            }

            public World world() {
                return world;
            }
        });
        LuaTable module = new LuaTable();
        PlayersApi.attach(module);
        LuaTable players = module.get("players").checktable();
        AtomicReference<LuaTable> retained = new AtomicReference<LuaTable>();
        VarArgFunction callback = new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                retained.set(args.arg1().checktable());
                EntityTransformApiTest.call(retained.get(), "addVelocity", LuaValue.ONE, LuaValue.ZERO, LuaValue.ZERO);
                return LuaValue.FALSE;
            }
        };
        require(call(players, "withLocal", callback).arg1().toboolean(),
                "Player accessor forwarded callback false rather than successful invocation");
        near(player.motionX, 1);
        expectLua(() -> call(retained.get(), "getPosition"));
        try (LuaCallbackScope ignored = new LuaCallbackScope(false)) {
            expectLua(() -> call(players, "withLocal", callback));
        }
        near(player.motionX, 1);
        player.health = 0;
        require(call(players, "withLocal", callback).arg(2).tojstring().equals("player_not_ready"),
                "A dead/replacing player was reported as absent");
        player.health = 20;
        world.multiplayerWorld = true;
        require(call(players, "withLocal", callback).arg(2).tojstring().equals("unsupported_environment"),
                "Remote player access was not rejected");
        world.multiplayerWorld = false;
        current[0] = null;
        Varargs unavailable = call(players, "withLocal", callback);
        require(!unavailable.arg1().toboolean() && unavailable.arg(2).tojstring().equals("no_player"),
                "Absent player did not return a reason");
    }

    private static void saveParity() {
        FixtureWorld world = new FixtureWorld();
        Body body = new Body(world);
        body.setPosition(8, 64, 8);
        NativeEntityFields.fallDistance(body, 3);
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable api = LuaEntityActionAccess.create(scope, null, body);
            require(call(api, "setTransform", lua("{position={x=6,y=65},rotation={yaw=90,pitch=15}}"))
                    .arg1().toboolean(), "Saved transform was refused");
            call(api, "addVelocity", LuaValue.valueOf(0.25), LuaValue.valueOf(-0.5), LuaValue.valueOf(0.1));
        }
        NBTTagCompound saved = new NBTTagCompound();
        body.writeToNBT(saved);
        Body restored = new Body(world);
        restored.readFromNBT(saved);
        near(restored.posX, 6);
        near(restored.posY, 65);
        near(restored.posZ, 8);
        near(restored.rotationYaw, 90);
        near(restored.rotationPitch, 15);
        near(restored.motionX, 0.25);
        near(restored.motionY, -0.5);
        near(restored.motionZ, 0.1);
        near(NativeEntityFields.fallDistance(restored), 3);
    }

    private static void callbackOwnership() {
        LuaTable definition = new LuaTable();
        LuaTable tick = new LuaTable();
        tick.set("action", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(LuaScriptRegistry.getCurrentScriptFile());
            }
        });
        definition.set("onTick", tick);
        LuaCallbackDeclarations<Callback> declarations = ScriptExecution.withOwner("declaration.lua", () ->
                LuaCallbackDeclarations.builder(Callback.class, "test:entity/owner")
                        .parse(definition, Callback.TICK).build());
        LuaCallbackDispatcher<Callback> dispatcher = new LuaCallbackDispatcher<Callback>(declarations);
        ScriptExecution.withOwner("caller.lua", () -> {
            require(dispatcher.call(Callback.TICK, new LuaTable(), LuaValue.NIL).tojstring().equals("declaration.lua"),
                    "Declaration dispatcher inherited its caller's owner");
            require(LuaScriptRegistry.getCurrentScriptFile().equals("caller.lua"),
                    "Declaration dispatcher did not restore caller ownership");
            return LuaValue.NIL;
        });

        StringBuilder owners = new StringBuilder();
        LuaTable context = new LuaTable();
        LuaOverrideActionDefinition first = override("first.lua", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                owners.append(LuaScriptRegistry.getCurrentScriptFile()).append(',');
                args.arg1().get("base").call(args.arg1());
                owners.append(LuaScriptRegistry.getCurrentScriptFile()).append(',');
                return LuaValue.NIL;
            }
        });
        LuaOverrideActionDefinition second = override("second.lua", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                owners.append(LuaScriptRegistry.getCurrentScriptFile()).append(',');
                args.arg1().get("base").call(args.arg1());
                owners.append(LuaScriptRegistry.getCurrentScriptFile()).append(',');
                return LuaValue.NIL;
            }
        });
        LuaOverrideCallback layers = new LuaOverrideCallback(
                LuaOverrideLayers.concat(LuaOverrideLayers.single(first), LuaOverrideLayers.single(second)));
        String previous = LuaScriptRegistry.getCurrentScriptFile();
        ScriptExecution.withOwner("base.lua", () -> layers.invoke(context, () -> {
            owners.append(LuaScriptRegistry.getCurrentScriptFile()).append(',');
            return LuaValue.NIL;
        }, LuaOverrideCallback.Result.EVENT));
        require(owners.toString().equals("second.lua,first.lua,base.lua,first.lua,second.lua,"),
                "Override/base owner chain was not restored: " + owners);
        require(previous == null ? LuaScriptRegistry.getCurrentScriptFile() == null
                : previous.equals(LuaScriptRegistry.getCurrentScriptFile()), "Caller ownership leaked");
        require(context.get("base").isnil(), "Base continuation leaked after the callback");
    }

    private enum Callback implements LuaCallbackKey {
        TICK;

        public String luaName() {
            return "onTick";
        }
    }

    private static LuaOverrideActionDefinition override(final String owner, final LuaValue action) {
        return new LuaOverrideActionDefinition() {
            public String getName() {
                return "ownership_test";
            }

            public String getOwner() {
                return owner;
            }

            public LuaValue getAction() {
                return action;
            }
        };
    }

    private static LuaValue lua(String expression) {
        return JsePlatform.standardGlobals().load("return " + expression).call();
    }

    private static Varargs call(LuaTable api, String method, LuaValue... args) {
        LuaValue[] all = new LuaValue[args.length + 1];
        all[0] = api;
        System.arraycopy(args, 0, all, 1, args.length);
        return api.get(method).invoke(LuaValue.varargsOf(all));
    }

    private static void expectLua(Runnable operation) {
        try {
            operation.run();
        } catch (LuaError expected) {
            return;
        }
        throw new AssertionError("Expected Lua validation/authority error");
    }

    private static void near(double actual, double expected) {
        require(Math.abs(actual - expected) < 1e-5, actual + " != " + expected);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Body extends Entity {
        private Body(World world) {
            super(world);
        }

        protected void entityInit() {
        }

        protected void readEntityFromNBT(NBTTagCompound tag) {
        }

        protected void writeEntityToNBT(NBTTagCompound tag) {
        }
    }

    private static final class FixtureWorld extends World {
        private boolean available = true;
        private int blockReads;
        private final Map<Long, Chunk> chunks = new HashMap<Long, Chunk>();

        private FixtureWorld() {
            super(null, "entity_motion_fixture", new WorldProvider() {
            }, 0L);
        }

        public boolean checkChunksExist(int x1, int y1, int z1, int x2, int y2, int z2) {
            return available;
        }

        public boolean blockExists(int x, int y, int z) {
            return available;
        }

        public int getBlockId(int x, int y, int z) {
            blockReads++;
            require(available, "Unloaded terrain was accessed");
            return y == 63 || x == 10 && y >= 64 && y <= 66 ? Block.stone.blockID : 0;
        }

        public List getEntitiesWithinAABBExcludingEntity(Entity excluded, AxisAlignedBB box) {
            return Collections.emptyList();
        }

        private Chunk addChunk(int x, int z) {
            Chunk chunk = new Chunk(this, new byte[32768], x, z);
            chunk.isChunkLoaded = true;
            chunks.put(chunkKey(x, z), chunk);
            return chunk;
        }

        public Chunk getChunkFromChunkCoords(int x, int z) {
            require(available, "Unloaded chunk was accessed");
            Chunk chunk = chunks.get(chunkKey(x, z));
            require(chunk != null, "Unexpected chunk load: " + x + "," + z);
            return chunk;
        }

        private static long chunkKey(int x, int z) {
            return (long) x << 32 ^ z & 0xffffffffL;
        }

        protected IChunkProvider getChunkProvider() {
            return new IChunkProvider() {
                public boolean chunkExists(int x, int z) {
                    return available;
                }

                public Chunk provideChunk(int x, int z) {
                    throw new AssertionError("Unexpected chunk load");
                }

                public Chunk prepareChunk(int x, int z) {
                    throw new AssertionError("Unexpected chunk load");
                }

                public void populate(IChunkProvider provider, int x, int z) {
                }

                public boolean saveChunks(boolean force, IProgressUpdate progress) {
                    return true;
                }

                public boolean unload100OldestChunks() {
                    return false;
                }

                public boolean canSave() {
                    return false;
                }

                public String makeString() {
                    return "motion_fixture";
                }
            };
        }
    }
}
