package betamoon.worldgen.terrain;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.CRC32;

/**
 * Strict bounded records, synchronized file contents and same-directory atomic
 * replacement.
 */
public final class GeneratorManifestStore {
    private static final int MAGIC = 0x424d474d;
    private static final int MAX_BYTES = 4096;
    private final Path path;

    public GeneratorManifestStore(File file) {
        if (file == null) {
            throw new IllegalArgumentException("Generator manifest storage is unavailable");
        }
        path = file.toPath().toAbsolutePath().normalize();
    }

    public WorldGeneratorManifest read() throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            throw new IOException("Cannot determine generator manifest availability: " + path);
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_BYTES) {
            throw new IOException("Invalid generator manifest file: " + path);
        }
        byte[] record;
        try (FileInputStream input = new FileInputStream(path.toFile());
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[MAX_BYTES + 1];
            int count;
            while ((count = input.read(buffer, 0, Math.min(buffer.length, MAX_BYTES + 1 - bytes.size()))) > 0) {
                bytes.write(buffer, 0, count);
                if (bytes.size() > MAX_BYTES) {
                    throw new IOException("Generator manifest exceeds its storage budget: " + path);
                }
            }
            record = bytes.toByteArray();
        }
        if (record.length < 20 || record.length > MAX_BYTES) {
            throw new IOException("Invalid generator manifest length: " + path);
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(record))) {
            if (input.readInt() != MAGIC || input.readInt() != WorldGeneratorManifest.SCHEMA_REVISION) {
                throw new IOException("Unknown generator manifest format/revision: " + path);
            }
            long seed = input.readLong();
            int dimension = input.readInt();
            String key = input.readUTF();
            String signature = input.readUTF();
            WorldGeneratorManifest.OriginalSpawn spawn = input.readBoolean()
                    ? new WorldGeneratorManifest.OriginalSpawn(input.readInt(), input.readInt(), input.readInt())
                    : null;
            int payloadLength = record.length - 8;
            CRC32 checksum = new CRC32();
            checksum.update(record, 0, payloadLength);
            if (input.available() != 8 || input.readLong() != checksum.getValue()) {
                throw new IOException("Generator manifest checksum/length mismatch: " + path);
            }
            return new WorldGeneratorManifest(seed, dimension, key, signature, spawn);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid generator manifest values: " + path, exception);
        }
    }

    public void write(WorldGeneratorManifest manifest) throws IOException {
        byte[] record = encode(manifest);
        Path parent = path.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, path.getFileName().toString(), ".pending");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                output.write(record);
                output.getFD().sync();
            }
            // Refuse filesystems without atomic replacement instead of risking a torn
            // identity record.
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static byte[] encode(WorldGeneratorManifest manifest) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(WorldGeneratorManifest.SCHEMA_REVISION);
            output.writeLong(manifest.worldSeed);
            output.writeInt(manifest.dimension);
            output.writeUTF(manifest.profileKey);
            output.writeUTF(manifest.signature);
            output.writeBoolean(manifest.originalSpawn != null);
            if (manifest.originalSpawn != null) {
                output.writeInt(manifest.originalSpawn.x);
                output.writeInt(manifest.originalSpawn.y);
                output.writeInt(manifest.originalSpawn.z);
            }
            output.flush();
            CRC32 checksum = new CRC32();
            checksum.update(bytes.toByteArray());
            output.writeLong(checksum.getValue());
        }
        if (bytes.size() > MAX_BYTES) {
            throw new IOException("Generator manifest exceeds its storage budget");
        }
        return bytes.toByteArray();
    }
}
