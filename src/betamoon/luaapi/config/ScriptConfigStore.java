package betamoon.luaapi.config;

import betamoon.assets.model.ModelJson;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptExecution;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** One config file and the disk revision last observed by its Lua table. */
final class ScriptConfigStore {
    private static final int MAX_FILE_BYTES = 1024 * 1024;
    static final String VERSION_FIELD = "$schemaVersion";

    private final Path root;
    private final Path file;
    private final Map<String, Object> defaults;
    private final Map<String, ScriptConfigField> fields;
    private final int schemaVersion;
    private final LuaValue migrate;
    private byte[] observedBytes;
    private boolean migrationBlocked;

    ScriptConfigStore(Path root, ScriptConfigKey key, Map<String, Object> defaults, ScriptConfigMetadata metadata) {
        this.root = root.toAbsolutePath().normalize();
        this.file = key.fileUnder(this.root);
        this.defaults = defaults;
        this.fields = metadata.fields;
        this.schemaVersion = metadata.schemaVersion;
        this.migrate = metadata.migrate;
    }

    String fileName() {
        return file.toString();
    }

    Map<String, Object> open() throws IOException {
        byte[] bytes = readFile();
        if (bytes == null) {
            bytes = json(defaults);
            writeFile(bytes);
            observedBytes = bytes;
            migrationBlocked = false;
            return defaults;
        }
        observedBytes = bytes;
        Loaded loaded = parse(bytes);
        observedBytes = loaded.bytes;
        return loaded.values;
    }

    Map<String, Object> defaults() {
        return defaults;
    }

    boolean schemaRepairRequired() {
        return migrationBlocked;
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
        if (migrationBlocked) {
            throw new IOException("Config migration failed or the file uses a newer schema; resolve the file first");
        }
        Map<String, Object> merged = ScriptConfigValues.merge(defaults, draft, fields);
        byte[] bytes = json(merged);
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
            bytes = json(defaults);
            writeFile(bytes);
            migrationBlocked = false;
        } else {
            Loaded parsed = parse(bytes);
            loaded = parsed.values;
            bytes = parsed.bytes;
        }
        LuaTable staged = ScriptConfigValues.toLua(loaded).checktable();
        ScriptConfigValues.update(values, staged);
        observedBytes = bytes;
    }

    DiskDraft readDraftFromDisk() throws IOException {
        byte[] bytes = readFile();
        Map<String, Object> loaded = defaults;
        if (bytes != null) {
            Loaded parsed = parse(bytes);
            loaded = parsed.values;
            bytes = parsed.bytes;
        }
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

    private Loaded parse(byte[] bytes) throws IOException {
        try {
            Map<String, Object> values = ModelJson.read(bytes);
            if (schemaVersion == 0) {
                return new Loaded(ScriptConfigValues.merge(defaults, values, fields), bytes);
            }
            migrationBlocked = true;
            Object marker = values.remove(VERSION_FIELD);
            int fileVersion = version(marker);
            if (fileVersion > schemaVersion) {
                throw new IOException(
                        "Config schema " + fileVersion + " is newer than supported schema " + schemaVersion);
            }
            if (fileVersion < schemaVersion) {
                if (migrate.isnil()) {
                    throw new IOException("Config schema " + fileVersion + " requires a migration to " + schemaVersion);
                }
                LuaValue result = ScriptExecution
                        .invoke(LuaScriptRegistry.getCurrentScriptFile(), "config.migrate", migrate, LuaValue
                                .varargsOf(ScriptConfigValues.immutableSnapshot(values), LuaValue.valueOf(fileVersion)))
                        .arg1();
                if (!result.istable()) {
                    throw new IOException("Config migration must return a table");
                }
                values = ScriptConfigValues.snapshot(result.checktable(), null);
                if (values.containsKey(VERSION_FIELD)) {
                    throw new IOException("Config migration result must not contain " + VERSION_FIELD);
                }
            }
            Map<String, Object> merged = ScriptConfigValues.merge(defaults, values, fields);
            if (fileVersion < schemaVersion) {
                byte[] upgraded = json(merged);
                if (!Arrays.equals(readFile(), bytes)) {
                    throw new IOException("Config changed on disk during migration");
                }
                writeFile(upgraded);
                bytes = upgraded;
            }
            migrationBlocked = false;
            return new Loaded(merged, bytes);
        } catch (LuaError error) {
            throw new IOException(error.getMessage(), error);
        }
    }

    private int version(Object marker) throws IOException {
        if (marker == null) {
            return 0;
        }
        if (!(marker instanceof Number)) {
            throw new IOException("Config " + VERSION_FIELD + " must be a non-negative integer");
        }
        double value = ((Number) marker).doubleValue();
        if (!Double.isFinite(value) || value < 0 || value > Integer.MAX_VALUE || value != Math.floor(value)) {
            throw new IOException("Config " + VERSION_FIELD + " must be a non-negative integer");
        }
        return (int) value;
    }

    private byte[] json(Map<String, Object> values) throws IOException {
        if (schemaVersion == 0) {
            return ScriptConfigValues.json(values);
        }
        Map<String, Object> document = ScriptConfigValues.copy(values);
        document.put(VERSION_FIELD, Integer.valueOf(schemaVersion));
        byte[] bytes = ScriptConfigValues.json(document);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("Config exceeds " + MAX_FILE_BYTES + " bytes");
        }
        return bytes;
    }

    private static final class Loaded {
        private final Map<String, Object> values;
        private final byte[] bytes;

        private Loaded(Map<String, Object> values, byte[] bytes) {
            this.values = values;
            this.bytes = bytes;
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
