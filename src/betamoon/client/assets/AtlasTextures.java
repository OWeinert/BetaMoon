package betamoon.client.assets;

import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.assets.NativeTextureSource;
import betamoon.resources.BetaMoonTextureStatic;
import betamoon.resources.EnumTexAtlas;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.src.ModLoader;
import net.minecraft.src.RenderEngine;
import org.lwjgl.opengl.GL11;

/**
 * Atlas slots survive content reload; pixels upload when their visible frame
 * or display settings change.
 */
public final class AtlasTextures {
    private static final Map<String, Slot> SLOTS = new LinkedHashMap<>();
    private static final Map<String, Deque<Integer>> RETIRED = new LinkedHashMap<>();
    private static Backend backend = new NativeBackend();

    private AtlasTextures() {
    }

    public static int register(EnumTexAtlas atlas, AssetLocation location) throws IOException {
        NativeTextureSource source = location.getNativeTexture();
        if (source != null) {
            if (!source.isCell() || !source.getResource().equals(atlas.getAtlasPath())) {
                throw new IOException("This texture field requires a cell from " + atlas.getAtlasPath()
                        + "; got " + location.getCacheKey());
            }
            return source.getIndex();
        }
        ClientAssets.requestRefresh();
        String key = atlas.getAtlasPath() + "\n" + location.getCacheKey();
        Slot existing = SLOTS.get(key);
        if (existing != null) {
            existing.references++;
            ScriptResourceTracker.track(() -> release(key, existing));
            return existing.index;
        }
        TextureAsset texture = ClientAssets.acquireTexture(location);
        try {
            Deque<Integer> retired = RETIRED.get(atlas.getAtlasPath());
            int index = retired == null || retired.isEmpty() ? backend.allocate(atlas) : retired.removeFirst();
            Slot slot = new Slot(atlas, index, texture);
            texture.listen(slot.listener);
            SLOTS.put(key, slot);
            ScriptResourceTracker.track(() -> release(key, slot));
            return index;
        } catch (RuntimeException error) {
            texture.close();
            throw error;
        }
    }

    private static void release(String key, Slot slot) {
        if (--slot.references > 0 || SLOTS.get(key) != slot) {
            return;
        }
        SLOTS.remove(key);
        slot.texture.unlisten(slot.listener);
        slot.texture.close();
        RETIRED.computeIfAbsent(slot.atlas.getAtlasPath(), unused -> new ArrayDeque<>()).addLast(slot.index);
    }

    public static void refresh() {
        for (Slot slot : SLOTS.values()) {
            slot.dirty = true;
        }
        uploadChanged();
    }

    public static void uploadChanged() {
        if (SLOTS.isEmpty() || !backend.isReady()) {
            return;
        }
        boolean anaglyph = backend.isAnaglyph();
        for (Slot slot : SLOTS.values()) {
            if (slot.dirty || slot.pixels.anaglyphEnabled != anaglyph) {
                slot.pixels.anaglyphEnabled = anaglyph;
                slot.pixels.onTick();
                backend.uploadImage(slot.atlas, slot.index, slot.texture.currentFrame(), anaglyph,
                        slot.pixels.imageData);
                slot.dirty = false;
            }
        }
    }

    /**
     * Rendering boundary, also used to verify upload lifetime without an OpenGL
     * context.
     */
    public interface Backend {
        int allocate(EnumTexAtlas atlas);

        boolean isReady();

        boolean isAnaglyph();

        void upload(EnumTexAtlas atlas, int index, byte[] pixels);

        default void uploadImage(EnumTexAtlas atlas, int index, BufferedImage image, boolean anaglyph,
                byte[] fallbackPixels) {
            upload(atlas, index, fallbackPixels);
        }
    }

    public static void useBackend(Backend replacement) {
        if (!SLOTS.isEmpty()) {
            throw new IllegalStateException("Atlas backend must be selected before registering slots");
        }
        RETIRED.clear();
        backend = replacement;
    }

    private static final class Slot {
        private final EnumTexAtlas atlas;
        private final int index;
        private final TextureAsset texture;
        private final BetaMoonTextureStatic pixels;
        private final Consumer<TextureImage> listener;
        private int references = 1;
        private boolean dirty = true;

        private Slot(EnumTexAtlas atlas, int index, TextureAsset texture) {
            this.atlas = atlas;
            this.index = index;
            this.texture = texture;
            pixels = new BetaMoonTextureStatic(index, atlas.getAtlasId(), texture.currentFrame());
            listener = image -> {
                pixels.setImage(texture.currentFrame());
                dirty = true;
            };
        }
    }

    private static final class NativeBackend implements Backend {
        public int allocate(EnumTexAtlas atlas) {
            return ModLoader.getUniqueSpriteIndex(atlas.getAtlasPath());
        }

        public boolean isReady() {
            return ModLoader.getMinecraftInstance() != null && ModLoader.getMinecraftInstance().renderEngine != null;
        }

        public boolean isAnaglyph() {
            return ModLoader.getMinecraftInstance().gameSettings.anaglyph;
        }

        public void upload(EnumTexAtlas atlas, int index, byte[] pixels) {
            throw new UnsupportedOperationException("Native atlas upload requires the source image");
        }

        @Override
        public void uploadImage(EnumTexAtlas atlas, int index, BufferedImage image, boolean anaglyph,
                byte[] fallbackPixels) {
            RenderEngine engine = ModLoader.getMinecraftInstance().renderEngine;
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try {
                engine.bindTexture(engine.getTexture(atlas.getAtlasPath()));
                int atlasWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int atlasHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                if (atlasWidth % 16 != 0 || atlasHeight % 16 != 0 || atlasWidth != atlasHeight) {
                    throw new IllegalStateException("Block/item atlas must have 16 square cells per side");
                }
                int cell = atlasWidth / 16;
                TexturePixelUpload.upload(image, index % 16 * cell, index / 16 * cell, cell, cell, anaglyph,
                        RenderEngine.useMipmaps);
            } finally {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            }
        }
    }
}
