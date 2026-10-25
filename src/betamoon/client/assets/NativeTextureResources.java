package betamoon.client.assets;

import betamoon.assets.NativeTextureSource;
import betamoon.assets.AssetDefinition;
import betamoon.assets.AssetId;
import betamoon.assets.AssetKey;
import betamoon.assets.AssetKind;
import betamoon.assets.BuiltinAssets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.src.ModLoader;
import net.minecraft.src.TexturePackBase;
import net.minecraft.src.TexturePackDefault;
import net.minecraft.src.TexturePackCustom;

/** Validates native images through the same selected-pack lookup used by RenderEngine. */
public final class NativeTextureResources {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_PIXELS = 262144;
    private static final Map<String, Selection> IMAGES = new LinkedHashMap<>();
    private static TexturePackBase cachedPack;
    private static TexturePackBase testPack;

    private NativeTextureResources() {
    }

    public static Selection inspect(NativeTextureSource source) throws IOException {
        TexturePackBase pack = selectedPack();
        if (pack != cachedPack) {
            refresh();
            cachedPack = pack;
        }
        Selection selection = IMAGES.get(source.getResource());
        if (selection == null) {
            selection = inspect(source, pack);
            IMAGES.put(source.getResource(), selection);
        }
        try {
            source.validateDimensions(selection.width, selection.height);
        } catch (IllegalArgumentException error) {
            throw new IOException(source.getResource() + ": " + error.getMessage(), error);
        }
        return selection;
    }

    /** Stream acquisition remains polymorphic, including vanilla's missing-entry JAR fallback. */
    public static Selection inspect(NativeTextureSource source, TexturePackBase pack) throws IOException {
        if (pack == null) {
            throw new IOException("No selected Minecraft texture pack");
        }
        String resource = source.getResource();
        try (InputStream input = pack.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Native texture not found: " + resource);
            }
            byte[] bytes = read(input);
            if (bytes.length < 8 || bytes[0] != (byte) 137 || bytes[1] != 80 || bytes[2] != 78 || bytes[3] != 71) {
                throw new IOException("Native texture must be a PNG: " + resource);
            }
            try (ImageInputStream image = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(image);
                if (!readers.hasNext()) {
                    throw new IOException("Cannot decode native texture: " + resource);
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(image);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if ((long) width * height > MAX_PIXELS) {
                        throw new IOException("Native texture exceeds Minecraft's upload limit of " + MAX_PIXELS
                                + " pixels: " + resource);
                    }
                    source.validateDimensions(width, height);
                    reader.read(0);
                    return new Selection(pack.texturePackFileName == null ? "Minecraft" : pack.texturePackFileName,
                            pack instanceof TexturePackDefault ? "jar" : "unknown", width, height);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IllegalArgumentException error) {
            throw new IOException(resource + ": " + error.getMessage(), error);
        } catch (IOException error) {
            throw new IOException("Native texture " + resource + " from selected pack '" + pack.texturePackFileName
                    + "': " + error.getMessage(), error);
        }
    }

    public static void refresh() {
        IMAGES.clear();
    }

    /** Reopen the selected vanilla ZIP before native refresh reads cached resource paths again. */
    public static void reopenSelectedPack(TexturePackBase selected) {
        TexturePackBase pack = testPack == null ? selected : testPack;
        if (pack instanceof TexturePackCustom) {
            pack.closeTexturePackFile();
            pack.func_6482_a();
        }
        refresh();
    }

    public static Selection inspectImage(String resource) throws IOException {
        if (!resource.startsWith("/") || !resource.endsWith(".png")) {
            return null;
        }
        AssetDefinition definition;
        try {
            definition = BuiltinAssets.find(new AssetId(AssetKind.TEXTURE,
                    AssetKey.parse("minecraft:image/" + resource.substring(1, resource.length() - 4))));
        } catch (IllegalArgumentException error) {
            return null;
        }
        return definition == null ? null : inspect(definition.getNativeTexture());
    }

    /** Provider seam for headless integration tests; never exposed to Lua. */
    public static void useTexturePack(TexturePackBase pack) {
        testPack = pack;
        cachedPack = null;
        refresh();
    }

    private static TexturePackBase selectedPack() throws IOException {
        if (testPack != null) {
            return testPack;
        }
        Minecraft minecraft = ModLoader.getMinecraftInstance();
        if (minecraft == null || minecraft.texturePackList == null) {
            throw new IOException("Minecraft texture packs are unavailable outside the client");
        }
        return minecraft.texturePackList.selectedTexturePack;
    }

    private static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count > MAX_BYTES - output.size()) {
                throw new IOException("Native texture exceeds " + MAX_BYTES + " bytes");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    public static final class Selection {
        public final String packName;
        public final String origin;
        public final int width;
        public final int height;

        private Selection(String packName, String origin, int width, int height) {
            this.packName = packName;
            this.origin = origin;
            this.width = width;
            this.height = height;
        }
    }
}
