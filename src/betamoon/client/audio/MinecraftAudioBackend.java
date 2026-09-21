package betamoon.client.audio;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import net.minecraft.src.SoundManager;
import paulscode.sound.SoundSystem;
import paulscode.sound.SoundSystemConfig;

/**
 * Adapter to the existing Beta sound engine; it does not create another audio
 * device.
 */
final class MinecraftAudioBackend implements PlaybackBackend {
    private static final Field SYSTEM_FIELD = findSystemField();
    private static MinecraftAudioBackend current;
    private final SoundSystem system;

    private MinecraftAudioBackend(SoundSystem system) {
        this.system = system;
    }

    static PlaybackBackend find() {
        try {
            SoundSystem system = SYSTEM_FIELD == null ? null : (SoundSystem) SYSTEM_FIELD.get(null);
            if (system == null) {
                current = null;
                return null;
            }
            if (current == null || current.system != system) {
                current = new MinecraftAudioBackend(system);
            }
            return current;
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Minecraft sound backend is unavailable", error);
        }
    }

    private static Field findSystemField() {
        for (Field field : SoundManager.class.getDeclaredFields()) {
            if (field.getType() == SoundSystem.class && Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    public void load(String name, URL data) {
        system.loadSound(data, name);
    }

    public void create(String voice, String buffer, float x, float y, float z, boolean positional, float range) {
        system.newSource(false, voice, buffer, false, x, y, z,
                positional ? SoundSystemConfig.ATTENUATION_LINEAR : SoundSystemConfig.ATTENUATION_NONE, range);
    }

    public void volume(String voice, float volume) {
        system.setVolume(voice, volume);
    }

    public void pitch(String voice, float pitch) {
        system.setPitch(voice, pitch);
    }

    public void play(String voice) {
        system.play(voice);
    }

    public boolean playing(String voice) {
        return system.playing(voice);
    }

    public void remove(String voice) {
        system.stop(voice);
        system.removeSource(voice);
    }

    public void unload(String buffer) {
        system.unloadSound(buffer);
    }
}
