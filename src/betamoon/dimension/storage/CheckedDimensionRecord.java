package betamoon.dimension.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.CRC32;

/**
 * Bounded checksummed records with synchronized temporary files and atomic
 * replacement.
 */
public final class CheckedDimensionRecord {
    private final DimensionPaths paths;
    private final String relative;
    private final int magic;
    private final int maximumBytes;

    public CheckedDimensionRecord(DimensionPaths paths, String relative, int magic, int maximumBytes) {
        if (maximumBytes < 32 || maximumBytes > 4 * 1024 * 1024) {
            throw new IllegalArgumentException("Invalid dimension record budget");
        }
        this.paths = paths;
        this.relative = relative;
        this.magic = magic;
        this.maximumBytes = maximumBytes;
    }

    public byte[] read() throws IOException {
        Path file = paths.resolve(relative);
        if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > maximumBytes + 20L) {
            throw new IOException("Invalid dimension record: " + relative);
        }
        byte[] record;
        try (InputStream stream = Files.newInputStream(file);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (bytes.size() + count > maximumBytes + 20) {
                    throw new IOException("Dimension record exceeds its byte budget: " + relative);
                }
                bytes.write(buffer, 0, count);
            }
            record = bytes.toByteArray();
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(record))) {
            if (input.readInt() != magic || input.readInt() != 1) {
                throw new IOException("Unknown dimension record schema: " + relative);
            }
            int length = input.readInt();
            if (length < 0 || length > maximumBytes || record.length != length + 20) {
                throw new IOException("Invalid dimension record length: " + relative);
            }
            byte[] payload = new byte[length];
            input.readFully(payload);
            CRC32 checksum = new CRC32();
            checksum.update(record, 0, record.length - 8);
            if (input.readLong() != checksum.getValue()) {
                throw new IOException("Dimension record checksum mismatch: " + relative);
            }
            return payload;
        }
    }

    public void write(byte[] payload) throws IOException {
        if (payload.length > maximumBytes) {
            throw new IOException("Dimension record exceeds its byte budget: " + relative);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(magic);
            output.writeInt(1);
            output.writeInt(payload.length);
            output.write(payload);
            output.flush();
            CRC32 checksum = new CRC32();
            checksum.update(bytes.toByteArray());
            output.writeLong(checksum.getValue());
        }
        Path file = paths.resolve(relative);
        Files.createDirectories(file.getParent());
        paths.resolve(relative);
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".pending");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                output.write(bytes.toByteArray());
                output.getFD().sync();
            }
            paths.resolve(relative);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
