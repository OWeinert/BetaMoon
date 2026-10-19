package betamoon.dimension;

import betamoon.dimension.storage.DimensionSaveFlush;
import betamoon.dimension.storage.DimensionNbt;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.NativeFieldAccess;
import betamoon.teleport.ChunkPreparation;
import java.nio.file.Files;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityItem;
import net.minecraft.src.ChunkLoader;
import net.minecraft.src.ChunkProvider;
import net.minecraft.src.ISaveHandler;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.MapDataBase;
import net.minecraft.src.McRegionChunkLoader;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.SaveHandler;
import net.minecraft.src.SaveOldDir;
import net.minecraft.src.World;
import net.minecraft.src.WorldInfo;

/**
 * Real persistence and serialization counts across dirty and unchanged transfer
 * checkpoints.
 */
final class DimensionSaveFlushTest {
    private DimensionSaveFlushTest() {
    }

    static void verify() throws Exception {
        verify(false);
        verify(true);
    }

    private static void verify(boolean region) throws Exception {
        ISaveHandler root = region
                ? new SaveOldDir(Files.createTempDirectory("betamoon-flush-region-").toFile(), "world", false)
                : new SaveHandler(Files.createTempDirectory("betamoon-flush-old-").toFile(), "world", false);
        WorldInfo info = new WorldInfo(42, "world");
        info.setSpawn(8, 64, 8);
        root.saveWorldInfo(info);
        World world = new World(root, "world", 42, null);
        DimensionRuntime.activate(world);
        DimensionSession session = DimensionQueries.session(world);
        ChunkPreparation preparation = new ChunkPreparation(world, 25);
        preparation.reserve(0, 0, true);
        for (int tick = 0; !preparation.step(); tick++) {
            require(tick < 40, "Checkpoint fixture preparation must remain bounded");
        }
        List<Counter> counters = new ArrayList<Counter>();
        for (Chunk chunk : DimensionSaveFlush.loadedChunks(world)) {
            Counter counter = new Counter(world);
            counter.setPosition(chunk.xPosition * 16 + 8.5, 65, chunk.zPosition * 16 + 8.5);
            chunk.addEntity(counter);
            chunk.setChunkModified();
            counters.add(counter);
        }
        long started = System.nanoTime();
        DimensionSaveFlush.flush(world, session);
        long first = System.nanoTime() - started;
        int initialWrites = countAndReset(counters);
        started = System.nanoTime();
        DimensionSaveFlush.flush(world, session);
        long unchanged = System.nanoTime() - started;
        int repeatedWrites = countAndReset(counters);
        System.out.println("Checkpoint " + (region ? "McRegion" : "old") + ": " + counters.size()
                + " loaded chunks; initial serializations=" + initialWrites + " (" + first / 1000000.0
                + " ms), unchanged=" + repeatedWrites + " (" + unchanged / 1000000.0 + " ms)");
        require(initialWrites == counters.size(), "Each dirty chunk must be serialized exactly once per checkpoint");
        require(repeatedWrites == 0, "An unchanged checkpoint must not serialize any chunks");

        world.setBlockWithNotify(8, 60, 8, Block.oreDiamond.blockID);
        DimensionSaveFlush.flush(world, session);
        require(countAndReset(counters) == 1, "Only the modified chunk must be written again");
        Chunk restored = DimensionWorldAccess.save(world).getChunkLoader(world.worldProvider).loadChunk(world, 0, 0);
        require(restored.getBlockID(8, 60, 8) == Block.oreDiamond.blockID,
                "The optimized checkpoint must persist changed terrain in the native format");

        EntityItem item = new EntityItem(world, 8.5, 65, 8.5, new ItemStack(Item.diamond, 9));
        world.getChunkFromChunkCoords(0, 0).addEntity(item);
        world.setWorldTime(world.getWorldTime() + 1);
        world.getChunkFromChunkCoords(0, 0).isModified = false;
        DimensionSaveFlush.flush(world, session);
        restored = DimensionWorldAccess.save(world).getChunkLoader(world.worldProvider).loadChunk(world, 0, 0);
        boolean savedEntity = false;
        for (List<?> entities : restored.entities) {
            for (Object entity : entities) {
                savedEntity |= entity instanceof EntityItem && ((EntityItem) entity).item.stackSize == 9;
            }
        }
        require(savedEntity, "Entity state must be saved when time advances even without dirty terrain");
        countAndReset(counters);

        CounterData data = new CounterData();
        world.setItemData("checkpoint_test", data);
        data.markDirty();
        DimensionSaveFlush.flush(world, session);
        require(data.writes == 1, "Dirty world data must be saved once");
        DimensionSaveFlush.flush(world, session);
        require(data.writes == 1, "An unchanged checkpoint cannot rewrite clean world data");
        require(DimensionNbt.readCompressed(session.paths.resolve("data/checkpoint_test.dat"), 65536)
                .getCompoundTag("data").getInteger("value") == 42, "World data must remain durable");

        Counter failure = counters.get(0);
        Chunk dirty = world.getChunkFromChunkCoords(failure.chunkCoordX, failure.chunkCoordZ);
        failure.fail = true;
        dirty.setChunkModified();
        try {
            DimensionSaveFlush.flush(world, session);
            throw new AssertionError("An injected serialization failure must stop the checkpoint");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().equals("injected chunk serialization failure") && dirty.isModified,
                    "Failed saves cannot clear a chunk's dirty flag");
        }
        failure.fail = false;
        DimensionSaveFlush.flush(world, session);
        require(!dirty.isModified, "A successful retry must finish the pending chunk save");

        // Reproduce enabling dimension scripts after an ordinary native world was
        // loaded.
        Field loader = NativeFieldAccess.field(ChunkProvider.class, new FieldRef(
                new ClassRef("net/minecraft/src/ChunkProvider"), "chunkLoader", "Lnet/minecraft/src/IChunkLoader;"));
        loader.set(world.getIChunkProvider(),
                region
                        ? new McRegionChunkLoader(session.paths.root().toFile())
                        : new ChunkLoader(session.paths.root().toFile(), true));
        countAndReset(counters);
        failure.fail = true;
        try {
            DimensionSaveFlush.flush(world, session);
            throw new AssertionError("Native writer adoption must check existing clean chunks too");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().equals("injected chunk serialization failure"),
                    "Report the actual save error");
        }
        failure.fail = false;
        countAndReset(counters);
        DimensionSaveFlush.flush(world, session);
        require(countAndReset(counters) == counters.size(),
                "An interrupted native writer adoption must retry its complete baseline");
        DimensionSaveFlush.flush(world, session);
        require(countAndReset(counters) == 0, "The adopted cache must subsequently use dirty saves");
    }

    private static int countAndReset(List<Counter> counters) {
        int count = 0;
        for (Counter counter : counters) {
            count += counter.writes;
            counter.writes = 0;
        }
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Counter extends Entity {
        private int writes;
        private boolean fail;

        private Counter(World world) {
            super(world);
        }

        protected void entityInit() {
        }

        protected void readEntityFromNBT(NBTTagCompound tag) {
        }

        protected void writeEntityToNBT(NBTTagCompound tag) {
        }

        public boolean addEntityID(NBTTagCompound tag) {
            writes++;
            if (fail) {
                throw new IllegalStateException("injected chunk serialization failure");
            }
            return false;
        }
    }

    private static final class CounterData extends MapDataBase {
        private int writes;

        private CounterData() {
            super("checkpoint_test");
        }

        public void readFromNBT(NBTTagCompound tag) {
        }

        public void writeToNBT(NBTTagCompound tag) {
            writes++;
            tag.setInteger("value", 42);
        }
    }
}
