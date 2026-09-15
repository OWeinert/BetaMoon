package betamoon.luaapi.audio;

import betamoon.assets.AssetKey;
import betamoon.client.assets.AssetLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Immutable event selection and playback defaults, separate from voices and
 * decoded audio.
 */
final class SoundEventDefinition {
    final AssetKey key;
    final List<Clip> clips;
    final float volume;
    final float pitchMin;
    final float pitchMax;
    final float range;
    private final double totalWeight;

    SoundEventDefinition(AssetKey key, List<Clip> clips, float volume, float pitchMin, float pitchMax, float range) {
        this.key = key;
        this.clips = Collections.unmodifiableList(new ArrayList<>(clips));
        this.volume = volume;
        this.pitchMin = pitchMin;
        this.pitchMax = pitchMax;
        this.range = range;
        double total = 0;
        for (Clip clip : clips) {
            total += clip.weight;
        }
        totalWeight = total;
    }

    AssetLocation choose(Random random) {
        double selected = random.nextDouble() * totalWeight;
        for (Clip clip : clips) {
            selected -= clip.weight;
            if (selected < 0) {
                return clip.location;
            }
        }
        return clips.get(clips.size() - 1).location;
    }

    static final class Clip {
        final AssetLocation location;
        final double weight;
        Clip(AssetLocation location, double weight) {
            this.location = location;
            this.weight = weight;
        }
    }
}
