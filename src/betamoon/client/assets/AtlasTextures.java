package betamoon.client.assets;

import betamoon.resources.BetaMoonTextureStatic;
import betamoon.resources.EnumTexAtlas;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.src.GLAllocation;
import net.minecraft.src.ModLoader;
import net.minecraft.src.RenderEngine;
import org.lwjgl.opengl.GL11;

/**
 * Static atlas slots survive content reload; pixels upload only when their
 * atlas or image changes.
 */
public final class AtlasTextures {
    private static final Map<String, Slot> SLOTS = new LinkedHashMap<>();
    private static Backend backend = new NativeBackend();

    private AtlasTextures() {
    }

    public static int register(EnumTexAtlas atlas, AssetLocation location) throws IOException {
        ClientAssets.requestRefresh();
        String key = atlas.getAtlasPath() + "\n" + location.getCacheKey();
        Slot existing = SLOTS.get(key);
        if (existing != null) {
            return existing.index;
        }
        TextureAsset texture = ClientAssets.acquireTexture(location);
        try {
            int index = backend.allocate(atlas);
            Slot slot = new Slot(atlas, index, texture);
            texture.listen(image -> {
                slot.pixels.setImage(image.getImage());
                slot.dirty = true;
            });
            SLOTS.put(key, slot);
            return index;
        } catch (RuntimeException error) {
            texture.close();
            throw error;
        }
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
                backend.upload(slot.atlas, slot.index, slot.pixels.imageData);
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
    }

    public static void useBackend(Backend replacement) {
        if (!SLOTS.isEmpty()) {
            throw new IllegalStateException("Atlas backend must be selected before registering slots");
        }
        backend = replacement;
    }

    private static final class Slot {
        private final EnumTexAtlas atlas;
        private final int index;
        private final TextureAsset texture;
        private final BetaMoonTextureStatic pixels;
        private boolean dirty = true;

        private Slot(EnumTexAtlas atlas, int index, TextureAsset texture) {
            this.atlas = atlas;
            this.index = index;
            this.texture = texture;
            pixels = new BetaMoonTextureStatic(index, atlas.getAtlasId(), texture.getContent().getValue().getImage());
        }
    }

    private static final class NativeBackend implements Backend {
        private final ByteBuffer buffer = GLAllocation.createDirectByteBuffer(1024);

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
            RenderEngine engine = ModLoader.getMinecraftInstance().renderEngine;
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try {
                engine.bindTexture(engine.getTexture(atlas.getAtlasPath()));
                buffer.clear();
                buffer.put(pixels, 0, 1024);
                buffer.flip();
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, index % 16 * 16, index / 16 * 16, 16, 16, GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE, buffer);
            } finally {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            }
        }
    }
}
