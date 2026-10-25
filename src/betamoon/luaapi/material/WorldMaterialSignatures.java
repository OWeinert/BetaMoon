package betamoon.luaapi.material;

import betamoon.fluid.FluidDefinition;
import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.src.Block;

/** Small save-side preflight for native block material and fluid carrier identities. */
public final class WorldMaterialSignatures {
    private static final int MAGIC = 0x424d4d41;
    private static final int VERSION = 1;
    private static final int MAX_BYTES = 128 * 1024;
    private static final int MAX_ENTRIES = 512;
    private static final String FILE = "betamoon_materials.dat";

    private WorldMaterialSignatures() {
    }

    public static void preflight(Path world) throws IOException {
        Path path = file(world);
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
            return; // Worlds made before custom materials have no material manifest.
        }
        Map<String, String> saved = read(path);
        Map<String, String> current = snapshot();
        for (Map.Entry<String, String> entry : saved.entrySet()) {
            if (!entry.getValue().equals(current.get(entry.getKey()))) {
                throw new IOException("World material dependency '" + entry.getKey()
                        + "' is missing or changed. Restore the script definition before opening this world.");
            }
        }
    }

    public static void record(Path world) throws IOException {
        Path path = file(world);
        Map<String, String> current = snapshot();
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && current.equals(read(path))) {
            return;
        }
        Files.createDirectories(path.getParent());
        byte[] bytes = encode(current);
        Path temporary = Files.createTempFile(path.getParent(), FILE, ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static Map<String, String> snapshot() {
        Map<String, String> entries = new TreeMap<String, String>();
        for (Block block : Block.blocksList) {
            if (block == null) {
                continue;
            }
            BlockMaterialRegistry.Entry material = BlockMaterialRegistry.describe(block.blockMaterial);
            if (material != null && material.custom()) {
                entries.put("block:" + block.blockID, material.key + "|" + material.signature);
            }
        }
        for (FluidRegistration registration : FluidRegistry.instance().snapshot()) {
            FluidDefinition fluid = registration.definition();
            if (!fluid.isVanilla()) {
                entries.put("fluid:" + fluid.key(), fluid.structuralSignature());
            }
        }
        return entries;
    }

    private static Path file(Path world) throws IOException {
        Path root = world.toAbsolutePath().normalize();
        Path path = root.resolve("data").resolve(FILE).normalize();
        if (!path.startsWith(root) || Files.isSymbolicLink(path) || Files.isSymbolicLink(path.getParent())) {
            throw new IOException("Invalid world material manifest path");
        }
        return path;
    }

    private static byte[] encode(Map<String, String> values) throws IOException {
        if (values.size() > MAX_ENTRIES) {
            throw new IOException("Too many world material dependencies");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(values.size());
            for (Map.Entry<String, String> entry : values.entrySet()) {
                output.writeUTF(entry.getKey());
                output.writeUTF(entry.getValue());
            }
        }
        if (bytes.size() > MAX_BYTES) {
            throw new IOException("World material manifest exceeds its storage budget");
        }
        return bytes.toByteArray();
    }

    private static Map<String, String> read(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_BYTES) {
            throw new IOException("Invalid world material manifest");
        }
        byte[] bytes = Files.readAllBytes(path);
        Map<String, String> result = new LinkedHashMap<String, String>();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("Unknown world material manifest format");
            }
            int count = input.readInt();
            if (count < 0 || count > MAX_ENTRIES) {
                throw new IOException("Invalid world material dependency count");
            }
            for (int index = 0; index < count; index++) {
                String key = input.readUTF();
                String signature = input.readUTF();
                if (result.put(key, signature) != null) {
                    throw new IOException("Duplicate world material dependency " + key);
                }
            }
            if (input.available() != 0) {
                throw new IOException("Trailing world material manifest data");
            }
        }
        return result;
    }
}
