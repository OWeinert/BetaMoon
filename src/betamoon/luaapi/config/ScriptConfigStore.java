package betamoon.luaapi.config;

import betamoon.assets.model.ModelJson;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;

/** One config file and the disk revision last observed by its Lua table. */
final class ScriptConfigStore {
    private static final int MAX_FILE_BYTES = 1024 * 1024;

    private final Path root;
    private final Path file;
    private final Map<String, Object> defaults;
    private final Map<String, ScriptConfigField> fields;
    private byte[] observedBytes;

    ScriptConfigStore(Path root, ScriptConfigKey key, Map<String, Object> defaults,
            Map<String, ScriptConfigField> fields) {
        this.root = root.toAbsolutePath().normalize();
        this.file = key.fileUnder(this.root);
        this.defaults = defaults;
        this.fields = fields;
    }

    String fileName() {
        return file.toString();
    }

    Map<String, Object> open() throws IOException {
        byte[] bytes = readFile();
        if (bytes == null) {
            bytes = ScriptConfigValues.json(defaults);
            writeFile(bytes);
            observedBytes = bytes;
            return defaults;
        }
        observedBytes = bytes;
        return parse(bytes);
    }

    Map<String, Object> defaults() {
        return defaults;
    }

    void save(LuaTable values) throws IOException {
        Map<String, Object> snapshot = ScriptConfigValues.snapshot(values, defaults);
        Map<String, Object> merged = saveDraft(snapshot);
        ScriptConfigValues.update(values, ScriptConfigValues.toLua(merged).checktable());
    }

    Map<String, Object> saveDraft(Map<String, Object> draft) throws IOException {
        return saveDraft(draft, observedBytes);
    }

    Map<String, Object> saveDraft(Map<String, Object> draft, byte[] expectedRevision) throws IOException {
        Map<String, Object> merged = ScriptConfigValues.merge(defaults, draft, fields);
        byte[] bytes = ScriptConfigValues.json(merged);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("Config exceeds " + MAX_FILE_BYTES + " bytes");
        }
        byte[] current = readFile();
        if (!Arrays.equals(current, expectedRevision)) {
            throw new IOException("Config changed on disk; reload it before saving");
        }
        writeFile(bytes);
        observedBytes = bytes;
        return merged;
    }

    void reload(LuaTable values) throws IOException {
        byte[] bytes = readFile();
        Map<String, Object> loaded;
        if (bytes == null) {
            loaded = defaults;
            bytes = ScriptConfigValues.json(defaults);
            writeFile(bytes);
        } else {
            loaded = parse(bytes);
        }
        LuaTable staged = ScriptConfigValues.toLua(loaded).checktable();
        ScriptConfigValues.update(values, staged);
        observedBytes = bytes;
    }

    DiskDraft readDraftFromDisk() throws IOException {
        byte[] bytes = readFile();
        Map<String, Object> loaded = bytes == null ? defaults : parse(bytes);
        return new DiskDraft(ScriptConfigValues.copy(loaded), bytes);
    }

    static final class DiskDraft {
        final Map<String, Object> values;
        final byte[] revision;

        DiskDraft(Map<String, Object> values, byte[] revision) {
            this.values = values;
            this.revision = revision == null ? null : revision.clone();
        }
    }

    private Map<String, Object> parse(byte[] bytes) throws IOException {
        try {
            return ScriptConfigValues.merge(defaults, ModelJson.read(bytes), fields);
        } catch (LuaError error) {
            throw new IOException(error.getMessage(), error);
        }
    }

    private byte[] readFile() throws IOException {
        checkLocation();
        if (!Files.exists(file)) {
            return null;
        }
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) {
            throw new IOException("Config is not a regular file or exceeds " + MAX_FILE_BYTES + " bytes");
        }
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("Config exceeds " + MAX_FILE_BYTES + " bytes");
        }
        return bytes;
    }

    private void writeFile(byte[] bytes) throws IOException {
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("Config exceeds " + MAX_FILE_BYTES + " bytes");
        }
        checkLocation();
        Path temporary = Files.createTempFile(file.getParent(), "betamoon-config-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                throw new IOException("Atomic config replacement is unavailable", error);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void checkLocation() throws IOException {
        Files.createDirectories(root);
        Files.createDirectories(file.getParent());
        if (!file.getParent().toRealPath().startsWith(root.toRealPath()) || Files.isSymbolicLink(file)) {
            throw new IOException("Config path leaves the BetaMoon config folder");
        }
    }
}
