package betamoon.luaapi.audio;

import betamoon.assets.AssetKey;
import betamoon.client.audio.ClientAudio;
import betamoon.client.audio.ClientSounds;
import betamoon.client.audio.SoundAsset;
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

/**
 * Pending and published event metadata; generation-specific cleanup follows
 * script ownership.
 */
public final class SoundEvents {
    private static final Map<AssetKey, Entry> PENDING = new LinkedHashMap<>();
    private static final Map<AssetKey, Entry> PUBLISHED = new LinkedHashMap<>();
    private static final Random RANDOM = new Random();

    private SoundEvents() {
    }

    static synchronized void stage(SoundEventDefinition definition) {
        ScriptAssetScope.requireInitialization();
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        Entry existing = PUBLISHED.get(definition.key);
        if (PENDING.containsKey(definition.key) || (existing != null && !owner.equals(existing.owner))) {
            throw new LuaError("Sound event key is already owned: " + definition.key);
        }
        List<SoundAsset> retained = new ArrayList<>();
        try {
            for (SoundEventDefinition.Clip clip : definition.clips) {
                retained.add(ClientSounds.acquire(clip.location));
            }
        } catch (IOException | RuntimeException error) {
            for (SoundAsset sound : retained) {
                sound.close();
            }
            throw new LuaError("Sound event: " + error.getMessage());
        }
        Entry entry = new Entry(owner, definition);
        PENDING.put(definition.key, entry);
        ScriptResourceTracker.track(() -> {
            remove(entry);
            for (SoundAsset sound : retained) {
                sound.close();
            }
        });
    }

    public static synchronized void publish(String owner) {
        for (Entry entry : new ArrayList<>(PENDING.values())) {
            if (owner.equals(entry.owner)) {
                PUBLISHED.put(entry.definition.key, entry);
                PENDING.remove(entry.definition.key);
            }
        }
    }

    static synchronized SoundEventDefinition find(AssetKey key) {
        Entry pending = PENDING.get(key);
        if (pending != null && pending.owner.equals(LuaScriptRegistry.getCurrentScriptFile())) {
            return pending.definition;
        }
        Entry entry = PUBLISHED.get(key);
        return entry == null ? null : entry.definition;
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

    private static synchronized void remove(Entry entry) {
        PENDING.remove(entry.definition.key, entry);
        PUBLISHED.remove(entry.definition.key, entry);
    }

    /** Immutable event metadata for diagnostics; decoded sounds stay private. */
    public static final class Description {
        public final AssetKey key;
        public final String owner;
        public final float volume;
        public final float pitchMin;
        public final float pitchMax;
        public final float range;
        public final List<ClipDescription> clips;

        private Description(Entry entry) {
            SoundEventDefinition definition = entry.definition;
            key = definition.key;
            owner = entry.owner;
            volume = definition.volume;
            pitchMin = definition.pitchMin;
            pitchMax = definition.pitchMax;
            range = definition.range;
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
            @Override
            public int compare(Description left, Description right) {
                return left.key.toString().compareTo(right.key.toString());
            }
        });
        return Collections.unmodifiableList(result);
    }

    private static final class Entry {
        private final String owner;
        private final SoundEventDefinition definition;
        private Entry(String owner, SoundEventDefinition definition) {
            this.owner = owner;
            this.definition = definition;
        }
    }
}
