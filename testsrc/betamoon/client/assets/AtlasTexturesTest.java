package betamoon.client.assets;

import betamoon.assets.AssetKind;
import betamoon.assets.AssetPath;
import betamoon.assets.io.FileAssetProvider;
import betamoon.resources.EnumTexAtlas;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Static atlas pixels must not enter vanilla's per-tick texture animation
 * upload loop.
 */
public final class AtlasTexturesTest {
    private AtlasTexturesTest() {
    }

    public static void main(String[] arguments) throws Exception {
        Path root = Files.createTempDirectory("betamoon-atlas-test-");
        try {
            Files.write(root.resolve("tile.png"), AssetResourceTest.png(0xffff0000, 32));
            ClientAssets.useProviders(new FileAssetProvider(root.toFile()), null, message -> {
            });
            RecordingBackend backend = new RecordingBackend();
            AtlasTextures.useBackend(backend);
            AssetLocation location = new AssetLocation(AssetKind.TEXTURE, AssetPath.parse("tile.png"));
            int block = AtlasTextures.register(EnumTexAtlas.BLOCKS, location);
            require(AtlasTextures.register(EnumTexAtlas.BLOCKS, location) == block && backend.allocations == 1,
                    "Repeated declarations must retain their atlas slot");
            AtlasTextures.register(EnumTexAtlas.ITEMS, location);
            AtlasTextures.uploadChanged();
            require(backend.uploads == 2 && backend.red == 255, "Both atlases receive scaled pixels");
            for (int i = 0; i < 20; i++) {
                AtlasTextures.uploadChanged();
            }
            require(backend.uploads == 2, "Unchanged static textures must not upload every frame/tick");
            Files.write(root.resolve("tile.png"), AssetResourceTest.png(0xff0000ff, 16));
            ClientAssets.refresh();
            AtlasTextures.uploadChanged();
            require(backend.uploads == 4 && backend.blue == 255, "Refresh updates existing slots once");
            backend.anaglyph = true;
            AtlasTextures.uploadChanged();
            require(backend.uploads == 6, "Display setting changes update pixels");
            AtlasTextures.refresh();
            require(backend.uploads == 8 && backend.allocations == 2,
                    "Native atlas replacement reuploads without allocating new slots");
            System.out
                    .println("Atlas textures passed: stable slots, scaling, dirty uploads and display/atlas refresh.");
        } finally {
            AssetResourceTest.deleteTree(root);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingBackend implements AtlasTextures.Backend {
        private int allocations;
        private int uploads;
        private int red;
        private int blue;
        private boolean anaglyph;
        public int allocate(EnumTexAtlas atlas) {
            allocations++;
            return 200;
        }

        public boolean isReady() {
            return true;
        }

        public boolean isAnaglyph() {
            return anaglyph;
        }

        public void upload(EnumTexAtlas atlas, int index, byte[] pixels) {
            require(index == 200 && pixels.length == 1024, "Upload uses one legacy atlas tile");
            uploads++;
            red = pixels[0] & 255;
            blue = pixels[2] & 255;
        }
    }
}
