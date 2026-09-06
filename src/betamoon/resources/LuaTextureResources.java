package betamoon.resources;

import betamoon.io.ImageIo;
import betamoon.io.IoUtils;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.src.ModLoader;
import net.minecraft.src.RenderEngine;
import org.luaj.vm2.LuaError;

/** Provides immutable virtual resource names for standalone textures stored with Lua scripts. */
public final class LuaTextureResources {
    public static final String PREFIX = "/betamoon-lua-texture/";
    private static final Map ENTRIES = new HashMap();
    private static final Map RESOURCES_BY_SIGNATURE = new HashMap();
    private static long nextId;

    private LuaTextureResources() {
    }

    /**
     * Validates and publishes a texture file under a fresh resource name.
     * A fresh name ensures Minecraft does not reuse an image cached before a script reload.
     *
     * @param relativePath path relative to the Lua scripts directory
     * @return virtual resource path understood by the texture-pack hook
     */
    public static synchronized String register(String relativePath) {
        File root = IoUtils.resolveLuaModsDir(LuaTextureResources.class, false);
        if (root == null) {
            throw new LuaError("Armor: Lua scripts directory not found.");
        }
        File file = resolveContainedFile(root, relativePath);
        if (!file.isFile()) {
            throw new LuaError("Armor: model texture file not found: " + file.getAbsolutePath());
        }
        BufferedImage image;
        try {
            image = ImageIo.loadImage(file);
            if (image == null) {
                throw new LuaError("Armor: model texture could not be decoded: " + file.getAbsolutePath());
            }
        } catch (IOException e) {
            throw new LuaError("Armor: failed to read model texture: " + file.getAbsolutePath());
        }
        String signature = file.getAbsolutePath().toLowerCase() + "\n" + file.lastModified() + "\n" + file.length();
        String resource = (String) RESOURCES_BY_SIGNATURE.get(signature);
        if (resource != null) {
            ((Entry) ENTRIES.get(resource)).references++;
            return resource;
        }
        resource = PREFIX + (++nextId) + ".png";
        ENTRIES.put(resource, new Entry(file, signature, image));
        RESOURCES_BY_SIGNATURE.put(signature, resource);
        return resource;
    }

    /** Loads a BetaMoon virtual resource, or returns null for an ordinary Minecraft resource. */
    public static synchronized BufferedImage load(String resourcePath) {
        Entry entry = (Entry) ENTRIES.get(resourcePath);
        if (entry == null) {
            return null;
        }
        return entry.image;
    }

    /** Releases a texture name after an armor item stops using it. */
    public static synchronized void release(String resourcePath) {
        Entry entry = (Entry) ENTRIES.get(resourcePath);
        if (entry == null || --entry.references > 0) {
            return;
        }
        ENTRIES.remove(resourcePath);
        RESOURCES_BY_SIGNATURE.remove(entry.signature);
        removeMinecraftTexture(resourcePath);
    }

    private static void removeMinecraftTexture(String resourcePath) {
        try {
            RenderEngine engine = ModLoader.getMinecraftInstance().renderEngine;
            Field[] fields = RenderEngine.class.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                if (!Map.class.isAssignableFrom(fields[i].getType())) continue;
                fields[i].setAccessible(true);
                Map values = (Map) fields[i].get(engine);
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

    private static File resolveContainedFile(File root, String relativePath) {
        if (relativePath == null || relativePath.trim().length() == 0) {
            throw new LuaError("Armor: modelTexture must be a non-empty path.");
        }
        try {
            File canonicalRoot = root.getCanonicalFile();
            File candidate = new File(canonicalRoot, stripLeadingSeparators(relativePath)).getCanonicalFile();
            String rootPath = canonicalRoot.getPath();
            String candidatePath = candidate.getPath();
            if (!candidatePath.equals(rootPath)
                && !candidatePath.startsWith(rootPath + File.separator)) {
                throw new LuaError("Armor: modelTexture must stay inside the Lua scripts directory.");
            }
            return candidate;
        } catch (IOException e) {
            throw new LuaError("Armor: invalid modelTexture path: " + relativePath);
        }
    }

    private static String stripLeadingSeparators(String value) {
        String result = value.trim();
        while (result.startsWith("/") || result.startsWith("\\")) {
            result = result.substring(1);
        }
        return result;
    }

    private static final class Entry {
        private final File file;
        private final String signature;
        private final BufferedImage image;
        private int references = 1;

        private Entry(File file, String signature, BufferedImage image) {
            this.file = file;
            this.signature = signature;
            this.image = image;
        }
    }
}
