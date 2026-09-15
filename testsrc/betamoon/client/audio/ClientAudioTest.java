package betamoon.client.audio;

import betamoon.client.assets.AssetResourceTest;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.IOException;
import java.lang.reflect.Method;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Verifies voice/buffer lifetime and volume semantics without an audio device.
 */
public final class ClientAudioTest {
    private ClientAudioTest() {
    }

    public static void main(String[] arguments) throws Exception {
        FakeBackend backend = new FakeBackend();
        SoundClip clip = SoundClip.decode(AssetResourceTest.wav(), "wav");
        Object world = new Object();
        Method scriptOwner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        scriptOwner.setAccessible(true);
        scriptOwner.invoke(null, "sound_owner.lua");
        try {
            ClientAudio.tick(backend, world, 1);
            ClientAudio.play(backend, clip, 1, 2, 3, true, 0.5f, 1.2f, 32, 0.4f);
            require(backend.loads == 1 && backend.voices.size() == 1, "One voice loads one exact buffer");
            require(Math.abs(backend.lastVolume - 0.2f) < 0.00001 && backend.lastRange == 32,
                    "Master volume scales gain independently of attenuation range");
            ClientAudio.tick(backend, world, 0.2f);
            require(Math.abs(backend.lastVolume - 0.1f) < 0.00001, "Active voices follow changed sound settings");
            backend.playing = false;
            ClientAudio.tick(backend, world, 1);
            require(backend.voices.isEmpty() && backend.unloads == 1, "Finished voices release their final buffer");
            backend.playing = true;
            for (int i = 0; i < 65; i++) {
                ClientAudio.play(backend, clip, 0, 0, 0, false, 1, 1, 16, 1);
            }
            require(backend.voices.size() == 64 && backend.loads == 2, "Voice budget reuses shared buffers");
            ScriptResourceTracker.unload("sound_owner.lua");
            require(backend.voices.isEmpty() && backend.unloads == 2, "Script cleanup stops all owned voices");
            ClientAudio.play(backend, clip, 0, 0, 0, false, 1, 1, 16, 1);
            ClientAudio.tick(backend, new Object(), 1);
            require(backend.voices.isEmpty(), "World changes release local voices");
            backend.failCreate = true;
            try {
                ClientAudio.play(backend, clip, 0, 0, 0, false, 1, 1, 16, 1);
                throw new AssertionError("Expected backend failure");
            } catch (IllegalStateException expected) {
                require(backend.voices.isEmpty() && backend.loads == backend.unloads,
                        "Failed source creation releases its buffer");
            }
            System.out.println(
                    "Client audio passed: ownership, budgets, settings, completion, world changes and failures.");
        } finally {
            ScriptResourceTracker.unload("sound_owner.lua");
            scriptOwner.invoke(null, new Object[]{null});
            ClientAudio.tick(null, null, 0);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FakeBackend implements PlaybackBackend {
        private final Map<String, String> voices = new HashMap<>();
        private int loads;
        private int unloads;
        private float lastVolume;
        private float lastRange;
        private boolean playing = true;
        private boolean failCreate;
        public void load(String name, URL data) {
            try (InputStream input = data.openStream()) {
                require(input.read() == 'R', "Backend must receive detached WAV bytes");
            } catch (IOException error) {
                throw new AssertionError(error);
            }
            loads++;
        }

        public void create(String voice, String buffer, float x, float y, float z, boolean positional, float range) {
            if (failCreate) {
                throw new IllegalStateException("Expected source failure");
            }
            voices.put(voice, buffer);
            lastRange = range;
        }

        public void volume(String voice, float volume) {
            lastVolume = volume;
        }

        public void pitch(String voice, float pitch) {
        }

        public void play(String voice) {
        }

        public boolean playing(String voice) {
            return playing;
        }

        public void remove(String voice) {
            voices.remove(voice);
        }

        public void unload(String buffer) {
            unloads++;
        }
    }
}
