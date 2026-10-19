package betamoon.gui.texture;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.src.TexturePackBase;

/** Standalone GUI artwork, loaded through Minecraft's texture cache and refresh path. */
public final class BuiltinGuiSprites {
    private static final String PREFIX = "/betamoon-gui-sprites/";
    private static final int ATLAS_SIZE = 256;
    private static final Map<String, Sprite> SPRITES = sprites();

    private BuiltinGuiSprites() {
    }

    public static Sprite find(String name) {
        return SPRITES.get(name);
    }

    /** Returns null for ordinary resources so the existing resource providers retain their handling. */
    public static InputStream open(TexturePackBase pack, String resource) throws IOException {
        if (!resource.startsWith(PREFIX) || !resource.endsWith(".png")) {
            return null;
        }
        String name = "minecraft:" + resource.substring(PREFIX.length(), resource.length() - 4);
        Sprite sprite = find(name);
        if (sprite == null) {
            return null;
        }
        BufferedImage atlas;
        try (InputStream input = pack.getResourceAsStream(sprite.atlas)) {
            atlas = input == null ? null : ImageIO.read(input);
        }
        if (atlas == null) {
            throw new IOException("Cannot read GUI sprite source: " + sprite.atlas);
        }
        BufferedImage image = extract(atlas, sprite);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", encoded)) {
            throw new IOException("Cannot encode built-in GUI sprite: " + name);
        }
        return new ByteArrayInputStream(encoded.toByteArray());
    }

    private static BufferedImage extract(BufferedImage atlas, Sprite sprite) throws IOException {
        int x = sprite.u * atlas.getWidth() / ATLAS_SIZE;
        int y = sprite.v * atlas.getHeight() / ATLAS_SIZE;
        int width = (sprite.u + sprite.width) * atlas.getWidth() / ATLAS_SIZE - x;
        int height = (sprite.v + sprite.height) * atlas.getHeight() / ATLAS_SIZE - y;
        if (width <= 0 || height <= 0) {
            throw new IOException("GUI sprite source is too small: " + sprite.atlas);
        }
        int[] pixels = atlas.getRGB(x, y, width, height, null, 0, width);
        boolean hasTransparency = false;
        for (int pixel : pixels) {
            if ((pixel >>> 24) != 255) {
                hasTransparency = true;
                break;
            }
        }
        // Vanilla bakes the flat panel color into these sprites. Alpha-authored texture packs already
        // distinguish their artwork from padding; preserve those pixels and their partial alpha exactly.
        if (!hasTransparency) {
            int panel = atlas.getRGB(10 * atlas.getWidth() / ATLAS_SIZE, 10 * atlas.getHeight() / ATLAS_SIZE);
            if ((panel >>> 24) == 255) {
                for (int index = 0; index < pixels.length; index++) {
                    if (pixels[index] == panel) {
                        pixels[index] &= 0x00FFFFFF;
                    }
                }
            }
        }
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, width, height, pixels, 0, width);
        return result;
    }

    private static Map<String, Sprite> sprites() {
        Map<String, Sprite> result = new LinkedHashMap<>();
        add(result, "furnace_flame", "/gui/furnace.png", 176, 0, 14, 14);
        add(result, "furnace_flame_background", "/gui/furnace.png", 56, 36, 14, 14);
        add(result, "furnace_arrow", "/gui/furnace.png", 176, 14, 24, 16);
        add(result, "furnace_arrow_background", "/gui/furnace.png", 79, 34, 24, 16);
        add(result, "crafting_arrow", "/gui/crafting.png", 89, 34, 24, 17);
        return Collections.unmodifiableMap(result);
    }

    private static void add(Map<String, Sprite> sprites, String name, String atlas, int u, int v, int width, int height) {
        sprites.put("minecraft:" + name, new Sprite(PREFIX + name + ".png", atlas, u, v, width, height));
    }

    public static final class Sprite {
        public final String resource;
        public final int width;
        public final int height;
        private final String atlas;
        private final int u;
        private final int v;

        private Sprite(String resource, String atlas, int u, int v, int width, int height) {
            this.resource = resource;
            this.atlas = atlas;
            this.u = u;
            this.v = v;
            this.width = width;
            this.height = height;
        }
    }
}
