package betamoon.client.assets;

import betamoon.assets.AssetKind;
import betamoon.assets.AssetPath;
import betamoon.assets.TextureAnimation;
import betamoon.assets.io.FileAssetProvider;
import betamoon.assets.io.ZipAssetProvider;
import betamoon.luaapi.asset.TextureInputs;
import betamoon.resources.EnumTexAtlas;
import betamoon.resources.LuaTextureResources;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Frame timing, shared bindings, validation, and reload fallback without OpenGL. */
public final class AnimatedTexturesTest {
    private AnimatedTexturesTest() {
    }

    public static void main(String[] arguments) throws Exception {
        Path root = Files.createTempDirectory("betamoon-animation-test-");
        try {
            Files.write(root.resolve("strip.png"), strip(16, 0xffff0000, 0xff00ff00, 0xff0000ff));
            List<String> warnings = new ArrayList<>();
            ClientAssets.useProviders(new FileAssetProvider(root.toFile()), null, warnings::add);
            RecordingBackend backend = new RecordingBackend();
            AtlasTextures.useBackend(backend);

            LuaTable descriptor = new LuaTable();
            descriptor.set("asset", "strip.png");
            LuaTable animation = new LuaTable();
            animation.set("frameTicks", 2);
            descriptor.set("animation", animation);
            AssetLocation location = TextureInputs.read(descriptor);
            require(location.getTextureAnimation() != null, "Inline texture animation must be parsed");
            int block = AtlasTextures.register(EnumTexAtlas.BLOCKS, location);
            int item = AtlasTextures.register(EnumTexAtlas.ITEMS, location);
            require(block == item && backend.allocations == 2, "Each atlas allocates its own slot");
            String standalone = LuaTextureResources.register(location);
            require(LuaTextureResources.dimensions(standalone)[0] == 16
                    && LuaTextureResources.dimensions(standalone)[1] == 16,
                    "Standalone bindings report one frame's dimensions");
            AtlasTextures.uploadChanged();
            require(backend.uploads == 2 && backend.color == 0xffff0000, "First frame uploads to both atlases");
            ClientAssets.animationTick(false);
            require(backend.uploads == 2, "Frame remains unchanged for its first tick");
            ClientAssets.animationTick(false);
            require(backend.uploads == 4 && backend.color == 0xff00ff00,
                    "Second tick advances both atlas bindings together");
            require(ImageIO.read(LuaTextureResources.open(standalone)).getRGB(0, 0) == 0xff00ff00,
                    "Standalone binding observes the same current frame");
            ClientAssets.animationTicks(0, false);
            require(backend.uploads == 4, "Render frames without a client tick must not advance animations");
            ClientAssets.animationTicks(4, false);
            require(backend.uploads == 6 && backend.color == 0xffff0000,
                    "Several client ticks between renders must advance the clock and upload the final frame");

            AssetLocation paused = new AssetLocation(AssetKind.TEXTURE, AssetPath.parse("strip.png"))
                    .withTextureAnimation(new TextureAnimation(1, null, true));
            TextureAsset unpausedOnly = ClientAssets.acquireTexture(paused);
            ClientAssets.animationTick(true);
            require(unpausedOnly.framePosition() == 0, "Unpaused clock must stop while paused");
            ClientAssets.animationTick(false);
            require(unpausedOnly.framePosition() == 1, "Unpaused clock resumes on an eligible tick");
            unpausedOnly.close();

            AssetLocation reordered = new AssetLocation(AssetKind.TEXTURE, AssetPath.parse("strip.png"))
                    .withTextureAnimation(new TextureAnimation(1, new int[]{3, 1, 2, 2}, false));
            TextureAsset ordered = ClientAssets.acquireTexture(reordered);
            require(ordered.currentFrame().getRGB(0, 0) == 0xff0000ff,
                    "A custom sequence starts at its first listed frame");
            ClientAssets.animationTick(false);
            require(ordered.currentFrame().getRGB(0, 0) == 0xffff0000,
                    "Sequence advances to the next listed frame");
            ordered.close();

            descriptor.set("animation", LuaValue.FALSE);
            TextureAsset firstOnly = ClientAssets.acquireTexture(TextureInputs.read(descriptor));
            require(firstOnly.getFrames().sequenceLength() == 1
                    && firstOnly.currentFrame().getRGB(0, 0) == 0xffff0000,
                    "animation=false selects the first strip frame");
            firstOnly.close();

            boolean rejected = false;
            try {
                ClientAssets.acquireTexture(new AssetLocation(AssetKind.TEXTURE, AssetPath.parse("strip.png"))
                        .withTextureAnimation(new TextureAnimation(1, new int[]{4}, false)));
            } catch (IOException expected) {
                rejected = true;
            }
            require(rejected, "Frame indices beyond the strip must be rejected");

            Path pack = root.resolve("invalid.zip");
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(pack))) {
                output.putNextEntry(new ZipEntry("bm_assets/strip.png"));
                ByteArrayOutputStream invalid = new ByteArrayOutputStream();
                ImageIO.write(new BufferedImage(16, 24, BufferedImage.TYPE_INT_ARGB), "png", invalid);
                output.write(invalid.toByteArray());
                output.closeEntry();
            }
            ClientAssets.useProviders(new FileAssetProvider(root.toFile()), new ZipAssetProvider(pack.toFile()),
                    warnings::add);
            TextureImage selected = ClientAssets.resolveTexture(location).getValue();
            require(selected.getImage().getHeight() == 48 && !warnings.isEmpty(),
                    "Invalid pack strip falls back to script default");

            Files.write(root.resolve("strip.png"), new byte[]{1, 2, 3});
            ClientAssets.useProviders(new FileAssetProvider(root.toFile()), null, warnings::add);
            ClientAssets.refresh();
            require(ImageIO.read(LuaTextureResources.open(standalone)) != null,
                    "Failed refresh retains the previous complete animation");
            LuaTextureResources.release(standalone);
            System.out.println("Animated textures passed: parsing, shared frames, timing, pause, pack fallback, "
                    + "reload.");
        } finally {
            AssetResourceTest.deleteTree(root);
        }
    }

    private static byte[] strip(int size, int... colors) throws Exception {
        BufferedImage image = new BufferedImage(size, size * colors.length, BufferedImage.TYPE_INT_ARGB);
        for (int frame = 0; frame < colors.length; frame++) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    image.setRGB(x, frame * size + y, colors[frame]);
                }
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingBackend implements AtlasTextures.Backend {
        private int allocations;
        private int uploads;
        private int color;

        public int allocate(EnumTexAtlas atlas) {
            allocations++;
            return 200;
        }

        public boolean isReady() {
            return true;
        }

        public boolean isAnaglyph() {
            return false;
        }

        public void upload(EnumTexAtlas atlas, int index, byte[] pixels) {
            uploads++;
            color = 0xff000000 | (pixels[0] & 255) << 16 | (pixels[1] & 255) << 8 | pixels[2] & 255;
        }
    }
}
