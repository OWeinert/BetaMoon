package betamoon.dimension.storage;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.src.Chunk;
import net.minecraft.src.ChunkLoader;
import net.minecraft.src.IChunkLoader;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.RegionFile;
import net.minecraft.src.RegionFileCache;
import net.minecraft.src.World;

/**
 * Native chunk formats/serialization with observable I/O failures and bounded
 * reads.
 */
final class DimensionChunkLoader implements IChunkLoader {
    private static final int MAX_CHUNK_BYTES = 4 * 1024 * 1024;
    private final DimensionPaths paths;
    private final String directory;
    private final boolean regionFormat;
    private final Set<String> dirtyRegionFiles = new LinkedHashSet<String>();
    private IOException failure;
    private boolean fullCheckpointRequired;
    private Set<String> checkpointRegions;
    private Path checkpointDirectory;

    DimensionChunkLoader(DimensionPaths paths, String directory, boolean regionFormat) {
        this.paths = paths;
        this.directory = directory;
        this.regionFormat = regionFormat;
    }

    @Override
    public Chunk loadChunk(World world, int x, int z) throws IOException {
        try {
            byte[] bytes = read(x, z);
            if (bytes == null) {
                return null;
            }
            NBTTagCompound root = DimensionNbt.decode(bytes, MAX_CHUNK_BYTES);
            NBTTagCompound level = root.getCompoundTag("Level");
            if (!root.hasKey("Level") || level.getByteArray("Blocks").length != 32768
                    || level.getByteArray("Data").length != 16384 || level.getByteArray("SkyLight").length != 16384
                    || level.getByteArray("BlockLight").length != 16384 || level.getByteArray("HeightMap").length != 256
                    || level.getInteger("xPos") != x || level.getInteger("zPos") != z) {
                throw new IOException("Saved dimension chunk has invalid data or coordinates");
            }
            Chunk result = ChunkLoader.loadChunkIntoWorldFromCompound(world, level);
            result.func_25124_i();
            return result;
        } catch (IOException | RuntimeException exception) {
            failure = exception instanceof IOException ? (IOException) exception : new IOException(exception);
            // The native cache catches Exception and regenerates failed reads. Escaping
            // that catch is essential: corrupt saved terrain must never become a cache
            // miss.
            throw new SavedChunkFailure(failure);
        }
    }

    private byte[] read(int x, int z) throws IOException {
        InputStream stream;
        if (regionFormat) {
            Path regionPath = paths.resolve(regionPath(x, z));
            if (!Files.exists(regionPath)) {
                return null;
            }
            if (Files.size(regionPath) < 8192 || Files.size(regionPath) % 4096 != 0) {
                throw new IOException("Saved dimension region has an invalid header/sector length");
            }
            RegionFile region = RegionFileCache.func_22193_a(directory().toFile(), x, z);
            boolean present = region.func_22202_c(x & 31, z & 31);
            stream = region.getChunkDataInputStream(x & 31, z & 31);
            if (stream == null) {
                if (present) {
                    throw new IOException("Saved dimension region contains an unreadable chunk");
                }
                return null;
            }
        } else {
            Path file = paths.resolve(oldPath(x, z));
            if (!Files.exists(file)) {
                return null;
            }
            stream = new GZIPInputStream(Files.newInputStream(file));
        }
        try (InputStream input = stream) {
            return readBytes(input);
        }
    }

    @Override
    public void saveChunk(World world, Chunk chunk) throws IOException {
        world.checkSessionLock();
        try {
            NBTTagCompound root = new NBTTagCompound();
            NBTTagCompound level = new NBTTagCompound();
            ChunkLoader.storeChunkInCompound(chunk, world, level);
            root.setCompoundTag("Level", level);
            byte[] bytes = DimensionNbt.encode(root, MAX_CHUNK_BYTES);
            if (regionFormat) {
                RegionFile region = writeRegion(chunk.xPosition, chunk.zPosition);
                try (DataOutputStream output = region.getChunkDataOutputStream(chunk.xPosition & 31,
                        chunk.zPosition & 31)) {
                    if (output == null) {
                        throw new IOException("Native region writer is unavailable");
                    }
                    output.write(bytes);
                }
                // RegionFile itself swallows write exceptions; verify its actual serialized
                // bytes.
                byte[] persisted;
                try (InputStream input = region.getChunkDataInputStream(chunk.xPosition & 31, chunk.zPosition & 31)) {
                    persisted = input == null ? null : readBytes(input);
                }
                if (!Arrays.equals(bytes, persisted)) {
                    throw new IOException("Native region write could not be verified");
                }
                dirtyRegionFiles.add(regionPath(chunk.xPosition, chunk.zPosition));
            } else {
                writeOld(chunk.xPosition, chunk.zPosition, bytes);
            }
        } catch (IOException exception) {
            failure = exception;
            throw exception;
        }
    }

    private RegionFile writeRegion(int x, int z) throws IOException {
        String relative = regionPath(x, z);
        if (checkpointRegions == null || checkpointRegions.add(relative)) {
            paths.resolve(relative);
        }
        Path base = checkpointDirectory;
        if (base == null) {
            base = directory();
            if (checkpointRegions != null) {
                checkpointDirectory = base;
            }
        }
        // Ask the native cache each time: it can evict and close region handles.
        return RegionFileCache.func_22193_a(base.toFile(), x, z);
    }

    private static byte[] readBytes(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count > MAX_CHUNK_BYTES - bytes.size()) {
                throw new IOException("Dimension chunk exceeds its uncompressed byte budget");
            }
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }

    Checkpoint beginCheckpoint() {
        if (checkpointRegions != null) {
            throw new IllegalStateException("Nested dimension chunk checkpoint");
        }
        checkpointRegions = new LinkedHashSet<String>();
        return new Checkpoint();
    }

    final class Checkpoint implements AutoCloseable {
        @Override
        public void close() {
            checkpointRegions = null;
            checkpointDirectory = null;
        }
    }

    private void writeOld(int x, int z, byte[] bytes) throws IOException {
        Path file = paths.resolve(oldPath(x, z));
        Files.createDirectories(file.getParent());
        paths.resolve(oldPath(x, z));
        Path temporary = Files.createTempFile(file.getParent(), "chunk-", ".pending");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                GZIPOutputStream compressed = new GZIPOutputStream(output);
                compressed.write(bytes);
                compressed.finish();
                output.getFD().sync();
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private String regionPath(int x, int z) {
        return directory + "/region/r." + (x >> 5) + "." + (z >> 5) + ".mcr";
    }

    private Path directory() throws IOException {
        return directory.equals(".") ? paths.root() : paths.resolve(directory);
    }

    private String oldPath(int x, int z) {
        return directory + "/" + Integer.toString(x & 63, 36) + "/" + Integer.toString(z & 63, 36) + "/c."
                + Integer.toString(x, 36) + "." + Integer.toString(z, 36) + ".dat";
    }

    void requireSaved() throws IOException {
        if (failure != null) {
            throw new IOException("save_failed: dimension chunk storage failed", failure);
        }
        for (String relative : dirtyRegionFiles) {
            try (RandomAccessFile file = new RandomAccessFile(paths.resolve(relative).toFile(), "rw")) {
                file.getFD().sync();
            }
        }
        dirtyRegionFiles.clear();
    }

    void requireFullCheckpoint() {
        fullCheckpointRequired = true;
    }

    boolean needsFullCheckpoint() {
        return fullCheckpointRequired;
    }

    void checkpointCompleted() {
        fullCheckpointRequired = false;
    }

    @Override
    public void saveExtraChunkData(World world, Chunk chunk) {
    }

    @Override
    public void func_814_a() {
    }

    @Override
    public void saveExtraData() {
        try {
            requireSaved();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot durably flush dimension chunks", exception);
        }
    }

    static final class SavedChunkFailure extends Error {
        private SavedChunkFailure(IOException cause) {
            super("Refusing to regenerate an unreadable saved dimension chunk", cause);
        }
    }
}
