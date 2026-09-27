package betamoon.luaapi.audio;

import betamoon.assets.AssetKey;
import betamoon.client.audio.ClientAudio;
import betamoon.client.audio.ClientSounds;
import betamoon.client.audio.SoundAsset;
import betamoon.luaapi.resource.OverrideManager;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptAssetScope;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Pending, published, and layered effective sound-event metadata. */
public final class SoundEvents {
    private static final Map<AssetKey, Entry> PENDING = new LinkedHashMap<AssetKey, Entry>();
    private static final Map<AssetKey, Entry> PUBLISHED = new LinkedHashMap<AssetKey, Entry>();
    private static final Random RANDOM = new Random();

    static final OverrideManager.Property<Entry, SoundEventPatch> OVERRIDE_PROPERTY =
            new OverrideManager.Property<Entry, SoundEventPatch>("definition",
                    new OverrideManager.PropertyAdapter<Entry, SoundEventPatch>() {
                        public SoundEventPatch read(Entry target) {
                            SoundEventDefinition base = target.base;
                            return new SoundEventPatch(base.clips, Float.valueOf(base.volume),
                                    Float.valueOf(base.pitchMin), Float.valueOf(base.pitchMax),
                                    Float.valueOf(base.range), Boolean.valueOf(base.enabled));
                        }

                        public void write(Entry target, SoundEventPatch value) {
                            target.effective = value.apply(target.base);
                        }
                    }, new OverrideManager.ValueResolver<SoundEventPatch>() {
                        public SoundEventPatch resolve(SoundEventPatch base, List<SoundEventPatch> layers) {
                            List<SoundEventDefinition.Clip> clips = base.clips;
                            Float volume = base.volume;
                            Float pitchMin = base.pitchMin;
                            Float pitchMax = base.pitchMax;
                            Float range = base.range;
                            Boolean enabled = base.enabled;
                            for (SoundEventPatch layer : layers) {
                                clips = layer.clips == null ? clips : layer.clips;
                                volume = layer.volume == null ? volume : layer.volume;
                                pitchMin = layer.pitchMin == null ? pitchMin : layer.pitchMin;
                                pitchMax = layer.pitchMax == null ? pitchMax : layer.pitchMax;
                                range = layer.range == null ? range : layer.range;
                                enabled = layer.enabled == null ? enabled : layer.enabled;
                            }
                            return new SoundEventPatch(clips, volume, pitchMin, pitchMax, range, enabled);
                        }
                    });

    private SoundEvents() {
    }

    static synchronized void stage(SoundEventDefinition definition) {
        ScriptAssetScope.requireInitialization();
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        Entry existing = PUBLISHED.get(definition.key);
        if (PENDING.containsKey(definition.key) || (existing != null && !owner.equals(existing.owner))) {
            throw new LuaError("Sound event key is already owned: " + definition.key);
        }
        final List<SoundAsset> retained = acquire(definition.clips, "Sound event");
        final Entry entry = new Entry(owner, definition);
        PENDING.put(definition.key, entry);
        ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
            public void run() {
                remove(entry);
                close(retained);
            }
        });
    }

    public static synchronized void publish(String owner) {
        for (Entry entry : new ArrayList<Entry>(PENDING.values())) {
            if (owner.equals(entry.owner)) {
                PUBLISHED.put(entry.base.key, entry);
                PENDING.remove(entry.base.key);
            }
        }
    }

    static synchronized SoundEventDefinition find(AssetKey key) {
        Entry entry = findEntry(key);
        return entry == null ? null : entry.effective;
    }

    static synchronized Entry findEntry(AssetKey key) {
        Entry pending = PENDING.get(key);
        if (pending != null && pending.owner.equals(LuaScriptRegistry.getCurrentScriptFile())) {
            return pending;
        }
        return PUBLISHED.get(key);
    }

    static synchronized List<Entry> entries() {
        List<Entry> values = new ArrayList<Entry>(PUBLISHED.values());
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        for (Entry entry : PENDING.values()) {
            if (entry.owner.equals(owner) && !values.contains(entry)) {
                values.add(entry);
            }
        }
        return Collections.unmodifiableList(values);
    }

    public static AssetKey requireKey(LuaValue value, String path) {
        AssetKey key;
        if (value instanceof SoundEventReference) {
            key = ((SoundEventReference) value).key();
        } else {
            try {
                key = AssetKey.parse(value.checkjstring());
            } catch (IllegalArgumentException error) {
                throw new LuaError(path + ": " + error.getMessage());
            }
        }
        if (find(key) == null) {
            throw new LuaError(path + ": sound event is not registered: " + key);
        }
        return key;
    }

    public static void play(AssetKey key, double x, double y, double z,
            float volumeOverride, float pitchOverride, float rangeOverride) throws IOException {
        SoundEventDefinition event = find(key);
        if (event == null) {
            throw new IOException("Sound event is no longer registered: " + key);
        }
        if (!event.enabled) {
            return;
        }
        float volume = Float.isNaN(volumeOverride) ? event.volume : volumeOverride;
        float pitch = Float.isNaN(pitchOverride)
                ? event.pitchMin + RANDOM.nextFloat() * (event.pitchMax - event.pitchMin)
                : pitchOverride;
        float range = Float.isNaN(rangeOverride) ? event.range : rangeOverride;
        try (SoundAsset clip = ClientSounds.acquire(event.choose(RANDOM))) {
            if (clip.getContent().getValue().getFormat().getChannels() != 1) {
                throw new IOException("Positional sounds require a mono clip");
            }
            ClientAudio.play(clip.getContent().getValue(), (float) x, (float) y, (float) z,
                    true, volume, pitch, range);
        }
    }

    static List<SoundAsset> acquire(SoundEventPatch patch) {
        return patch.clips == null
                ? Collections.<SoundAsset>emptyList()
                : acquire(patch.clips, "Sound event override");
    }

    private static List<SoundAsset> acquire(List<SoundEventDefinition.Clip> clips, String context) {
        List<SoundAsset> retained = new ArrayList<SoundAsset>();
        try {
            for (SoundEventDefinition.Clip clip : clips) {
                retained.add(ClientSounds.acquire(clip.location));
            }
            return retained;
        } catch (IOException | RuntimeException error) {
            close(retained);
            throw new LuaError(context + ": " + error.getMessage());
        }
    }

    static void close(List<SoundAsset> sounds) {
        for (SoundAsset sound : sounds) {
            sound.close();
        }
    }

    private static synchronized void remove(Entry entry) {
        PENDING.remove(entry.base.key, entry);
        PUBLISHED.remove(entry.base.key, entry);
    }

    /** Immutable event metadata for diagnostics; decoded sounds stay private. */
    public static final class Description {
        public final AssetKey key;
        public final String owner;
        public final float volume;
        public final float pitchMin;
        public final float pitchMax;
        public final float range;
        public final boolean enabled;
        public final List<ClipDescription> clips;

        private Description(Entry entry) {
            SoundEventDefinition definition = entry.effective;
            key = definition.key;
            owner = entry.owner;
            volume = definition.volume;
            pitchMin = definition.pitchMin;
            pitchMax = definition.pitchMax;
            range = definition.range;
            enabled = definition.enabled;
            List<ClipDescription> values = new ArrayList<ClipDescription>();
            for (SoundEventDefinition.Clip clip : definition.clips) {
                values.add(new ClipDescription(clip));
            }
            clips = Collections.unmodifiableList(values);
        }
    }

    public static final class ClipDescription {
        public final String kind;
        public final String fallbackPath;
        public final String overridePath;
        public final boolean builtIn;
        public final double weight;

        private ClipDescription(SoundEventDefinition.Clip clip) {
            kind = clip.location.getKind().name().toLowerCase(java.util.Locale.ROOT);
            fallbackPath = clip.location.getFallback().toString();
            overridePath = clip.location.getOverride().toString();
            builtIn = clip.location.isBuiltin();
            weight = clip.weight;
        }
    }

    public static synchronized List<Description> snapshot() {
        List<Description> result = new ArrayList<Description>();
        for (Entry entry : PUBLISHED.values()) {
            result.add(new Description(entry));
        }
        Collections.sort(result, new Comparator<Description>() {
            public int compare(Description left, Description right) {
                return left.key.toString().compareTo(right.key.toString());
            }
        });
        return Collections.unmodifiableList(result);
    }

    static final class Entry {
        final String owner;
        final SoundEventDefinition base;
        volatile SoundEventDefinition effective;

        private Entry(String owner, SoundEventDefinition definition) {
            this.owner = owner;
            this.base = definition;
            this.effective = definition;
        }
    }
}
