package betamoon.world.explosion;

import betamoon.luaapi.block.LuaBlockActionContext;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import forge.ISpecialResistance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Block;
import net.minecraft.src.Entity;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.Material;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.Vec3D;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Exercises parsing, selective effects, source handling, callbacks, and safety guards. */
public final class ExplosionApiTest {
    private ExplosionApiTest() {
    }

    public static void main(String[] arguments) {
        int blockId = freeBlockId();
        RecordingBlock block = new RecordingBlock(blockId);
        try {
            verifySelectiveEntityEffects();
            verifyBlockEffectsAndResult(block);
            verifyFireAfterBlockEffects();
            verifyScopeAndValidation();
            verifyRequestValidation();
            verifyWorkloadGuard();
        } finally {
            Block.blocksList[blockId] = null;
        }
        System.out.println("Explosion API passed: options, sources, blocks, entities, fire, results and guards.");
    }

    private static void verifySelectiveEntityEffects() {
        TestWorld world = new TestWorld();
        RecordingEntity source = new RecordingEntity(world);
        source.setPosition(10.0D, 64.0D, 10.0D);
        RecordingEntity centered = new RecordingEntity(world);
        centered.setPosition(10.0D, 64.0D, 10.0D);
        RecordingEntity nearby = new RecordingEntity(world);
        nearby.setPosition(11.0D, 64.0D, 10.0D);
        world.entities.add(source);
        world.entities.add(centered);
        world.entities.add(nearby);

        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable api = LuaWorldActionAccess.create(scope, world, 10, 64, 10, source);
            LuaTable options = options(1.0D);
            options.set("blocks", "none");
            options.set("sound", LuaValue.FALSE);
            options.set("particles", LuaValue.FALSE);
            LuaTable result = api.get("createExplosion").call(options).checktable();

            require(result.get("entitiesDamaged").checkint() == 2,
                    "The default source must be excluded while other exposed entities take damage");
            int knockedBack = result.get("entitiesKnockedBack").checkint();
            require(knockedBack == 1,
                    "Only the entity with a non-zero radial direction should be knocked back, got " + knockedBack
                            + " with centered motion " + centered.motionX + "," + centered.motionY + ","
                            + centered.motionZ + " and source damage " + source.damage);
            require(source.damage == 0, "The context entity must be the default explosion source");
            require(centered.damage > 0 && finiteMotion(centered),
                    "An entity at the exact center must take damage without receiving NaN motion");
            require(centered.motionX == 0.0D && centered.motionY == 0.0D && centered.motionZ == 0.0D,
                    "An exact-center entity must use deterministic zero knockback");
            require(nearby.damage > 0 && nearby.lastAttacker == source && nearby.motionX > 0.0D,
                    "Damage attribution and outward knockback must use the selected source");
            LuaTable position = result.get("position").checktable();
            require(position.get("x").checkdouble() == 10.0D && position.get("y").checkdouble() == 64.0D,
                    "An omitted position must use the callback origin");
        }

        TestWorld sourceIncludedWorld = new TestWorld();
        RecordingEntity included = new RecordingEntity(sourceIncludedWorld);
        included.setPosition(0.0D, 64.0D, 0.0D);
        sourceIncludedWorld.entities.add(included);
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable api = LuaWorldActionAccess.create(scope, sourceIncludedWorld, 0, 64, 0, included);
            LuaTable options = options(1.0D);
            options.set("source", LuaValue.FALSE);
            options.set("blocks", "none");
            options.set("knockback", LuaValue.FALSE);
            options.set("sound", LuaValue.FALSE);
            options.set("particles", LuaValue.FALSE);
            LuaTable result = api.get("createExplosion").call(options).checktable();
            require(result.get("entitiesDamaged").checkint() == 1 && included.lastAttacker == null,
                    "source=false must explicitly clear an entity-context default source");
        }

        TestWorld impulseWorld = new TestWorld();
        RecordingEntity pushed = new RecordingEntity(impulseWorld);
        pushed.setPosition(1.0D, 64.0D, 0.0D);
        impulseWorld.entities.add(pushed);
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable api = LuaWorldActionAccess.create(scope, impulseWorld, 0, 64, 0);
            LuaTable options = options(1.0D);
            LuaTable position = new LuaTable();
            position.set("x", 0.0D);
            position.set("y", 64.0D);
            position.set("z", 0.0D);
            options.set("position", position);
            options.set("blocks", "none");
            options.set("entities", LuaValue.FALSE);
            options.set("sound", LuaValue.FALSE);
            options.set("particles", LuaValue.FALSE);
            LuaTable result = api.get("createExplosion").call(options).checktable();
            require(pushed.damage == 0 && pushed.motionX > 0.0D,
                    "Knockback must remain usable when entity damage is disabled");
            require(result.get("entitiesDamaged").checkint() == 0
                            && result.get("entitiesKnockedBack").checkint() == 1,
                    "Independent entity-effect counts must reflect a non-damaging impulse");
        }
    }

    private static void verifyBlockEffectsAndResult(RecordingBlock block) {
        block.reset();
        TestWorld world = new TestWorld();
        world.put(2, 64, 2, block.blockID, 7);
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable api = LuaWorldActionAccess.create(scope, world, 2, 64, 2);
            LuaTable options = options(1.0D);
            LuaTable position = new LuaTable();
            position.set("x", 2.5D);
            position.set("y", 64.5D);
            position.set("z", 2.5D);
            options.set("position", position);
            options.set("entities", LuaValue.FALSE);
            options.set("knockback", LuaValue.FALSE);
            options.set("dropChance", 0.65D);
            options.set("includeAffectedBlocks", LuaValue.TRUE);
            LuaTable result = api.get("createExplosion").call(options).checktable();

            require(world.getBlockId(2, 64, 2) == 0 && result.get("blocksDestroyed").checkint() == 1,
                    "A zero-resistance block at the center must be destroyed exactly once");
            require(block.drops == 1 && Math.abs(block.lastDropChance - 0.65F) < 0.0001F,
                    "Selected drop chance must reach native block drop logic");
            require(block.callbacks == 1 && block.callbackSawRequest && block.callbackSawLuaContext,
                    "The native destruction callback must run while explosion context is active");
            require(block.specialResistanceCalls > 0,
                    "Forge position-aware explosion resistance must participate in ray calculation");
            require(world.sounds == 1 && world.particles > 0,
                    "Default presentation must emit one sound and explosion particles");
            require(result.get("blocksAffected").checkint() >= 1,
                    "The result must report the calculated affected-position count");
            LuaTable affected = result.get("affectedBlocks").checktable();
            require(affected.length() > 0 && !result.get("affectedBlocksTruncated").toboolean(),
                    "Requested affected positions must be returned within the public cap");
        }

        block.reset();
        world = new TestWorld();
        world.put(2, 64, 2, block.blockID, 5);
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable api = LuaWorldActionAccess.create(scope, world, 2, 64, 2);
            LuaTable options = options(1.0D);
            options.set("blocks", "none");
            options.set("entities", LuaValue.FALSE);
            options.set("knockback", LuaValue.FALSE);
            options.set("sound", LuaValue.FALSE);
            options.set("particles", LuaValue.FALSE);
            LuaTable result = api.get("createExplosion").call(options).checktable();
            require(world.getBlockId(2, 64, 2) == block.blockID,
                    "blocks=none must preserve affected blocks");
            require(result.get("blocksDestroyed").checkint() == 0 && block.drops == 0 && block.callbacks == 0,
                    "Disabled block effects must not drop, remove, or notify the block");
        }

        block.reset();
        world = new TestWorld();
        world.put(2, 64, 2, block.blockID, 3);
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable api = LuaWorldActionAccess.create(scope, world, 2, 64, 2);
            LuaTable options = options(1.0D);
            options.set("drops", LuaValue.FALSE);
            options.set("entities", LuaValue.FALSE);
            options.set("knockback", LuaValue.FALSE);
            options.set("sound", LuaValue.FALSE);
            options.set("particles", LuaValue.FALSE);
            LuaTable result = api.get("createExplosion").call(options).checktable();
            require(result.get("blocksDestroyed").checkint() == 1 && block.drops == 0 && block.callbacks == 1,
                    "Disabling drops must preserve destruction and native explosion callbacks");
        }
    }

    private static void verifyFireAfterBlockEffects() {
        TestWorld world = new TestWorld();
        world.rand = new Random(7L);
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                world.put(x, 63, z, Block.bedrock.blockID, 0);
            }
        }
        ExplosionRequest request = new ExplosionRequest(0.5D, 64.0D, 0.5D, 2.0F, null, true,
                ExplosionRequest.BlockMode.DESTROY, false, false, false, 0.0F, false, false, false);
        ExplosionResult result = new ExplosionExecutor(world, request, new AlwaysFireRandom()).execute();
        require(result.firesPlaced > 0, "A flaming explosion must place fire on supported affected air positions");
        require(world.count(Block.fire.blockID) == result.firesPlaced,
                "Reported fires must remain in the world after block effects complete");
    }

    private static void verifyScopeAndValidation() {
        TestWorld world = new TestWorld();
        LuaCallbackScope queryScope = new LuaCallbackScope(false);
        LuaTable queryApi = LuaWorldActionAccess.create(queryScope, world, 0, 64, 0);
        expectLuaError(() -> queryApi.get("createExplosion").call(options(1.0D)),
                "A query-only context must reject explosion creation");
        queryScope.close();

        LuaCallbackScope expiredScope = new LuaCallbackScope(true);
        LuaTable expiredApi = LuaWorldActionAccess.create(expiredScope, world, 0, 64, 0);
        expiredScope.close();
        expectLuaError(() -> expiredApi.get("createExplosion").call(options(1.0D)),
                "An expired callback context must reject explosion creation");

        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            LuaTable api = LuaWorldActionAccess.create(scope, world, 0, 64, 0);
            expectLuaError(() -> api.get("createExplosion").call(options(0.0D)),
                    "Out-of-range strength must be rejected before execution");
            LuaTable unknown = options(1.0D);
            unknown.set("unexpected", LuaValue.TRUE);
            expectLuaError(() -> api.get("createExplosion").call(unknown),
                    "Unknown option fields must be rejected instead of silently ignored");
        }
    }

    private static void verifyRequestValidation() {
        expectIllegalArgument(() -> new ExplosionRequest(Double.NaN, 0.0D, 0.0D, 1.0F, null, false,
                ExplosionRequest.BlockMode.DESTROY, true, true, true, 0.3F, true, true, false),
                "Non-finite positions must be rejected");
        expectIllegalArgument(() -> new ExplosionRequest(0.0D, 0.0D, 0.0D, 17.0F, null, false,
                ExplosionRequest.BlockMode.DESTROY, true, true, true, 0.3F, true, true, false),
                "Excessive strengths must be rejected");

        TestWorld sourceWorld = new TestWorld();
        RecordingEntity deadSource = new RecordingEntity(sourceWorld);
        deadSource.setEntityDead();
        ExplosionRequest deadSourceRequest = new ExplosionRequest(0.0D, 64.0D, 0.0D, 1.0F, deadSource, false,
                ExplosionRequest.BlockMode.NONE, false, false, false, 0.0F, false, false, false);
        expectIllegalArgument(() -> ExplosionExecution.execute(sourceWorld, deadSourceRequest),
                "A dead source must be rejected at the execution boundary");

        RecordingEntity crossWorldSource = new RecordingEntity(new TestWorld());
        ExplosionRequest crossWorldRequest = new ExplosionRequest(0.0D, 64.0D, 0.0D, 1.0F, crossWorldSource,
                false, ExplosionRequest.BlockMode.NONE, false, false, false, 0.0F, false, false, false);
        expectIllegalArgument(() -> ExplosionExecution.execute(sourceWorld, crossWorldRequest),
                "A source from another world must be rejected at the execution boundary");

        TestWorld remote = new TestWorld();
        remote.multiplayerWorld = true;
        ExplosionRequest request = new ExplosionRequest(0.0D, 64.0D, 0.0D, 1.0F, null, false,
                ExplosionRequest.BlockMode.NONE, false, false, false, 0.0F, false, false, false);
        try {
            ExplosionExecution.execute(remote, request);
            throw new AssertionError("A non-authoritative world must reject explosion execution");
        } catch (IllegalStateException expected) {
            // Expected authority guard.
        }
    }

    private static void verifyWorkloadGuard() {
        TestWorld world = new TestWorld();
        for (int i = 0; i < 1025; i++) {
            RecordingEntity entity = new RecordingEntity(world);
            entity.setPosition(0.5D, 64.0D, 0.5D);
            world.entities.add(entity);
        }
        ExplosionRequest request = new ExplosionRequest(0.5D, 64.0D, 0.5D, 1.0F, null, false,
                ExplosionRequest.BlockMode.NONE, true, true, false, 0.0F, false, false, false);
        try {
            ExplosionExecution.execute(world, request);
            throw new AssertionError("An oversized entity candidate set must be rejected");
        } catch (IllegalStateException expected) {
            // Expected workload guard.
        }
        for (Entity entity : world.entities) {
            RecordingEntity recording = (RecordingEntity) entity;
            require(recording.damage == 0 && recording.motionX == 0.0D && recording.motionY == 0.0D
                            && recording.motionZ == 0.0D,
                    "A workload failure must occur before explosion effects are applied");
        }
    }

    private static LuaTable options(double strength) {
        LuaTable options = new LuaTable();
        options.set("strength", strength);
        return options;
    }

    private static boolean finiteMotion(Entity entity) {
        return Double.isFinite(entity.motionX) && Double.isFinite(entity.motionY) && Double.isFinite(entity.motionZ);
    }

    private static int freeBlockId() {
        for (int id = Block.blocksList.length - 1; id >= 1; id--) {
            if (Block.blocksList[id] == null) {
                return id;
            }
        }
        throw new AssertionError("No free block ID is available for the explosion test");
    }

    private static void expectLuaError(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (LuaError expected) {
            // Expected Lua contract failure.
        }
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            // Expected Java contract failure.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingBlock extends Block implements ISpecialResistance {
        private int drops;
        private int callbacks;
        private float lastDropChance;
        private boolean callbackSawRequest;
        private boolean callbackSawLuaContext;
        private int specialResistanceCalls;

        private RecordingBlock(int id) {
            super(id, 1, Material.rock);
        }

        @Override
        public float getExplosionResistance(Entity source) {
            return 0.0F;
        }

        @Override
        public float getSpecialExplosionResistance(World world, int x, int y, int z, double explosionX,
                double explosionY, double explosionZ, Entity source) {
            specialResistanceCalls++;
            return 0.0F;
        }

        @Override
        public void dropBlockAsItemWithChance(World world, int x, int y, int z, int metadata, float chance) {
            drops++;
            lastDropChance = chance;
        }

        @Override
        public void onBlockDestroyedByExplosion(World world, int x, int y, int z) {
            callbacks++;
            ExplosionRequest current = ExplosionExecution.current();
            callbackSawRequest = current != null;
            try (LuaBlockActionContext context = new LuaBlockActionContext(
                    world, x, y, z, null, null, -1, true, blockID, 0)) {
                LuaValue explosion = context.get("explosion");
                callbackSawLuaContext = current != null && explosion.istable()
                        && explosion.get("strength").checkdouble() == current.strength;
            }
        }

        private void reset() {
            drops = 0;
            callbacks = 0;
            lastDropChance = 0.0F;
            callbackSawRequest = false;
            callbackSawLuaContext = false;
            specialResistanceCalls = 0;
        }
    }

    private static final class RecordingEntity extends Entity {
        private int damage;
        private Entity lastAttacker;

        private RecordingEntity(World world) {
            super(world);
        }

        @Override
        protected void entityInit() {
        }

        @Override
        public boolean attackEntityFrom(Entity attacker, int amount) {
            lastAttacker = attacker;
            damage += amount;
            return true;
        }

        @Override
        protected void readEntityFromNBT(NBTTagCompound tag) {
        }

        @Override
        protected void writeEntityToNBT(NBTTagCompound tag) {
        }
    }

    private static final class TestWorld extends World {
        private final Map<String, Cell> blocks = new HashMap<String, Cell>();
        private final List<Entity> entities = new ArrayList<Entity>();
        private int sounds;
        private int particles;

        private TestWorld() {
            super(null, "explosion_api_test", new WorldProvider() {
            }, 0L);
            rand = new Random(3L);
        }

        @Override
        protected IChunkProvider getChunkProvider() {
            return null;
        }

        @Override
        public int getBlockId(int x, int y, int z) {
            Cell cell = blocks.get(key(x, y, z));
            return cell == null ? 0 : cell.id;
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            Cell cell = blocks.get(key(x, y, z));
            return cell == null ? 0 : cell.metadata;
        }

        @Override
        public boolean setBlockWithNotify(int x, int y, int z, int id) {
            String key = key(x, y, z);
            int previous = getBlockId(x, y, z);
            if (id == 0) {
                blocks.remove(key);
            } else {
                blocks.put(key, new Cell(id, 0));
            }
            return previous != id;
        }

        @Override
        public List getEntitiesWithinAABBExcludingEntity(Entity excluded, AxisAlignedBB bounds) {
            List<Entity> matches = new ArrayList<Entity>();
            for (Entity entity : entities) {
                if (entity != excluded && entity.boundingBox.intersectsWith(bounds)) {
                    matches.add(entity);
                }
            }
            return matches;
        }

        @Override
        public float func_675_a(Vec3D center, AxisAlignedBB bounds) {
            return 1.0F;
        }

        @Override
        public void playSoundEffect(double x, double y, double z, String name, float volume, float pitch) {
            sounds++;
        }

        @Override
        public void spawnParticle(String name, double x, double y, double z, double vx, double vy, double vz) {
            particles++;
        }

        private void put(int x, int y, int z, int id, int metadata) {
            blocks.put(key(x, y, z), new Cell(id, metadata));
        }

        private int count(int id) {
            int count = 0;
            for (Cell cell : blocks.values()) {
                if (cell.id == id) {
                    count++;
                }
            }
            return count;
        }

        private static String key(int x, int y, int z) {
            return x + ":" + y + ":" + z;
        }
    }

    private static final class Cell {
        private final int id;
        private final int metadata;

        private Cell(int id, int metadata) {
            this.id = id;
            this.metadata = metadata;
        }
    }

    private static final class AlwaysFireRandom extends Random {
        @Override
        public int nextInt(int bound) {
            return 0;
        }
    }
}
