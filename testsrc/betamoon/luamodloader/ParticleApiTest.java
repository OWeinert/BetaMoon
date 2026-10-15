package betamoon.luamodloader;

import betamoon.client.particles.ParticleRuntime;
import betamoon.client.particles.ParticleSprite;
import betamoon.assets.io.FileAssetProvider;
import betamoon.client.assets.ClientAssets;
import betamoon.luaapi.BetaMoonModule;
import betamoon.luaapi.block.BlockTickRegistry;
import betamoon.instrumentation.hooks.block.BlockScheduleCallbacks;
import betamoon.wrappers.BlockWrapper;
import betamoon.recipes.RecipeModificationHandler;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import betamoon.luaapi.particle.ParticleDeclarations;
import betamoon.luaapi.particle.ParticleReference;
import betamoon.luaapi.particle.ParticlesApi;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.particles.ParticleDefinition;
import betamoon.particles.ParticleEffectDefinition;
import betamoon.particles.ParticleRegistry;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.Entity;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import net.minecraft.src.Material;
import net.minecraft.src.ModLoader;
import net.minecraft.src.Session;
import betamoon.instrumentation.hooks.particle.ParticleCallbacks;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Exercises actual Lua registration, scope expiry, deterministic emitters, and
 * native particle collision.
 */
public final class ParticleApiTest {
    public static final String OWNER = "particles/main.lua";

    private ParticleApiTest() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("betamoon.runtime.side", "unknown");
        try {
            registrationAndReload();
            scriptedMotionAndFailure();
            collisionAndLifetime();
            effectsAndBudgets();
            worldApiCompatibility();
            ringExample();
            profileCallbacks();
            System.out.println(
                    "Particle API passed: validation, generations, Lua updates, collision, effects and budgets.");
        } finally {
            ParticleRegistry.retainOwners(Collections.emptySet());
            LuaScriptRegistry.setCurrentScriptFile(null);
        }
    }

    private static Globals lua() {
        Globals lua = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        ParticlesApi.attach(module);
        lua.set("betamoon", module);
        return lua;
    }

    private static void registrationAndReload() {
        Globals lua = lua();
        LuaScriptRegistry.setCurrentScriptFile(OWNER);
        try (ScriptAssetScope assets = ScriptAssetScope.open(OWNER);
                ParticleRegistry.Batch batch = ParticleRegistry.begin(OWNER)) {
            lua.load(
                    "p=betamoon.particles:add{key='test:particle/spark',texture={atlas='minecraft:particles',index=0}} "
                            + "assert(p:getKey()=='test:particle/spark') "
                            + "assert(betamoon.particles:get('test:particle/spark')~=nil) "
                            + "assert(betamoon.particles:get('test:particle/missing')==nil) "
                            + "fx=betamoon.particles.effects:add{key='test:particle_effect/burst',"
                            + "emitters={{particle=p,shape={type='point'},burstCount=3}}}")
                    .call();
            expectLua(() -> lua.load("betamoon.particles:add{key='test:particle/spark',texture='missing.png'}").call());
            expectLua(() -> lua.load("betamoon.particles.effects:add{key='test:particle_effect/wrong',"
                    + "emitters={{particle=fx,shape={type='point'},burstCount=1}}}").call());
            expectLua(() -> lua.load("betamoon.particles:add{key='test:particle/bad',"
                    + "texture={atlas='minecraft:particles',index=256}}").call());
            expectLua(() -> lua.load(
                    "betamoon.particles:add{key='test:particle/bad'," + "texture='x.png',size={start=0,finish=1}}")
                    .call());
            expectLua(() -> lua.load("betamoon.particles:add{key='test:particle/bad'," + "texture='x.png',onTick=true}")
                    .call());
            expectLua(() -> lua.load("betamoon.particles:add{key='test:particle/bad',"
                    + "texture='x.png',lifetimeTicks={min=1.5,max=2}}").call());
            expectLua(() -> lua.load("betamoon.particles.effects:add{key='test:particle_effect/big',"
                    + "emitters={{particle=p,shape={type='point'},burstCount=256,ratePerTick=1,durationTicks=1}}}")
                    .call());
            batch.publish();
        }
        ParticleReference old = (ParticleReference) lua.get("p");
        Object previous = ParticleRegistry.find(old.key());
        try (ScriptAssetScope assets = ScriptAssetScope.open(OWNER);
                ParticleRegistry.Batch batch = ParticleRegistry.begin(OWNER)) {
            lua.load("new=betamoon.particles:add{key='test:particle/spark',"
                    + "texture={atlas='minecraft:particles',index=1}}").call();
            // Failed initialization closes the batch without publishing it.
        }
        require(ParticleRegistry.find(old.key()) == previous, "Failed reload must preserve the old generation");
        old.requireCurrent(false);
        try (ScriptAssetScope assets = ScriptAssetScope.open(OWNER);
                ParticleRegistry.Batch batch = ParticleRegistry.begin(OWNER)) {
            lua.load("new=betamoon.particles:add{key='test:particle/spark',"
                    + "texture={atlas='minecraft:particles',index=2}}").call();
            batch.publish();
        }
        expectLua(() -> old.requireCurrent(false));
        require(ParticleRegistry.particle(old.key()).atlasIndex(0) == 2,
                "Successful replacement must remain published");
        LuaScriptRegistry.setCurrentScriptFile("other.lua");
        try (ScriptAssetScope assets = ScriptAssetScope.open("other.lua");
                ParticleRegistry.Batch batch = ParticleRegistry.begin("other.lua")) {
            expectLua(() -> lua.load("betamoon.particles:add{key='test:particle/spark',"
                    + "texture={atlas='minecraft:particles',index=2}}").call());
        }
        LuaScriptRegistry.setCurrentScriptFile(OWNER);
    }

    public static ParticleDefinition definition(Globals lua, String extra) {
        LuaScriptRegistry.setCurrentScriptFile(OWNER);
        try (ScriptAssetScope assets = ScriptAssetScope.open(OWNER)) {
            return ParticleDeclarations.particle(lua.load("return {key='test:particle/runtime',"
                    + "texture={atlas='minecraft:particles',indices={0,1,2},frameTicks=2},"
                    + "lifetimeTicks=20,light='full'," + extra + "}").call());
        }
    }

    private static ParticleSprite sprite(FixtureWorld world, ParticleDefinition definition, double vx, double vy,
            double vz, AtomicInteger failures) {
        return new ParticleSprite(world, definition, OWNER, 4, 64, 4, vx, vy, vz, 1, new double[]{1, 1, 1, 1}, 42,
                error -> failures.incrementAndGet());
    }

    private static void scriptedMotionAndFailure() {
        Globals lua = lua();
        ParticleDefinition definition = definition(lua,
                "motion={drag=1},onTick=function(ctx) " + "ctx.memory.n=(ctx.memory.n or 0)+1; saved=ctx.particle; "
                        + "ctx.particle:setRotation(ctx.memory.n*10); ctx.particle:setSize(0.4); "
                        + "ctx.particle:setAlpha(0.25); ctx.particle:setVelocity(0.1,0.2,0.3) end");
        FixtureWorld world = new FixtureWorld();
        AtomicInteger failures = new AtomicInteger();
        ParticleSprite first = sprite(world, definition, 0, 0, 0, failures);
        ParticleSprite second = sprite(world, definition, 0, 0, 0, failures);
        first.onUpdate();
        first.onUpdate();
        second.onUpdate();
        near(first.rotation, 20, "Per-instance memory must advance independently");
        near(second.rotation, 10, "New instances must not share memory");
        near(first.posX, 4.2, "Callback velocity must drive this tick's motion");
        near(first.color[3], 0.25, "Callback must override declarative alpha");
        require(failures.get() == 0, "Valid callback must run successfully");
        expectLua(() -> lua.load("saved:setSize(1)").call());
        ParticleDefinition broken = definition(lua,
                "rotation={degreesPerTick=2}," + "onTick=function(ctx) ctx.particle:setSize(1); error('broken') end");
        ParticleSprite failed = sprite(world, broken, 0, 0, 0, failures);
        failed.onUpdate();
        failed.onUpdate();
        require(failures.get() == 1, "Failed instance callback must be disabled after its first error");
        near(failed.size, 0.2, "Failure must restore declarative presentation");
        near(failed.rotation, 4, "Declarative updates must continue after callback failure");
    }

    private static void collisionAndLifetime() {
        Globals lua = lua();
        FixtureWorld world = new FixtureWorld();
        world.wall = true;
        ContactBlock contact = new ContactBlock(230);
        world.wallBlock = contact.blockID;
        AtomicInteger failures = new AtomicInteger();
        for (String mode : new String[]{"none", "stop", "bounce", "die"}) {
            ParticleDefinition definition = definition(lua,
                    "motion={drag=1,collision='" + mode + "'" + ("bounce".equals(mode) ? ",bounce=0.5" : "") + "}");
            ParticleSprite sprite = sprite(world, definition, 1.5, 0, 0, failures);
            near(sprite.motionX, 1.5, "Constructor randomness must not change authored velocity");
            sprite.onUpdate();
            if ("none".equals(mode)) {
                near(sprite.posX, 5.5, "No collision must preserve exact displacement");
            } else if ("stop".equals(mode)) {
                near(sprite.motionX, 0, "Stop collision must stop motion");
                double position = sprite.posX;
                sprite.onUpdate();
                near(sprite.posX, position, "Stopped particle must remain stationary");
            } else if ("bounce".equals(mode)) {
                near(sprite.motionX, -0.75, "Bounce must reverse the blocked axis with restitution");
            } else {
                require(sprite.isDead, "Die collision must remove the sprite");
            }
        }
        ParticleSprite expired = sprite(world, definition(lua, "motion={drag=1}"), 0, 0, 0, failures);
        for (int tick = 0; tick < 20; tick++) {
            expired.onUpdate();
        }
        require(expired.isDead && expired.age() == 20, "Lifetime must expire at the declared tick");
        ParticleSprite unloaded = sprite(world, definition(lua, "motion={collision='stop'}"), 1, 0, 0, failures);
        world.available = false;
        unloaded.onUpdate();
        require(unloaded.isDead, "Collision must stop at unavailable terrain without loading a chunk");
        require(contact.contacts == 0, "Decorative collision must never invoke native block contact callbacks");
    }

    private static void effectsAndBudgets() {
        Globals lua = lua();
        ParticleDefinition particle = definition(lua, "motion={drag=1}");
        ParticleEffectDefinition.Emitter emitter = new ParticleEffectDefinition.Emitter(particle, "sphere", false,
                new double[]{1, 0, 0}, 2, 0.5, 4, new ParticleEffectDefinition.Range(0, 0),
                new ParticleEffectDefinition.Range(0, 0), new ParticleEffectDefinition.Range(0, 0), null,
                new ParticleEffectDefinition.Range(1, 1), new double[]{1, 1, 1, 1}, 0);
        ParticleEffectDefinition effect = new ParticleEffectDefinition(
                ContentKey.parseForType("test:particle_effect/runtime", ContentType.PARTICLE_EFFECT), OWNER,
                Collections.singletonList(emitter), 16, false);
        FixtureWorld world = new FixtureWorld();
        ParticleRuntime runtime = new ParticleRuntime();
        runtime.configure(65535, 65535, 64);
        runtime.clear(world);
        runtime.camera(sprite(world, particle, 0, 0, 0, new AtomicInteger()));
        ParticleRuntime.Effect handle = runtime.play(effect, OWNER, 4, 64, 4, null, 0, 0, 0, 42);
        runtime.tick(true);
        require(runtime.sprites().isEmpty() && handle.isActive(), "Pause must suspend emitter activation");
        for (int tick = 0; tick < 4; tick++) {
            runtime.tick(false);
        }
        require(runtime.sprites().size() == 4 && !handle.isActive(),
                "Burst and fractional rate must emit exact counts");
        double firstX = runtime.sprites().get(0).posX;
        runtime.clear(world);
        runtime.camera(sprite(world, particle, 0, 0, 0, new AtomicInteger()));
        runtime.play(effect, OWNER, 4, 64, 4, null, 0, 0, 0, 42);
        runtime.tick(false);
        near(runtime.sprites().get(0).posX, firstX, "Explicit seeds must reproduce shape sampling");
        runtime.configure(2, 1, 16);
        require(runtime.spawn(particle, OWNER, 4, 64, 4, 0, 0, 0, 1, new double[]{1, 1, 1, 1}, 1, 16) == false,
                "Active budget must reject extra sprites");
        require(!runtime.spawn(particle, OWNER, 100, 64, 100, 0, 0, 0, 1, new double[]{1, 1, 1, 1}, 1, 16),
                "Distance culling must reject distant sprites");
        ParticleRuntime.Counters stats = runtime.snapshot().get(OWNER);
        require(stats.budgetDropped == 1 && stats.distanceCulled == 1, "Admission diagnostics must explain omissions");
        runtime.removeOwner(OWNER);
        require(runtime.sprites().isEmpty(), "Owner cleanup must remove live sprites");
        require(!runtime.spawn(particle, OWNER, Double.NaN, 64, 4, 0, 0, 0, 1, new double[]{1, 1, 1, 1}, 1, 16),
                "Invalid native coordinates must never enter the render list");
    }

    private static void worldApiCompatibility() {
        Globals lua = lua();
        FixtureWorld world = new FixtureWorld();
        LuaScriptRegistry.setCurrentScriptFile(OWNER);
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            lua.set("world", LuaWorldActionAccess.create(scope, world, 4, 64, 4));
            lua.load("world:spawnParticle('smoke',{velocityX=1})").call();
            require(world.nativeCalls == 1 && "smoke".equals(world.nativeName), "Bare vanilla calls must pass through");
            expectLua(() -> lua.load("world:spawnParticle('smoke',{scale=2})").call());
            expectLua(() -> lua.load("world:spawnParticle('test:particle/absent')").call());
        }
        expectLua(() -> lua.load("world:spawnParticle('smoke')").call());
        require(ParticleCallbacks.spawn("smoke", 0, 0, 0, 0, 0, 0) == 0,
                "Native names must never enter custom dispatch");
        require(ParticleCallbacks.spawn("test:particle/absent", 0, 0, 0, 0, 0, 0) == 0,
                "Unregistered native dispatch requests must pass through unchanged");
    }

    private static void ringExample() throws Exception {
        String owner = "02_int_31_custom_particles.lua";
        ClientAssets.useProviders(new FileAssetProvider(Paths.get("examples").toAbsolutePath().toFile()), null,
                message -> {
                    throw new AssertionError(message);
                });
        Field nativeBlockList = Session.class.getDeclaredFields()[0];
        nativeBlockList.setAccessible(true);
        Field modLoaderBlockList = ModLoader.class.getDeclaredField("field_blockList");
        modLoaderBlockList.setAccessible(true);
        modLoaderBlockList.set(null, nativeBlockList);
        RecipeModificationHandler.createRecipeMap();
        Globals lua = JsePlatform.standardGlobals();
        new BetaMoonModule(owner).call(LuaValue.NIL, lua);
        LuaTable blocks = lua.get("betamoon").get("blocks").checktable();
        LuaValue originalAdd = blocks.get("add");
        LuaValue[] declaration = new LuaValue[1];
        blocks.set("add", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                declaration[0] = args.arg(args.arg1() == blocks ? 2 : 1);
                return originalAdd.invoke(args);
            }
        });
        lua.load(new String(Files.readAllBytes(Paths.get("examples", owner)), StandardCharsets.UTF_8), "@" + owner)
                .call();
        try (ScriptExecutionScope execution = ScriptExecutionScope.open(owner);
                ScriptAssetScope assets = ScriptAssetScope.open(owner);
                ParticleRegistry.Batch particles = ParticleRegistry.begin(owner)) {
            lua.get("modInit").call();
            assets.publish();
            particles.publish();
        }
        require(Block.blocksList[242] != null, "The actual example must register its custom block");
        int[] emitted = new int[1];
        LuaTable world = new LuaTable();
        ParticleDefinition particle = ParticleRegistry
                .particle(ContentKey.parseForType("example:particle/ring_spark", ContentType.PARTICLE));
        require(particle != null && particle.texture != null, "Example must use a registered custom PNG particle");
        world.set("spawnParticle", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                ((ParticleReference) args.arg(2)).requireCurrent(false);
                LuaValue options = args.arg(3);
                double dx = options.get("x").todouble() - 4.5;
                double dz = options.get("z").todouble() - 4.5;
                near(Math.sqrt(dx * dx + dz * dz), 0.95, "Every emitted particle must lie on the ring");
                near(options.get("y").todouble(), 65.15, "Ring must sit above the block");
                emitted[0]++;
                return NONE;
            }
        });
        LuaTable context = new LuaTable();
        context.set("x", 4);
        context.set("y", 64);
        context.set("z", 4);
        context.set("world", world);
        require(declaration[0].get("onTick").isnil(), "Decorative particles must not schedule gameplay ticks");
        declaration[0].get("onDisplayTick").get("action").call(context);
        require(emitted[0] == 12, "The example must emit a complete twelve-spark ring");
        ParticleSprite sprite = new ParticleSprite(new FixtureWorld(), particle, owner, 4, 64, 4, 0, 0.003, 0, 1,
                new double[]{1, 1, 1, 1}, 1, error -> {
                    throw error;
                });
        sprite.onUpdate();
        near(sprite.rotation, 8, "The example's actual particle callback must update rotation");
        require(sprite.size > 0.1 && sprite.size < 0.22, "The example's actual particle callback must pulse size");
        displayRingReload(declaration[0], world, emitted);
        scheduledBlockReload((BlockWrapper) Block.blocksList[242]);
        LuaScriptRegistry.setCurrentScriptFile(OWNER);
    }

    private static void displayRingReload(LuaValue declaration, LuaTable worldApi, int[] emitted) {
        LuaTable display = declaration.get("onDisplayTick").checktable();
        LuaValue action = display.get("action");
        display.set("action", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                // Keep the native display context and real ring callback; capture presentation
                // requests.
                args.arg1().get("world").set("spawnParticle", worldApi.get("spawnParticle"));
                return action.invoke(args);
            }
        });
        BlockWrapper block = (BlockWrapper) Block.blocksList[242];
        BlockTickRegistry.register(block, LuaValue.NIL, display);
        for (int visit = 0; visit < 2; visit++) {
            FixtureWorld world = new FixtureWorld();
            world.ringBlock = true;
            world.chunk.blocks[(4 << 11) | (4 << 7) | 64] = (byte) block.blockID;
            BlockScheduleCallbacks.loaded(world.chunk);
            int before = emitted[0];
            block.randomDisplayTick(world, 4, 64, 4, world.rand);
            require(emitted[0] == before + 12,
                    "Saved ring blocks emit through the native display callback after rejoin");
            BlockScheduleCallbacks.unloaded(world.chunk);
            BlockScheduleCallbacks.loaded(world.chunk);
            block.randomDisplayTick(world, 4, 64, 4, world.rand);
            require(emitted[0] == before + 24, "Display emission resumes after chunk reload");
            require(world.scheduledCalls == 0, "Visual-only ring blocks never enqueue gameplay updates");
        }
    }

    private static void scheduledBlockReload(BlockWrapper block) {
        int[] ticks = new int[1];
        LuaTable tick = new LuaTable();
        tick.set("mode", "scheduled");
        LuaTable schedule = new LuaTable();
        schedule.set("delay", 1);
        schedule.set("repeatEvery", 8);
        tick.set("schedule", schedule);
        tick.set("action", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                ticks[0]++;
                return NONE;
            }
        });
        BlockTickRegistry.register(block, tick, LuaValue.NIL);
        for (int visit = 0; visit < 2; visit++) {
            FixtureWorld world = new FixtureWorld();
            world.ringBlock = true;
            world.neighborsReady = false;
            world.chunk.blocks[(4 << 11) | (4 << 7) | 64] = (byte) block.blockID;
            BlockScheduleCallbacks.loaded(world.chunk);
            require(world.scheduledCalls == 0, "Chunk-edge schedules must wait for neighboring chunks");
            BlockScheduleCallbacks.retry(world);
            require(world.scheduledCalls == 0, "Unavailable neighbors must not consume the retry");
            world.neighborsReady = true;
            BlockScheduleCallbacks.retry(world);
            require(world.scheduledCalls == 1, "Loading an existing scheduled block must admit its schedule");
            BlockScheduleCallbacks.loaded(world.chunk);
            require(world.scheduledCalls == 1, "Repeated load notification must not duplicate schedules");
            world.setWorldTime(1);
            block.updateTick(world, 4, 64, 4, world.rand);
            require(ticks[0] == visit * 2 + 1, "Scheduled callback must resume in each new world instance");
            require(world.scheduledCalls == 2, "Resumed block must schedule its next update");
            BlockScheduleCallbacks.unloaded(world.chunk);
            world.neighborsReady = false;
            world.setWorldTime(20);
            BlockScheduleCallbacks.retry(world);
            require(world.scheduledCalls == 2, "Unloaded neighbors must suspend admission");
            world.neighborsReady = true;
            BlockScheduleCallbacks.loaded(world.chunk);
            BlockScheduleCallbacks.retry(world);
            require(world.scheduledCalls == 3, "Returning chunks must restore an overdue native update");
            world.setWorldTime(21);
            block.updateTick(world, 4, 64, 4, world.rand);
            require(ticks[0] == visit * 2 + 2, "Chunk reload must restore continued emission");
        }
    }

    private static void profileCallbacks() {
        Globals lua = lua();
        ParticleDefinition definition = definition(lua, "onTick=function(ctx) "
                + "ctx.particle:setRotation(math.sin(ctx.progress*6)*45); ctx.particle:setSize(0.3) end");
        FixtureWorld world = new FixtureWorld();
        ParticleSprite[] sprites = new ParticleSprite[1024];
        for (int i = 0; i < sprites.length; i++) {
            sprites[i] = sprite(world, definition, 0, 0, 0, new AtomicInteger());
        }
        long start = System.nanoTime();
        for (int tick = 0; tick < 10; tick++) {
            for (ParticleSprite sprite : sprites) {
                sprite.onUpdate();
            }
        }
        System.out.println("Particle callback profile: 1024 scripted sprites, 10 ticks, "
                + ((System.nanoTime() - start) / 1000000.0) + " ms total (Java 8; informational).");
    }

    private static void expectLua(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected a Lua validation/scope error");
        } catch (LuaError expected) {
            // Expected public validation contract.
        }
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void near(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 0.000001, message + ": " + actual + " != " + expected);
    }

    public static final class FixtureWorld extends World {
        private Chunk chunk;
        public boolean available = true;
        public boolean wall;
        private boolean ringBlock;
        private boolean neighborsReady = true;
        private int scheduledCalls;
        private int wallBlock = 1;
        private int nativeCalls;
        private String nativeName;

        public FixtureWorld() {
            super(null, "particle_test", new WorldProvider() {
            }, 0L);
            chunk = new Chunk(this, new byte[32768], 0, 0);
            chunk.isChunkLoaded = true;
        }

        @Override
        public boolean blockExists(int x, int y, int z) {
            return available;
        }

        @Override
        public boolean checkChunksExist(int x1, int y1, int z1, int x2, int y2, int z2) {
            return available && neighborsReady;
        }

        @Override
        public int getBlockId(int x, int y, int z) {
            if (!available) {
                throw new AssertionError("Particle collision accessed an unloaded chunk");
            }
            if (ringBlock && x == 4 && y == 64 && z == 4) {
                return 242;
            }
            return wall && x == 5 && y == 64 && z == 4 ? wallBlock : 0;
        }

        @Override
        public void scheduleBlockUpdate(int x, int y, int z, int id, int delay) {
            scheduledCalls++;
        }

        @Override
        public void spawnParticle(String name, double x, double y, double z, double vx, double vy, double vz) {
            nativeCalls++;
            nativeName = name;
        }

        @Override
        public Chunk getChunkFromChunkCoords(int x, int z) {
            return chunk;
        }

        @Override
        protected IChunkProvider getChunkProvider() {
            return new IChunkProvider() {
                public boolean chunkExists(int x, int z) {
                    return true;
                }

                public Chunk provideChunk(int x, int z) {
                    return chunk;
                }

                public Chunk prepareChunk(int x, int z) {
                    return chunk;
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
                    return "particle fixture";
                }
            };
        }
    }

    private static final class ContactBlock extends Block {
        private int contacts;

        private ContactBlock(int id) {
            super(id, Material.rock);
        }

        @Override
        public void onEntityCollidedWithBlock(World world, int x, int y, int z, Entity entity) {
            contacts++;
        }

        @Override
        public void onEntityWalking(World world, int x, int y, int z, Entity entity) {
            contacts++;
        }
    }
}
