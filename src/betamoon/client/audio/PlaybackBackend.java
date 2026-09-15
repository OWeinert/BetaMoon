package betamoon.client.audio;

import java.net.URL;

/**
 * Narrow one-shot backend boundary, independent of script and asset registries.
 */
interface PlaybackBackend {
    void load(String name, URL data);

    void create(String voice, String buffer, float x, float y, float z, boolean positional, float range);

    void volume(String voice, float volume);

    void pitch(String voice, float pitch);

    void play(String voice);

    boolean playing(String voice);

    void remove(String voice);

    void unload(String buffer);
}
