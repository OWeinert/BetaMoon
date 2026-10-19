package betamoon.dimension.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.src.NBTBase;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagList;

/**
 * Validates allocation lengths/depth before passing bounded native NBT to
 * Minecraft's reader.
 */
public final class DimensionNbt {
    private DimensionNbt() {
    }

    public static byte[] encode(NBTTagCompound value, int maximumBytes) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(new OutputStream() {
            public void write(int value) throws IOException {
                if (bytes.size() >= maximumBytes) {
                    throw new IOException("Dimension NBT exceeds its byte budget");
                }
                bytes.write(value);
            }

            public void write(byte[] value, int offset, int length) throws IOException {
                if (length > maximumBytes - bytes.size()) {
                    throw new IOException("Dimension NBT exceeds its byte budget");
                }
                bytes.write(value, offset, length);
            }
        })) {
            NBTBase.writeTag(value, output);
        }
        byte[] result = bytes.toByteArray();
        validate(result, maximumBytes);
        return result;
    }

    public static NBTTagCompound decode(byte[] bytes, int maximumBytes) throws IOException {
        validate(bytes, maximumBytes);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return (NBTTagCompound) NBTBase.readTag(input);
        }
    }

    public static NBTTagCompound copy(NBTTagCompound tag, int maximumBytes) throws IOException {
        return decode(encode(tag, maximumBytes), maximumBytes);
    }

    public static void remove(NBTTagCompound tag, String key) {
        tag.func_28110_c().removeIf(value -> ((NBTBase) value).getKey().equals(key));
    }

    public static NBTTagCompound readCompressed(Path file, int maximumBytes) throws IOException {
        try (InputStream input = new GZIPInputStream(Files.newInputStream(file));
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > maximumBytes - bytes.size()) {
                    throw new IOException("Compressed NBT exceeds its byte budget");
                }
                bytes.write(buffer, 0, count);
            }
            return decode(bytes.toByteArray(), maximumBytes);
        }
    }

    public static NBTTagCompound readRaw(Path file, int maximumBytes) throws IOException {
        if (Files.size(file) > maximumBytes) {
            throw new IOException("Native NBT exceeds its byte budget");
        }
        try (InputStream input = Files.newInputStream(file);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > maximumBytes - bytes.size()) {
                    throw new IOException("Native NBT exceeds its byte budget");
                }
                bytes.write(buffer, 0, count);
            }
            return decode(bytes.toByteArray(), maximumBytes);
        }
    }

    public static void writeRaw(DimensionPaths paths, String relative, NBTTagCompound tag, int maximumBytes)
            throws IOException {
        byte[] bytes = encode(tag, maximumBytes);
        Path file = paths.resolve(relative);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".pending");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                output.write(bytes);
                output.getFD().sync();
            }
            paths.resolve(relative);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Stable across compound insertion/serialization order, unlike raw native
     * HashMap NBT bytes.
     */
    public static String fingerprint(NBTTagCompound tag, int maximumBytes) throws IOException {
        encode(tag, maximumBytes);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            fingerprint(tag, digest, false);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) {
                result.append(String.format(Locale.ROOT, "%02x", value & 255));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void fingerprint(NBTBase tag, MessageDigest digest, boolean named) throws IOException {
        digest.update(tag.getType());
        if (named) {
            byte[] name = tag.getKey().getBytes(StandardCharsets.UTF_8);
            digest.update((byte) (name.length >> 8));
            digest.update((byte) name.length);
            digest.update(name);
        }
        if (tag instanceof NBTTagCompound) {
            List<NBTBase> entries = new ArrayList<NBTBase>();
            for (Object entry : ((NBTTagCompound) tag).func_28110_c()) {
                entries.add((NBTBase) entry);
            }
            entries.sort(Comparator.comparing(NBTBase::getKey));
            for (NBTBase entry : entries) {
                fingerprint(entry, digest, true);
            }
            digest.update((byte) 0);
        } else if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            for (int index = 0; index < list.tagCount(); index++) {
                fingerprint(list.tagAt(index), digest, false);
            }
            digest.update((byte) 0);
        } else {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NBTBase.writeTag(tag, new DataOutputStream(bytes));
            byte[] serialized = bytes.toByteArray();
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(serialized))) {
                input.readByte();
                input.readUTF();
                digest.update(serialized, serialized.length - input.available(), input.available());
            }
        }
    }

    public static void writeCompressed(DimensionPaths paths, String relative, NBTTagCompound tag, int maximumBytes)
            throws IOException {
        byte[] bytes = encode(tag, maximumBytes);
        Path file = paths.resolve(relative);
        Files.createDirectories(file.getParent());
        paths.resolve(relative);
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".pending");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                GZIPOutputStream compressed = new GZIPOutputStream(output);
                compressed.write(bytes);
                compressed.finish();
                output.getFD().sync();
            }
            paths.resolve(relative);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void validate(byte[] bytes, int maximumBytes) throws IOException {
        if (bytes.length > maximumBytes) {
            throw new IOException("Dimension NBT exceeds its byte budget");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readUnsignedByte() != 10) {
                throw new IOException("Dimension NBT requires a compound root");
            }
            input.readUTF();
            contents(input, 10, 0, new int[]{65536});
            if (input.available() != 0) {
                throw new IOException("Trailing dimension NBT data");
            }
        }
    }

    private static void contents(DataInputStream input, int type, int depth, int[] nodes) throws IOException {
        if (depth > 32 || --nodes[0] < 0) {
            throw new IOException("Dimension NBT exceeds its nesting/entry budget");
        }
        int length;
        switch (type) {
            case 1:
                input.readByte();
                break;
            case 2:
                input.readShort();
                break;
            case 3:
            case 5:
                input.readInt();
                break;
            case 4:
            case 6:
                input.readLong();
                break;
            case 7:
                length = input.readInt();
                if (length < 0 || length > input.available()) {
                    throw new IOException("Invalid dimension NBT byte-array length");
                }
                input.skipBytes(length);
                break;
            case 8:
                input.readUTF();
                break;
            case 9:
                int element = input.readUnsignedByte();
                length = input.readInt();
                if (length < 0 || length > nodes[0] || element < 0 || element > 10 || element == 0 && length != 0) {
                    throw new IOException("Invalid dimension NBT list");
                }
                for (int index = 0; index < length; index++) {
                    contents(input, element, depth + 1, nodes);
                }
                break;
            case 10:
                Set<String> names = new HashSet<String>();
                int child;
                while ((child = input.readUnsignedByte()) != 0) {
                    if (!names.add(input.readUTF())) {
                        throw new IOException("Duplicate dimension NBT compound field");
                    }
                    contents(input, child, depth + 1, nodes);
                }
                break;
            default:
                throw new IOException("Unsupported dimension NBT type: " + type);
        }
    }
}
