package betamoon.luaapi.config;

import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** One live Lua table, its file and its optional generated-page contract. */
public final class ScriptConfigRegistration {
    private final String owner;
    private final long generation;
    private final String key;
    private final ScriptConfigStore store;
    private final ScriptConfigMetadata metadata;
    private final LuaTable live;
    private Map<String, Object> published;
    private ApplyEvent retry;
    private String applyError;
    private boolean malformedAtOpen;

    ScriptConfigRegistration(String owner, long generation, String key, ScriptConfigStore store,
            ScriptConfigMetadata metadata, LuaTable live, Map<String, Object> initial, boolean malformedAtOpen) {
        this.owner = owner;
        this.generation = generation;
        this.key = key;
        this.store = store;
        this.metadata = metadata;
        this.live = live;
        this.published = ScriptConfigValues.copy(initial);
        this.malformedAtOpen = malformedAtOpen;
    }

    public String owner() {
        return owner;
    }

    public String key() {
        return key;
    }

    public String label() {
        return metadata.label;
    }

    public String description() {
        return metadata.description;
    }

    public Map<String, ScriptConfigField> fields() {
        return metadata.fields;
    }

    public Map<String, Object> defaults() {
        return ScriptConfigValues.copy(store.defaults());
    }

    public boolean malformedAtOpen() {
        return malformedAtOpen;
    }

    public String applyError() {
        return applyError;
    }

    public boolean active() {
        return ScriptConfigRegistry.isCurrent(this)
                && ScriptResourceTracker.ownerGeneration(owner) == generation;
    }

    public Map<String, Object> draft() {
        requireActive();
        return ScriptConfigValues.copy(ScriptConfigValues.snapshot(live, store.defaults()));
    }

    public DiskDraft reloadDraftFromDisk() throws IOException {
        requireActive();
        ScriptConfigStore.DiskDraft loaded = store.readDraftFromDisk();
        malformedAtOpen = false;
        return new DiskDraft(this, loaded.values, loaded.revision);
    }

    /** Explicitly allows replacing a file that failed to parse when opened. */
    public void resolveMalformedFile() {
        requireActive();
        malformedAtOpen = false;
    }

    public void saveDraft(Map<String, Object> draft) throws IOException {
        saveDraft(draft, ScriptConfigValues.snapshot(live, store.defaults()), null);
    }

    public void saveDraft(Map<String, Object> draft, Map<String, Object> expectedLive) throws IOException {
        saveDraft(draft, expectedLive, null);
    }

    public void saveDraft(Map<String, Object> draft, Map<String, Object> expectedLive, DiskDraft diskDraft)
            throws IOException {
        requireActive();
        if (malformedAtOpen) {
            throw new IOException("Config file was unreadable. Choose Replace invalid file first.");
        }
        Map<String, Object> currentLive = ScriptConfigValues.snapshot(live, store.defaults());
        if (!Objects.equals(currentLive, expectedLive)) {
            throw new IOException("Config changed in memory; reload the editor draft before saving");
        }
        if (diskDraft != null && diskDraft.owner != this) {
            throw new LuaError("Config draft belongs to a different section");
        }
        ScriptConfigEdits.validate(metadata.fields, currentLive, draft);
        Map<String, Object> committed = diskDraft == null ? store.saveDraft(draft)
                : store.saveDraft(draft, diskDraft.revision);
        publish(committed);
    }

    void saveLua() throws IOException {
        requireActive();
        Map<String, Object> snapshot = ScriptConfigValues.snapshot(live, store.defaults());
        Map<String, Object> committed = store.saveDraft(snapshot);
        publish(committed);
    }

    void reloadLua() throws IOException {
        requireActive();
        store.reload(live);
        publish(ScriptConfigValues.snapshot(live, store.defaults()));
    }

    public void retryApply() {
        requireActive();
        if (retry != null) {
            ScriptConfigRegistry.enqueue(retry);
        }
    }

    private void publish(Map<String, Object> values) {
        Map<String, Object> previous = published;
        List<String> changed = ScriptConfigEdits.changedPaths(previous, values);
        ScriptConfigValues.update(live, ScriptConfigValues.toLua(values).checktable());
        published = ScriptConfigValues.copy(values);
        malformedAtOpen = false;
        applyError = null;
        retry = null;
        if (!changed.isEmpty() && !metadata.onChange.isnil()) {
            ScriptConfigRegistry.enqueue(new ApplyEvent(this, ScriptConfigValues.copy(values),
                    ScriptConfigValues.copy(previous), changed));
        }
    }

    private void requireActive() {
        if (!active()) {
            throw new LuaError("Config '" + key + "' belongs to an unloaded script generation");
        }
    }

    /** File contents and revision captured for a single editor draft. */
    public static final class DiskDraft {
        private final ScriptConfigRegistration owner;
        private final Map<String, Object> values;
        private final byte[] revision;

        private DiskDraft(ScriptConfigRegistration owner, Map<String, Object> values, byte[] revision) {
            this.owner = owner;
            this.values = ScriptConfigValues.copy(values);
            this.revision = revision == null ? null : revision.clone();
        }

        public Map<String, Object> values() {
            return ScriptConfigValues.copy(values);
        }
    }

    static final class ApplyEvent {
        private final ScriptConfigRegistration registration;
        private final Map<String, Object> values;
        private final Map<String, Object> previous;
        private final List<String> changed;

        private ApplyEvent(ScriptConfigRegistration registration, Map<String, Object> values,
                Map<String, Object> previous, List<String> changed) {
            this.registration = registration;
            this.values = values;
            this.previous = previous;
            this.changed = changed;
        }

        void apply() {
            if (!registration.active()) {
                return;
            }
            LuaTable paths = new LuaTable();
            for (int index = 0; index < changed.size(); index++) {
                paths.set(index + 1, changed.get(index));
            }
            try {
                ScriptExecution.invoke(registration.owner, "config.onChange", registration.metadata.onChange,
                        LuaValue.varargsOf(new LuaValue[] { ScriptConfigValues.toLua(values),
                                ScriptConfigValues.toLua(previous), paths }));
                registration.applyError = null;
                registration.retry = null;
            } catch (Throwable error) {
                registration.applyError = error.getMessage() == null ? error.toString() : error.getMessage();
                registration.retry = this;
                LuaScriptErrors.add(registration.owner, "Config '" + registration.key
                        + "' was saved, but onChange failed: " + registration.applyError,
                        LuaScriptErrors.Outcome.UNKNOWN, error);
            }
        }
    }
}
