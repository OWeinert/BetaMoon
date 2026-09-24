package betamoon.client.audio;

import betamoon.assets.io.ResolvedAsset;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.client.assets.AssetLocation;
import betamoon.client.assets.ClientAssets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Clip cache and refresh. Already playing voices keep their detached previous
 * PCM version.
 */
public final class ClientSounds {
    private static final Map<String, SoundAsset> SOUNDS = new LinkedHashMap<>();
    private static final Map<String, Map<String, SoundAsset>> PLAYBACK_ASSETS = new LinkedHashMap<>();
    private ClientSounds() {
    }

    public static SoundAsset acquire(AssetLocation location) throws IOException {
        SoundAsset existing = SOUNDS.get(location.getCacheKey());
        if (existing != null) {
            return existing.retain();
        }
        SoundAsset created = new SoundAsset(location, resolve(location));
        SOUNDS.put(location.getCacheKey(), created);
        return created;
    }

    /**
     * Cache direct-path playback across callbacks, releasing all pins when the
     * calling script unloads.
     */
    public static void retainForPlayback(AssetLocation location) throws IOException {
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) {
            return;
        }
        Map<String, SoundAsset> owned = PLAYBACK_ASSETS.get(owner);
        if (owned == null) {
            owned = new LinkedHashMap<>();
            PLAYBACK_ASSETS.put(owner, owned);
            Map<String, SoundAsset> captured = owned;
            ScriptResourceTracker.track(() -> {
                PLAYBACK_ASSETS.remove(owner, captured);
                for (SoundAsset asset : captured.values()) {
                    asset.close();
                }
            });
        }
        if (!owned.containsKey(location.getCacheKey())) {
            owned.put(location.getCacheKey(), acquire(location));
        }
    }

    static void release(SoundAsset asset) {
        SOUNDS.remove(asset.getLocation().getCacheKey(), asset);
    }

    public static void refresh(Consumer<String> warnings) {
        for (SoundAsset asset : new ArrayList<>(SOUNDS.values())) {
            try {
                ResolvedAsset<SoundClip> next = resolve(asset.getLocation());
                asset.replace(next);
            } catch (IOException | IllegalArgumentException error) {
                warnings.accept("Sound " + asset.getLocation().getCacheKey() + ": " + error.getMessage()
                        + "; keeping the previous usable clip");
            }
        }
    }

    private static ResolvedAsset<SoundClip> resolve(AssetLocation location) throws IOException {
        String path = location.getFallback().toString().toLowerCase(Locale.ROOT);
        String extension = path.substring(path.lastIndexOf('.') + 1);
        return ClientAssets.getResolver(location).resolve(location.getCacheKey(), location.getFallback(),
                location.getOverride(), 8 * 1024 * 1024, bytes -> SoundClip.decode(bytes, extension));
    }
}
