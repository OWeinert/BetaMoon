package betamoon.dimension.storage;

import betamoon.dimension.DimensionSession;
import betamoon.dimension.DimensionWorldAccess;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.NativeFieldAccess;
import betamoon.multiblock.ControllerlessMultiblockRuntime;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.Chunk;
import net.minecraft.src.ChunkLoader;
import net.minecraft.src.ChunkProvider;
import net.minecraft.src.IChunkLoader;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.ISaveHandler;
import net.minecraft.src.McRegionChunkLoader;
import net.minecraft.src.World;

/**
 * Transfer-only full flush which observes the I/O failures swallowed by native
 * saveChunks/MapStorage.
 */
public final class DimensionSaveFlush {
    private static final Field CHUNKS = NativeFieldAccess.field(ChunkProvider.class,
            new FieldRef(new ClassRef("net/minecraft/src/ChunkProvider"), "chunkList", "Ljava/util/List;"));
    private static final Field LOADER = NativeFieldAccess.field(ChunkProvider.class, new FieldRef(
            new ClassRef("net/minecraft/src/ChunkProvider"), "chunkLoader", "Lnet/minecraft/src/IChunkLoader;"));

    private DimensionSaveFlush() {
    }

    public static void flush(World world, DimensionSession session) throws IOException {
        flush(world, session, null);
    }

    public static void flush(World world, DimensionSession session, IProgressUpdate progress) throws IOException {
        session.root.func_22150_b();
        ControllerlessMultiblockRuntime.flush(world);
        session.rememberRootMetadata(world);
        List<Chunk> chunks = loadedChunks(world);
        DimensionChunkLoader checked = checkedLoader(world);
        boolean full = checked.needsFullCheckpoint();
        List<Chunk> written = new ArrayList<Chunk>();
        if (progress != null) {
            progress.displayLoadingString("Saving chunks");
        }
        try (DimensionChunkLoader.Checkpoint ignored = checked.beginCheckpoint()) {
            for (int index = 0; index < chunks.size(); index++) {
                Chunk chunk = chunks.get(index);
                if (!chunk.neverSave && (full || chunk.needsSaving(true))) {
                    checked.saveChunk(world, chunk);
                    written.add(chunk);
                }
                if (progress != null) {
                    progress.setLoadingProgress((index + 1) * 100 / chunks.size());
                }
            }
            checked.requireSaved();
        }
        for (Chunk chunk : written) {
            chunk.lastSaveTime = world.getWorldTime();
            chunk.isModified = false;
        }
        checked.checkpointCompleted();
        if (progress != null) {
            progress.displayLoadingString("Saving level");
        }
        // Native metadata, player and dirty MapStorage writes still run, but the
        // cache sees the already durable chunk checkpoint and does not write it twice.
        world.saveWorld(true, null);
        checked.requireSaved();
    }

    private static DimensionChunkLoader checkedLoader(World world) throws IOException {
        ISaveHandler save = DimensionWorldAccess.save(world);
        if (!(save instanceof DimensionRootSaveHandler) && !(save instanceof DimensionSaveHandler)) {
            throw new IOException("unsupported_environment: world has no checked dimension save handler");
        }
        IChunkLoader expected = save.getChunkLoader(world.worldProvider);
        Object current = NativeFieldAccess.get(LOADER, world.getIChunkProvider());
        if (current == expected && current instanceof DimensionChunkLoader) {
            return (DimensionChunkLoader) current;
        }
        if (current == null || current.getClass() == ChunkLoader.class
                || current.getClass() == McRegionChunkLoader.class) {
            DimensionChunkLoader checked = (DimensionChunkLoader) expected;
            // A script may enable dimensions after native chunks have loaded. Verify
            // those existing chunks once, then retain the checked adapter for autosaves.
            checked.requireFullCheckpoint();
            try {
                LOADER.set(world.getIChunkProvider(), checked);
            } catch (IllegalAccessException exception) {
                throw new IOException("Cannot bind the checked native chunk writer", exception);
            }
            return checked;
        }
        throw new IOException("unsupported_environment: replacement chunk writer has no checked save contract");
    }

    @SuppressWarnings("unchecked")
    public static List<Chunk> loadedChunks(World world) throws IOException {
        if (world.getIChunkProvider().getClass() != ChunkProvider.class) {
            throw new IOException("unsupported_environment: replacement cache has no checked save adapter");
        }
        List<Chunk> chunks = (List<Chunk>) NativeFieldAccess.get(CHUNKS, world.getIChunkProvider());
        return new ArrayList<Chunk>(chunks);
    }
}
