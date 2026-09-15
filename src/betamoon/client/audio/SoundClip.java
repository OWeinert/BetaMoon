package betamoon.client.audio;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFileFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.sound.sampled.AudioFormat;
import paulscode.sound.ICodec;
import paulscode.sound.SoundBuffer;
import paulscode.sound.codecs.CodecJOrbis;
import paulscode.sound.codecs.CodecWav;

/**
 * Fully decoded, detached PCM. No live pack stream or temporary file is
 * retained by playback.
 */
public final class SoundClip {
    public static final int MAX_DECODED_BYTES = 32 * 1024 * 1024;
    private final byte[] samples;
    private final AudioFormat format;

    private SoundClip(byte[] samples, AudioFormat format) {
        this.samples = samples;
        this.format = format;
    }

    public static SoundClip decode(byte[] bytes, String extension) throws IOException {
        if (!extension.equals("wav") && !extension.equals("ogg")) {
            throw new IOException("Sound must be OGG or WAV");
        }
        Path temporary = Files.createTempFile("betamoon-sound-", "." + extension);
        ICodec codec = extension.equals("wav") ? new CodecWav() : new CodecJOrbis();
        try {
            Files.write(temporary, bytes);
            if (!codec.initialize(temporary.toUri().toURL())) {
                throw new IOException("Sound decoder could not initialize " + extension + " clip");
            }
            AudioFormat format = codec.getAudioFormat();
            if (format == null || format.getChannels() < 1 || format.getChannels() > 2 || format.getSampleRate() <= 0
                    || !Float.isFinite(format.getSampleRate()) || format.getFrameSize() <= 0
                    || (format.getSampleSizeInBits() != 8 && format.getSampleSizeInBits() != 16)) {
                throw new IOException("Sound must have a valid mono or stereo audio format");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            while (!codec.endOfStream()) {
                SoundBuffer chunk = codec.read();
                if (chunk == null || chunk.audioData == null || chunk.audioData.length == 0) {
                    if (codec.endOfStream()) {
                        break;
                    }
                    throw new IOException("Sound decoder stopped before the end of the clip");
                }
                if (chunk.audioData.length > MAX_DECODED_BYTES - output.size()) {
                    throw new IOException("Decoded sound exceeds " + MAX_DECODED_BYTES + " bytes");
                }
                output.write(chunk.audioData);
            }
            if (output.size() == 0) {
                throw new IOException("Sound contains no audio samples");
            }
            return new SoundClip(output.toByteArray(), format);
        } catch (RuntimeException error) {
            throw new IOException("Invalid " + extension + " sound: " + error.getMessage(), error);
        } finally {
            try {
                codec.cleanup();
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public int getDecodedBytes() {
        return samples.length;
    }

    /**
     * The older Beta sound backend accepts URLs, not predecoded buffers. This URL
     * never accesses a network.
     */
    public URL playbackUrl(String name) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(samples), format,
                samples.length / format.getFrameSize())) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, output);
        }
        byte[] wave = output.toByteArray();
        return new URL(null, "betamoon:/" + name, new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                return new URLConnection(url) {
                    public void connect() {
                    }

                    public InputStream getInputStream() {
                        return new ByteArrayInputStream(wave);
                    }

                    public int getContentLength() {
                        return wave.length;
                    }
                };
            }
        });
    }

    public AudioFormat getFormat() {
        return format;
    }

    public boolean sameContent(SoundClip other) {
        return format.matches(other.format) && Arrays.equals(samples, other.samples);
    }
}
