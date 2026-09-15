package betamoon.resources;

import betamoon.client.assets.AssetLocation;
import betamoon.client.assets.ClientAssets;
import betamoon.client.assets.TextureAsset;
import betamoon.client.assets.TextureImage;
import betamoon.luaapi.asset.AssetInputs;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.src.ModLoader;
import net.minecraft.src.RenderEngine;
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
            ENTRIES.put(resource, new Entry(key, texture));
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
        return (entry == null ? MISSING : entry.texture.getContent().getValue()).open();
    }

    public static synchronized BufferedImage load(String resource) {
        Entry entry = ENTRIES.get(resource);
        return entry == null ? null : entry.texture.getContent().getValue().getImage();
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
        entry.texture.close();
        removeMinecraftTexture(resource);
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
        private int references = 1;

        private Entry(String key, TextureAsset texture) {
            this.key = key;
            this.texture = texture;
        }
    }
}
