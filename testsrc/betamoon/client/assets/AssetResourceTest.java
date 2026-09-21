package betamoon.client.assets;

import betamoon.assets.AssetKind;
import betamoon.assets.AssetPath;
import betamoon.assets.io.AssetProvider;
import betamoon.assets.io.AssetResolver;
import betamoon.assets.io.FileAssetProvider;
import betamoon.assets.io.ResolvedAsset;
import betamoon.assets.io.ZipAssetProvider;
import betamoon.client.audio.SoundClip;
import betamoon.client.audio.ClientSounds;
import betamoon.client.audio.SoundAsset;
import betamoon.instrumentation.hooks.texture.TextureResourceCallbacks;
import betamoon.resources.LuaTextureResources;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.util.stream.Stream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import net.minecraft.src.TexturePackBase;
import paulscode.sound.codecs.CodecWav;

/**
 * Exercises actual providers, codecs, detached streams and refresh without a
 * graphics/audio device.
 */
public final class AssetResourceTest {
    private AssetResourceTest() {
    }

    public static void main(String[] arguments) throws Exception {
        Path root = Files.createTempDirectory("betamoon-asset-test-");
        try {
            verifyProviders(root);
            verifyRefreshAndVirtualStreams(root);
            verifyAudio();
            verifySoundOverrides(root);
            System.out.println("Asset resources passed: ZIP fallback, cache/refresh, virtual PNGs, WAV and OGG.");
        } finally {
            deleteTree(root);
        }
    }

    private static void verifyProviders(Path root) throws Exception {
        Files.write(root.resolve("Guard.png"), png(0xffff0000, 16));
        Path pack = root.resolve("pack.zip");
        zip(pack, "bm_assets/Guard.png", png(0xff00ff00, 16));
        List<String> warnings = new ArrayList<>();
        AssetResolver resolver = new AssetResolver(new FileAssetProvider(root.toFile()),
                new ZipAssetProvider(pack.toFile()), warnings::add);
        AssetPath fallback = AssetPath.parse("Guard.png");
        ResolvedAsset<TextureImage> selected = resolver.resolve("guard", fallback, fallback.getDirectOverridePath(),
                100000, TextureImage::decode);
        require(selected.getSourceKind().equals("pack") && pixel(selected.getValue()) == 0xff00ff00,
                "Pack overrides default");
        zip(pack, "betamoon/Guard.png", png(0xff0000ff, 16));
        selected = resolver.resolve("guard", fallback, fallback.getDirectOverridePath(), 100000, TextureImage::decode);
        require(selected.getSourceKind().equals("script") && pixel(selected.getValue()) == 0xffff0000,
                "Entries outside bm_assets must not override script assets");
        zip(pack, "bm_assets/Guard.png", new byte[]{1, 2, 3});
        selected = resolver.resolve("guard", fallback, fallback.getDirectOverridePath(), 100000, TextureImage::decode);
        require(pixel(selected.getValue()) == 0xffff0000 && warnings.size() == 1,
                "Corrupt overrides fall back with diagnostics");
        require(warnings.get(0).contains("pack.zip") && warnings.get(0).contains("bm_assets/Guard.png"),
                "Diagnostics identify pack and path");
        zip(pack, "bm_assets/guard.png", png(0xff0000ff, 16));
        selected = resolver.resolve("guard", fallback, fallback.getDirectOverridePath(), 100000, TextureImage::decode);
        require(selected.getSourceKind().equals("script"), "ZIP lookup preserves case");
        expectIo(() -> new ZipAssetProvider(pack.toFile()).read(AssetPath.parse("bm_assets/guard.png"), 4));
        expectIo(() -> TextureImage.decode(png(0, 1024)));
        expectIo(() -> new FileAssetProvider(root.toFile()).read(fallback, 4));
        Path outside = Files.createTempFile("betamoon-outside-", ".png");
        try {
            Path link = root.resolve("outside.png");
            try {
                Files.createSymbolicLink(link, outside);
                expectIo(() -> new FileAssetProvider(root.toFile()).read(AssetPath.parse("outside.png"), 100));
                expectIo(() -> new FileAssetProvider(root.toFile()).exists(AssetPath.parse("outside.png")));
            } catch (UnsupportedOperationException | FileSystemException unavailable) {
                System.out.println("Symbolic-link containment fixture unavailable on this filesystem.");
            }
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    private static void verifyRefreshAndVirtualStreams(Path root) throws Exception {
        Files.write(root.resolve("cache.png"), png(0xffff0000, 16));
        List<String> warnings = new ArrayList<>();
        CountingProvider defaults = new CountingProvider(new FileAssetProvider(root.toFile()));
        ClientAssets.useProviders(defaults, null, warnings::add);
        AssetLocation location = new AssetLocation(AssetKind.TEXTURE, AssetPath.parse("cache.png"));
        TextureAsset first = ClientAssets.acquireTexture(location);
        TextureAsset second = ClientAssets.acquireTexture(location);
        require(first == second && defaults.reads == 1, "Repeated binding acquisition must not decode again");
        int[] updates = {0};
        first.listen(image -> updates[0]++);
        String virtual = LuaTextureResources.register(location);
        TexturePackBase vanilla = new TexturePackBase() {
            public InputStream getResourceAsStream(String path) {
                return new ByteArrayInputStream(new byte[]{42});
            }
        };
        for (int i = 0; i < 5; i++) {
            try (InputStream input = TextureResourceCallbacks.openTexture(vanilla, virtual)) {
                require(ImageIO.read(input).getRGB(0, 0) == 0xffff0000, "Virtual resource returns decoded PNG data");
            }
        }
        require(defaults.reads == 1, "Draw-time virtual lookup performs no provider reads");
        try (InputStream input = TextureResourceCallbacks.openTexture(vanilla, "/terrain.png")) {
            require(input.read() == 42, "Ordinary resources still use the native provider");
        }
        ClientAssets.refresh();
        require(updates[0] == 0, "Unchanged bytes must not notify texture upload consumers");
        Files.write(root.resolve("cache.png"), png(0xff0000ff, 16));
        ClientAssets.refresh();
        require(updates[0] == 1 && pixel(first.getContent().getValue()) == 0xff0000ff,
                "Refresh updates shared binding once");
        Files.write(root.resolve("cache.png"), new byte[]{0});
        ClientAssets.refresh();
        require(pixel(first.getContent().getValue()) == 0xff0000ff && !warnings.isEmpty(),
                "Failed refresh preserves usable pixels");
        LuaTextureResources.release(virtual);
        second.close();
        first.close();
        Files.write(root.resolve("cache.png"), png(0xff00ff00, 16));
        try (TextureAsset replacement = ClientAssets.acquireTexture(location)) {
            require(pixel(replacement.getContent().getValue()) == 0xff00ff00,
                    "Released cache entries must not retain stale images");
        }
    }

    private static void verifyAudio() throws Exception {
        SoundClip wav = SoundClip.decode(wav(), "wav");
        require(wav.getDecodedBytes() > 0, "WAV decoder produces samples");
        SoundClip ogg = SoundClip.decode(Files.readAllBytes(Paths.get("testsrc/betamoon/assets/fixtures/tone.ogg")),
                "ogg");
        require(ogg.getDecodedBytes() > 0, "OGG decoder produces samples");
        CodecWav backendCodec = new CodecWav();
        try {
            require(backendCodec.initialize(ogg.playbackUrl("test.wav")), "Native codec accepts detached memory URL");
            require(backendCodec.readAll().audioData.length > 0,
                    "Detached URL remains readable after temporary decode file removal");
        } finally {
            backendCodec.cleanup();
        }
        expectIo(() -> SoundClip.decode(new byte[]{0, 1, 2}, "ogg"));
        expectIo(() -> SoundClip.decode(new byte[]{0, 1, 2}, "wav"));
    }

    private static void verifySoundOverrides(Path root) throws Exception {
        Files.write(root.resolve("hit.wav"), wav());
        Path pack = root.resolve("sound-pack.zip");
        byte[] changed = wav();
        changed[changed.length - 2] = 64;
        zip(pack, "bm_assets/hit.wav", changed);
        List<String> warnings = new ArrayList<>();
        ClientAssets.useProviders(new FileAssetProvider(root.toFile()), new ZipAssetProvider(pack.toFile()),
                warnings::add);
        AssetLocation location = new AssetLocation(AssetKind.SOUND, AssetPath.parse("hit.wav"));
        try (SoundAsset sound = ClientSounds.acquire(location)) {
            SoundClip original = sound.getContent().getValue();
            require(sound.getContent().getSourceKind().equals("pack"), "Sound overrides must use pack bytes");
            zip(pack, "bm_assets/hit.wav", new byte[]{1, 2, 3});
            ClientAssets.refresh();
            require(sound.getContent().getSourceKind().equals("script") && !warnings.isEmpty(),
                    "Invalid sound overrides must fall back with diagnostics");
            require(!original.sameContent(sound.getContent().getValue()),
                    "Replacement must produce a separate version");
            try (InputStream oldVoice = original.playbackUrl("old.wav").openStream()) {
                require(oldVoice.read() == 'R',
                        "Existing voices retain readable previous content after pack replacement");
            }
            ClientAssets.useProviders(new FileAssetProvider(root.toFile()), null, warnings::add);
            ClientAssets.refresh();
            require(sound.getContent().getSourceKind().equals("script"), "Default pack restores script clips");
        }
    }

    public static byte[] png(int argb, int size) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setRGB(x, y, argb);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    public static byte[] wav() throws IOException {
        byte[] samples = new byte[2205 * 2];
        AudioFormat format = new AudioFormat(22050, 16, 1, true, false);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (AudioInputStream input = new AudioInputStream(new ByteArrayInputStream(samples), format, 2205)) {
            AudioSystem.write(input, AudioFileFormat.Type.WAVE, output);
        }
        return output.toByteArray();
    }

    private static void zip(Path file, String name, byte[] bytes) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(file))) {
            output.putNextEntry(new ZipEntry(name));
            output.write(bytes);
            output.closeEntry();
        }
    }

    private static int pixel(TextureImage image) {
        return image.getImage().getRGB(0, 0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectIo(IoAction action) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError("Expected asset validation failure");
    }
    private interface IoAction {
        void run() throws Exception;
    }

    public static void deleteTree(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            Path[] paths = files.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class CountingProvider implements AssetProvider {
        private final AssetProvider delegate;
        private int reads;
        private CountingProvider(AssetProvider delegate) {
            this.delegate = delegate;
        }

        public boolean exists(AssetPath path) throws IOException {
            return delegate.exists(path);
        }

        public byte[] read(AssetPath path, int limit) throws IOException {
            reads++;
            return delegate.read(path, limit);
        }

        public String getName() {
            return delegate.getName();
        }
    }
}
