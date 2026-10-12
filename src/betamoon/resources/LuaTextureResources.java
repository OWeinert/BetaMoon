package betamoon.resources;

import betamoon.client.assets.AssetLocation;
import betamoon.client.assets.ClientAssets;
import betamoon.client.assets.TextureAsset;
import betamoon.client.assets.TextureImage;
import betamoon.client.assets.TexturePixelUpload;
import betamoon.luaapi.asset.AssetInputs;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.src.ModLoader;
import net.minecraft.src.RenderEngine;
import org.lwjgl.opengl.GL11;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/**
 * Virtual PNG resources consumed by Minecraft's ordinary texture cache and
 * refresh path.
 */
public final class LuaTextureResources {
    public static final String PREFIX = "/betamoon-lua-texture/";
    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static final Map<String, String> RESOURCES_BY_KEY = new HashMap<>();
    private static final TextureImage MISSING = TextureImage.missing();
    private static long nextId;

    private LuaTextureResources() {
    }

    public static String register(String path) {
        return register(AssetInputs.texture(LuaValue.valueOf(path)));
    }

    public static synchronized String register(AssetLocation location) {
        ClientAssets.requestRefresh();
        String key = location.getCacheKey();
        String resource = RESOURCES_BY_KEY.get(key);
        if (resource != null) {
            ENTRIES.get(resource).references++;
            return resource;
        }
        try {
            TextureAsset texture = ClientAssets.acquireTexture(location);
            resource = PREFIX + (++nextId) + ".png";
            Entry entry = new Entry(key, texture);
            texture.listen(entry.listener);
            ENTRIES.put(resource, entry);
            RESOURCES_BY_KEY.put(key, resource);
            return resource;
        } catch (IOException error) {
            throw new LuaError("Texture: " + error.getMessage());
        }
    }

    public static synchronized InputStream open(String resource) {
        if (!resource.startsWith(PREFIX)) {
            return null;
        }
        Entry entry = ENTRIES.get(resource);
        if (entry == null) {
            return MISSING.open();
        }
        try {
            return entry.texture.getFrames().open(entry.texture.framePosition());
        } catch (IOException error) {
            throw new IllegalStateException("Cannot encode texture frame " + resource, error);
        }
    }

    public static synchronized BufferedImage load(String resource) {
        Entry entry = ENTRIES.get(resource);
        return entry == null ? null : entry.texture.currentFrame();
    }

    public static synchronized int[] dimensions(String resource) {
        BufferedImage image = load(resource);
        return image == null ? null : new int[]{image.getWidth(), image.getHeight()};
    }

    public static synchronized void release(String resource) {
        Entry entry = ENTRIES.get(resource);
        if (entry == null || --entry.references > 0) {
            return;
        }
        ENTRIES.remove(resource);
        RESOURCES_BY_KEY.remove(entry.key);
        entry.texture.unlisten(entry.listener);
        entry.texture.close();
        removeMinecraftTexture(resource);
    }

    /** Upload changed standalone frames to Minecraft's stable cached texture IDs. */
    public static synchronized void uploadChanged() {
        if (ModLoader.getMinecraftInstance() == null || ModLoader.getMinecraftInstance().renderEngine == null) {
            return;
        }
        RenderEngine engine = ModLoader.getMinecraftInstance().renderEngine;
        boolean anaglyph = ModLoader.getMinecraftInstance().gameSettings.anaglyph;
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            for (Map.Entry<String, Entry> binding : ENTRIES.entrySet()) {
                Entry entry = binding.getValue();
                if (entry.texture.getFrames().animation() == null || !entry.dirty && entry.anaglyph == anaglyph) {
                    continue;
                }
                int textureId = engine.getTexture(binding.getKey());
                engine.bindTexture(textureId);
                BufferedImage frame = entry.texture.currentFrame();
                int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                if (width != frame.getWidth() || height != frame.getHeight()) {
                    engine.setupTexture(frame, textureId);
                } else {
                    TexturePixelUpload.upload(frame, 0, 0, width, height, anaglyph, RenderEngine.useMipmaps);
                }
                entry.dirty = false;
                entry.anaglyph = anaglyph;
            }
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        }
    }

    private static void removeMinecraftTexture(String resourcePath) {
        try {
            RenderEngine engine = ModLoader.getMinecraftInstance().renderEngine;
            Field[] fields = RenderEngine.class.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                if (!Map.class.isAssignableFrom(fields[i].getType())) {
                    continue;
                }
                fields[i].setAccessible(true);
                Map<?, ?> values = (Map<?, ?>) fields[i].get(engine);
                Object textureId = values == null ? null : values.remove(resourcePath);
                if (textureId instanceof Integer) {
                    engine.deleteTexture(((Integer) textureId).intValue());
                    return;
                }
            }
        } catch (Throwable ignored) {
            // A missed cleanup only delays reclamation until Minecraft exits.
        }
    }

    private static final class Entry {
        private final String key;
        private final TextureAsset texture;
        private final Consumer<TextureImage> listener = image -> dirty = true;
        private int references = 1;
        private boolean dirty = true;
        private boolean anaglyph;

        private Entry(String key, TextureAsset texture) {
            this.key = key;
            this.texture = texture;
        }
    }
}
