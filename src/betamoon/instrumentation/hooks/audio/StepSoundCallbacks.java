package betamoon.instrumentation.hooks.audio;

import betamoon.BetaMoonCommon;
import betamoon.assets.AssetKey;
import betamoon.luaapi.audio.BlockStepSounds;
import betamoon.luaapi.audio.SoundEvents;
import java.io.IOException;

/** Native playback callback; no Lua runs in the audio path. */
public final class StepSoundCallbacks {
    private StepSoundCallbacks() {
    }

    public static int play(String name, float x, float y, float z, float volume, float pitch) {
        if (!BlockStepSounds.isMarker(name)) {
            return 0;
        }
        AssetKey event = BlockStepSounds.eventForMarker(name);
        if (event != null && volume > 0.0F) {
            try {
                SoundEvents.playStep(event, x, y, z, volume, pitch);
            } catch (IOException error) {
                BetaMoonCommon.LOGGER.warning("Step sound " + name + ": " + error.getMessage());
            }
        }
        return 1;
    }

    public static void afterPlay() {
        // Capture handles custom names; vanilla playback completes unchanged.
    }
}
