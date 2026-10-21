package betamoon.worldgen.creation;

import betamoon.worldgen.terrain.GenerationSignature;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Bounded, checked, atomic world settings. Version 1 migrates without changing
 * choices.
 */
public final class GenerationSelectionStore {
    public static final int SCHEMA_VERSION = 3;
    private static final int MAGIC = 0x424d4753;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private final Path path;

    public GenerationSelectionStore(File file) {
        path = file.toPath().toAbsolutePath().normalize();
    }

    public GenerationSelection read() throws IOException {
        return read(true);
    }

    public GenerationSelection read(boolean migrate) throws IOException {
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_BYTES) {
            throw new IOException("Invalid generation settings file: " + path);
        }
        byte[] record;
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer, 0, Math.min(buffer.length, MAX_BYTES + 1 - bytes.size()))) > 0) {
                bytes.write(buffer, 0, count);
                if (bytes.size() > MAX_BYTES) {
                    throw new IOException("Generation settings exceed their storage budget");
                }
            }
            record = bytes.toByteArray();
        }
        if (record.length < 24 || record.length > MAX_BYTES) {
            throw new IOException("Invalid generation settings size");
        }
        CRC32 checksum = new CRC32();
        checksum.update(record, 0, record.length - 8);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(record))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Unknown generation settings format");
            }
            int version = input.readInt();
            if (version < 1 || version > SCHEMA_VERSION) {
                throw new IOException("Unsupported generation settings schema version " + version);
            }
            long seed = input.readLong();
            String preset = text(input);
            Map<String, Boolean> choices = new LinkedHashMap<String, Boolean>();
            Map<String, List<String>> parents = new LinkedHashMap<String, List<String>>();
            int count = count(input, 8192);
            for (int i = 0; i < count; i++) {
                String key = text(input);
                boolean selected = input.readBoolean();
                if (choices.put(key, selected) != null) {
                    throw new IOException("Duplicate generation choice " + key);
                }
                List<String> values = new ArrayList<String>();
                int parentCount = count(input, 8);
                for (int j = 0; j < parentCount; j++) {
                    values.add(text(input));
                }
                parents.put(key, values);
            }
            Map<String, String> dependencies = map(input);
            Map<String, String> profile = map(input);
            List<String> climate = new ArrayList<String>();
            for (int i = 0; i < 4096; i++) {
                climate.add(text(input));
            }
            GenerationSelection selection = new GenerationSelection(seed, preset, choices, parents, dependencies,
                    profile, climate);
            if (version >= 2) {
                Set<String> enabled = new LinkedHashSet<String>();
                int enabledCount = count(input, 8192);
                for (int i = 0; i < enabledCount; i++) {
                    if (!enabled.add(text(input))) {
                        throw new IOException("Duplicate enabled generation entry");
                    }
                }
                if (!enabled.equals(selection.enabled)) {
                    throw new IOException("Generation settings contain inconsistent enabled entries");
                }
            }
            if (version >= 3) {
                List<String> order = new ArrayList<String>();
                int orderCount = count(input, 256);
                for (int index = 0; index < orderCount; index++) {
                    order.add(text(input));
                }
                Set<String> selectedOverrides = new LinkedHashSet<String>();
                for (String entry : selection.enabled) {
                    if (entry.startsWith("override/")) {
                        selectedOverrides.add(entry.substring("override/".length()));
                    }
                }
                Map<String, String> orderValues = new LinkedHashMap<String, String>();
                for (int index = 0; index < order.size(); index++) {
                    orderValues.put(Integer.toString(index), order.get(index));
                }
                String savedSignature = selection.dependencies.get("terrain_override_order");
                if (order.size() != selectedOverrides.size()
                        || !new LinkedHashSet<String>(order).equals(selectedOverrides)
                        || savedSignature != null && !savedSignature.equals(GenerationSignature.of(orderValues))) {
                    throw new IOException("Generation settings contain inconsistent terrain override order");
                }
            }
            if (input.available() != 8 || input.readLong() != checksum.getValue()) {
                throw new IOException("Generation settings checksum/length mismatch");
            }
            if (migrate && version < SCHEMA_VERSION) {
                Path backup = path.resolveSibling(path.getFileName() + ".schema-" + version + ".bak");
                if (!Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
                    Files.copy(path, backup);
                }
                write(selection);
            }
            return selection;
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid generation settings values", exception);
        }
    }

    public void write(GenerationSelection selection) throws IOException {
        byte[] record = encode(selection, SCHEMA_VERSION);
        Files.createDirectories(path.getParent());
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Generation settings cannot be a symbolic link");
        }
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".pending");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                output.write(record);
                output.getFD().sync();
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static byte[] encode(GenerationSelection selection, int version) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(version);
            output.writeLong(selection.seed);
            output.writeUTF(selection.preset);
            output.writeInt(selection.choices.size());
            for (Map.Entry<String, Boolean> entry : selection.choices.entrySet()) {
                output.writeUTF(entry.getKey());
                output.writeBoolean(entry.getValue());
                List<String> parents = selection.parents.get(entry.getKey());
                output.writeInt(parents.size());
                for (String parent : parents) {
                    output.writeUTF(parent);
                }
            }
            map(output, selection.dependencies);
            map(output, selection.profileDependencies);
            for (String biome : selection.climate) {
                output.writeUTF(biome);
            }
            if (version >= 2) {
                output.writeInt(selection.enabled.size());
                for (String key : selection.enabled) {
                    output.writeUTF(key);
                }
            }
            if (version >= 3) {
                output.writeInt(selection.overrideOrder.size());
                for (String key : selection.overrideOrder) {
                    output.writeUTF(key);
                }
            }
            output.flush();
            CRC32 checksum = new CRC32();
            checksum.update(bytes.toByteArray());
            output.writeLong(checksum.getValue());
        }
        if (bytes.size() > MAX_BYTES) {
            throw new IOException("Generation settings exceed their storage budget");
        }
        return bytes.toByteArray();
    }

    private static String text(DataInputStream input) throws IOException {
        String value = input.readUTF();
        if (value.isEmpty() || value.length() > 65535) {
            throw new IOException("Invalid generation settings string");
        }
        return value;
    }

    private static int count(DataInputStream input, int maximum) throws IOException {
        int value = input.readInt();
        if (value < 0 || value > maximum) {
            throw new IOException("Generation settings count exceeds its budget");
        }
        return value;
    }

    private static Map<String, String> map(DataInputStream input) throws IOException {
        Map<String, String> result = new LinkedHashMap<String, String>();
        int count = count(input, 16384);
        for (int i = 0; i < count; i++) {
            String key = text(input);
            if (result.put(key, text(input)) != null) {
                throw new IOException("Duplicate generation dependency " + key);
            }
        }
        return result;
    }

    private static void map(DataOutputStream output, Map<String, String> values) throws IOException {
        output.writeInt(values.size());
        for (Map.Entry<String, String> entry : values.entrySet()) {
            output.writeUTF(entry.getKey());
            output.writeUTF(entry.getValue());
        }
    }
}
