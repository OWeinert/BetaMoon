package betamoon.luaapi.audio;

import betamoon.assets.AssetKey;
import betamoon.client.audio.ClientSounds;
import betamoon.client.audio.SoundAsset;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptAssetScope;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;

/**
 * Pending and published event metadata; generation-specific cleanup follows
 * script ownership.
 */
public final class SoundEvents {
    private static final Map<AssetKey, Entry> PENDING = new LinkedHashMap<>();
    private static final Map<AssetKey, Entry> PUBLISHED = new LinkedHashMap<>();

    private SoundEvents() {
    }

    static void stage(SoundEventDefinition definition) {
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
            PENDING.remove(definition.key, entry);
            PUBLISHED.remove(definition.key, entry);
            for (SoundAsset sound : retained) {
                sound.close();
            }
        });
    }

    public static void publish(String owner) {
        for (Entry entry : new ArrayList<>(PENDING.values())) {
            if (owner.equals(entry.owner)) {
                PUBLISHED.put(entry.definition.key, entry);
                PENDING.remove(entry.definition.key);
            }
        }
    }

    static SoundEventDefinition find(AssetKey key) {
        Entry pending = PENDING.get(key);
        if (pending != null && pending.owner.equals(LuaScriptRegistry.getCurrentScriptFile())) {
            return pending.definition;
        }
        Entry entry = PUBLISHED.get(key);
        return entry == null ? null : entry.definition;
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
