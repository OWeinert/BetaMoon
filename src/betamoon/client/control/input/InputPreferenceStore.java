package betamoon.client.control.input;

import betamoon.assets.model.ModelJson;
import betamoon.content.ContentKey;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/** Versioned, bounded client preferences independent of Lua registration lifetime. */
public final class InputPreferenceStore {
    private static final int MAX_FILE_BYTES = 1048576;
    private static final int MAX_RECORDS = 4096;
    private static final Logger LOGGER = Logger.getLogger(InputPreferenceStore.class.getName());

    public static final class Preference {
        private final List<InputBinding> bindings;
        private final InputActivationMode mode;

        Preference(List<InputBinding> bindings, InputActivationMode mode) {
            this.bindings = bindings == null ? null : Collections.unmodifiableList(new ArrayList<InputBinding>(bindings));
            this.mode = mode;
        }

        public List<InputBinding> bindings() {
            return bindings;
        }

        public InputActivationMode mode() {
            return mode;
        }

        boolean empty() {
            return bindings == null && mode == null;
        }
    }

    private final File file;
    private Map<String, Preference> records = new LinkedHashMap<String, Preference>();

    public InputPreferenceStore(File file) {
        if (file == null) {
            throw new IllegalArgumentException("Input preference file is required");
        }
        this.file = file;
        load();
    }

    private static String identity(ContentKey key, String action) {
        return key + "#" + action;
    }

    public Preference get(ContentKey key, String action) {
        return records.get(identity(key, action));
    }

    public int count() {
        return records.size();
    }

    public void setBindings(ContentKey key, String action, List<InputBinding> bindings) throws IOException {
        if (bindings == null || bindings.size() > 8) {
            throw new IllegalArgumentException("Saved input bindings require 0..8 entries");
        }
        Preference previous = get(key, action);
        update(identity(key, action), new Preference(bindings, previous == null ? null : previous.mode()));
    }

    public void resetBindings(ContentKey key, String action) throws IOException {
        Preference previous = get(key, action);
        update(identity(key, action), new Preference(null, previous == null ? null : previous.mode()));
    }

    public void setMode(ContentKey key, String action, InputActivationMode mode) throws IOException {
        Preference previous = get(key, action);
        update(identity(key, action), new Preference(previous == null ? null : previous.bindings(), mode));
    }

    public void resetMode(ContentKey key, String action) throws IOException {
        Preference previous = get(key, action);
        update(identity(key, action), new Preference(previous == null ? null : previous.bindings(), null));
    }

    public void resetMap(ContentKey key) throws IOException {
        Map<String, Preference> next = new LinkedHashMap<String, Preference>(records);
        String prefix = key + "#";
        for (String entry : new ArrayList<String>(next.keySet())) {
            if (entry.startsWith(prefix)) {
                next.remove(entry);
            }
        }
        commit(next);
    }

    private void update(String key, Preference value) throws IOException {
        Map<String, Preference> next = new LinkedHashMap<String, Preference>(records);
        if (value.empty()) {
            next.remove(key);
        } else {
            next.put(key, value);
        }
        commit(next);
    }

    private void commit(Map<String, Preference> next) throws IOException {
        if (next.size() > MAX_RECORDS) {
            throw new IOException("Saved input preference limit reached: " + MAX_RECORDS);
        }
        byte[] data = serialize(next).getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_FILE_BYTES) {
            throw new IOException("Saved input preferences exceed " + MAX_FILE_BYTES + " bytes");
        }
        Path target = file.toPath();
        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "betamoon-inputs-", ".tmp");
        try {
            Files.write(temporary, data);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                throw new IOException("Atomic input preference replacement is unavailable", error);
            }
            records = next;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void load() {
        if (!file.isFile()) {
            return;
        }
        try {
            if (file.length() > MAX_FILE_BYTES) {
                throw new IOException("Input preferences exceed the file size limit");
            }
            Map<String, Object> root = ModelJson.read(Files.readAllBytes(file.toPath()));
            Object version = root.get("version");
            if (!(version instanceof Number) || ((Number) version).doubleValue() != 1.0D) {
                throw new IOException("Unsupported input preference version");
            }
            Map<String, Object> actions = ModelJson.object(root.get("actions"), "actions");
            if (actions.size() > MAX_RECORDS) {
                throw new IOException("Input preferences exceed the record limit");
            }
            Map<String, Preference> loaded = new LinkedHashMap<String, Preference>();
            int ignored = 0;
            for (Map.Entry<String, Object> entry : actions.entrySet()) {
                try {
                    if (entry.getKey().length() > 256 || !entry.getKey().contains("#")) {
                        throw new IllegalArgumentException("Invalid preference identity");
                    }
                    Map<String, Object> value = ModelJson.object(entry.getValue(), entry.getKey());
                    List<InputBinding> bindings = null;
                    if (value.containsKey("bindings")) {
                        List<Object> serialized = ModelJson.array(value.get("bindings"), entry.getKey() + ".bindings");
                        if (serialized.size() > 8) {
                            throw new IllegalArgumentException("Too many saved bindings");
                        }
                        bindings = new ArrayList<InputBinding>();
                        for (Object binding : serialized) {
                            bindings.add(InputBinding.parse(ModelJson.name(binding, entry.getKey() + ".binding")));
                        }
                    }
                    InputActivationMode mode = value.containsKey("mode")
                            ? InputActivationMode.parse(ModelJson.name(value.get("mode"), entry.getKey() + ".mode"))
                            : null;
                    Preference preference = new Preference(bindings, mode);
                    if (!preference.empty()) {
                        loaded.put(entry.getKey(), preference);
                    }
                } catch (IOException | IllegalArgumentException error) {
                    ignored++;
                }
            }
            records = loaded;
            if (ignored > 0) {
                LOGGER.warning("Ignored " + ignored + " invalid saved input preferences");
            }
        } catch (IOException error) {
            LOGGER.warning("Kept the previous input preferences: " + error.getMessage());
        }
    }

    private static String serialize(Map<String, Preference> values) {
        StringBuilder result = new StringBuilder("{\n  \"version\": 1,\n  \"actions\": {");
        boolean first = true;
        for (Map.Entry<String, Preference> entry : new TreeMap<String, Preference>(values).entrySet()) {
            if (!first) {
                result.append(',');
            }
            first = false;
            result.append("\n    ").append(quote(entry.getKey())).append(": {");
            Preference preference = entry.getValue();
            if (preference.bindings() != null) {
                result.append("\"bindings\": [");
                for (int index = 0; index < preference.bindings().size(); index++) {
                    if (index > 0) {
                        result.append(", ");
                    }
                    result.append(quote(preference.bindings().get(index).toString()));
                }
                result.append(']');
            }
            if (preference.mode() != null) {
                if (preference.bindings() != null) {
                    result.append(", ");
                }
                result.append("\"mode\": ").append(quote(preference.mode().luaName()));
            }
            result.append('}');
        }
        return result.append("\n  }\n}\n").toString();
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 32) {
                result.append(String.format("\\u%04x", (int) character));
            } else {
                if (character == '"' || character == '\\') {
                    result.append('\\');
                }
                result.append(character);
            }
        }
        return result.append('"').toString();
    }
}
