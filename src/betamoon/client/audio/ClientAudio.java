package betamoon.client.audio;

import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.ModLoader;

/**
 * Local one-shot playback with exact names, separate range/volume and bounded
 * owned voices.
 */
public final class ClientAudio {
    private static final int MAX_VOICES = 64;
    private static final int MAX_BUFFER_BYTES = 64 * 1024 * 1024;
    private static final Set<String> TRACKED_OWNERS = new HashSet<>();
    private static int bufferBytes;
    private static final List<Voice> VOICES = new ArrayList<>();
    private static final Map<SoundClip, Buffer> BUFFERS = new IdentityHashMap<>();
    private static PlaybackBackend backend;
    private static Object previousWorld;
    private static long nextVoice;

    private ClientAudio() {
    }

    public static void play(SoundClip clip, float x, float y, float z, boolean positional, float volume, float pitch,
            float range) throws IOException {
        Minecraft minecraft = ModLoader.getMinecraftInstance();
        if (minecraft == null || minecraft.gameSettings == null || minecraft.gameSettings.soundVolume == 0) {
            return;
        }
        play(MinecraftAudioBackend.find(), clip, x, y, z, positional, volume, pitch, range,
                minecraft.gameSettings.soundVolume);
    }

    static void play(PlaybackBackend system, SoundClip clip, float x, float y, float z, boolean positional,
            float volume, float pitch, float range, float masterVolume) throws IOException {
        if (system == null || masterVolume == 0) {
            return;
        }
        updateBackend(system);
        while (VOICES.size() >= MAX_VOICES || (!BUFFERS.containsKey(clip)
                && bufferBytes + clip.getDecodedBytes() > MAX_BUFFER_BYTES && !VOICES.isEmpty())) {
            remove(VOICES.get(0));
        }
        Buffer buffer = BUFFERS.get(clip);
        if (buffer == null) {
            String name = "betamoon-buffer-" + (++nextVoice) + ".wav";
            buffer = new Buffer(name);
            system.load(name, clip.playbackUrl(name));
            BUFFERS.put(clip, buffer);
            bufferBytes += clip.getDecodedBytes();
        }
        buffer.references++;
        String name = "betamoon-voice-" + (++nextVoice);
        Voice voice = new Voice(name, clip, buffer, volume);
        VOICES.add(voice);
        try {
            system.create(name, buffer.name, x, y, z, positional, range);
            system.volume(name, volume * masterVolume);
            system.pitch(name, pitch);
            system.play(name);
        } catch (RuntimeException error) {
            remove(voice);
            throw error;
        }
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner != null && TRACKED_OWNERS.add(owner)) {
            ScriptResourceTracker.track(() -> {
                for (Voice owned : new ArrayList<>(VOICES)) {
                    if (owner.equals(owned.owner)) {
                        remove(owned);
                    }
                }
                TRACKED_OWNERS.remove(owner);
            });
        }
    }

    /**
     * Poll once per client tick; settings affect active voices and world changes
     * release them.
     */
    public static void tick() {
        Minecraft minecraft = ModLoader.getMinecraftInstance();
        if (minecraft == null) {
            return;
        }
        tick(MinecraftAudioBackend.find(), minecraft.theWorld, minecraft.gameSettings.soundVolume);
    }

    static void tick(PlaybackBackend current, Object world, float masterVolume) {
        if (previousWorld != null && previousWorld != world) {
            clear();
        }
        previousWorld = world;
        if (backend != current) {
            updateBackend(current);
        }
        if (backend == null) {
            return;
        }
        for (Voice voice : new ArrayList<>(VOICES)) {
            if (backend.playing(voice.name)) {
                voice.started = true;
                backend.volume(voice.name, voice.volume * masterVolume);
            } else if (voice.started || System.nanoTime() - voice.created > 2_000_000_000L) {
                remove(voice);
            }
        }
    }

    private static void updateBackend(PlaybackBackend current) {
        if (backend != current) {
            clear();
            backend = current;
        }
    }

    private static void clear() {
        for (Voice voice : new ArrayList<>(VOICES)) {
            remove(voice);
        }
    }

    private static void remove(Voice voice) {
        if (!VOICES.remove(voice)) {
            return;
        }
        if (backend != null) {
            backend.remove(voice.name);
        }
        if (--voice.buffer.references == 0) {
            BUFFERS.remove(voice.clip);
            bufferBytes -= voice.clip.getDecodedBytes();
            if (backend != null) {
                backend.unload(voice.buffer.name);
            }
        }
    }

    private static final class Buffer {
        private final String name;
        private int references;
        private Buffer(String name) {
            this.name = name;
        }
    }

    private static final class Voice {
        private final String name;
        private final String owner = LuaScriptRegistry.getCurrentScriptFile();
        private final SoundClip clip;
        private final Buffer buffer;
        private final float volume;
        private final long created = System.nanoTime();
        private boolean started;
        private Voice(String name, SoundClip clip, Buffer buffer, float volume) {
            this.name = name;
            this.clip = clip;
            this.buffer = buffer;
            this.volume = volume;
        }
    }
}
