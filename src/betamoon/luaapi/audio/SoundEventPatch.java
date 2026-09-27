package betamoon.luaapi.audio;

import java.util.List;

/** Parsed partial sound-event definition, applied in override precedence order. */
final class SoundEventPatch {
    final List<SoundEventDefinition.Clip> clips;
    final Float volume;
    final Float pitchMin;
    final Float pitchMax;
    final Float range;
    final Boolean enabled;

    SoundEventPatch(List<SoundEventDefinition.Clip> clips, Float volume, Float pitchMin, Float pitchMax, Float range,
            Boolean enabled) {
        this.clips = clips;
        this.volume = volume;
        this.pitchMin = pitchMin;
        this.pitchMax = pitchMax;
        this.range = range;
        this.enabled = enabled;
    }

    SoundEventDefinition apply(SoundEventDefinition base) {
        return new SoundEventDefinition(base.key, clips == null ? base.clips : clips,
                volume == null ? base.volume : volume.floatValue(),
                pitchMin == null ? base.pitchMin : pitchMin.floatValue(),
                pitchMax == null ? base.pitchMax : pitchMax.floatValue(),
                range == null ? base.range : range.floatValue(),
                enabled == null ? base.enabled : enabled.booleanValue());
    }
}
