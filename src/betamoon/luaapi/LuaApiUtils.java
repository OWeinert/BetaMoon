package betamoon.luaapi;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import betamoon.io.ImageIo;
import betamoon.resources.EnumTexAtlas;
import betamoon.scriptloader.LuaScriptRegistry;
import betamoon.io.IoUtils;
import net.minecraft.src.ModLoader;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import betamoon.BetaMoonMain;

public final class LuaApiUtils {
    private static final java.util.logging.Logger LOGGER = BetaMoonMain.LOGGER;
    private static final String[] TEXTURE_FX_METHOD_NAMES = new String[] {
        "registerTextureFX",
        "RegisterTextureFX",
        "a",
        "func_78387_a"
    };
    /**
     * Utility class for extracting typed arguments from Lua varargs.
     */
    private LuaApiUtils() {
    }

    public static void warn(String source, String message) {
        String safeSource = normalize(source, "Lua");
        String safeMessage = normalizePreserveFormatting(message, "Unknown warning");
        String currentScript = LuaScriptRegistry.getCurrentScriptFile();
        String scriptLabel = currentScript == null ? safeSource : currentScript;
        String combined = safeSource + ": " + safeMessage;
        LOGGER.warning("[Lua Warning] " + combined);
        betamoon.scriptloader.LuaScriptErrors.addWarning(scriptLabel, combined);
    }

    public static void warn(String message) {
        warn("Lua", message);
    }

    private static String normalize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String normalizePreserveFormatting(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : value;
    }

    /**
     * Reads a numeric argument, supporting an optional table as the first argument.
     *
     * @param args Lua varargs passed to the API function
     * @param index positional index to read when no leading table is provided
     * @return the numeric value coerced to double
     */
    public static double getNumberArg(Varargs args, int index) {
        int offset = (args.narg() >= 1 && args.arg(1).istable()) ? 1 : 0;
        return args.arg(index + offset).checkdouble();
    }

    /**
     * Reads a string argument, supporting an optional table as the first argument.
     *
     * @param args Lua varargs passed to the API function
     * @param index positional index to read when no leading table is provided
     * @return the string value
     */
    public static String getStringArg(Varargs args, int index) {
        int offset = (args.narg() >= 1 && args.arg(1).istable()) ? 1 : 0;
        return args.arg(index + offset).checkjstring();
    }

    /**
     * Reads a raw argument, skipping a leading table when called via ':'.
     *
     * @param args Lua varargs passed to the API function
     * @param index positional index to read when no leading table is provided
     * @return the raw Lua value at the resolved index
     */
    public static LuaValue getVarArg(Varargs args, int index) {
        int offset = (args.narg() >= 1 && args.arg(1).istable()) ? 1 : 0;
        return args.arg(index + offset);
    }

    /**
     * Reads an ItemStack from a Lua value, accepting numbers or tables.
     *
     * @param value Lua value representing the item stack
     * @param allowCount true to accept count values, false to force count 1
     * @param context error context label
     * @return parsed item stack
     */
    public static ItemStack readItemStack(LuaValue value, boolean allowCount, String context) {
        if (value.isnumber()) {
            int id = value.checkint();
            return new ItemStack(id, 1, 0);
        }
        if (value.istable()) {
            LuaValue idValue = value.get("id");
            int id;
            if (!idValue.isnil()) {
                id = resolveItemId(idValue);
            } else if (!value.get("getId").isnil()) {
                id = resolveItemId(value.get("getId").call(value));
            } else {
                id = resolveItemId(value.get(1));
            }
            int count = 1;
            int damage = 0;
            LuaValue countValue = value.get("count");
            if (!countValue.isnil()) {
                count = countValue.checkint();
            } else if (!value.get(2).isnil()) {
                count = value.get(2).checkint();
            }
            LuaValue damageValue = value.get("damage");
            if (!damageValue.isnil()) {
                damage = damageValue.checkint();
            } else if (!value.get(3).isnil()) {
                damage = value.get(3).checkint();
            }
            if (!allowCount) {
                count = 1;
            }
            return new ItemStack(id, count, damage);
        }
        throw new LuaError("LuaApi: expected " + context + " to be a number or table.");
    }

    public static int resolveItemId(LuaValue value) {
        if (value.isnumber()) {
            return value.checkint();
        }
        if (value.istable()) {
            LuaValue getter = value.get("getId");
            if (!getter.isnil()) {
                return resolveItemId(getter.call(value));
            }
        }
        throw new LuaError("LuaApi: expected item id or handle.");
    }

    /**
     * Registers a texture from the luamods directory on the specified atlas.
     *
     * @param atlas texture atlas to register against
     * @param relativePath path to the texture relative to the luamods directory
     * @return allocated texture index on the atlas
     */
    public static int registerTexture(EnumTexAtlas atlas, String relativePath) {
        File luaModsDir = IoUtils.resolveLuaModsDir(LuaApiUtils.class, false);
        if (luaModsDir == null) {
            throw new LuaError("LuaApi: lua mods directory not found.");
        }
        String trimmed = relativePath;
        while (trimmed.startsWith("/") || trimmed.startsWith("\\")) {
            trimmed = trimmed.substring(1);
        }
        File textureFile = new File(luaModsDir, trimmed);
        if (!textureFile.isFile()) {
            return warnMissingTexture(atlas, "Texture file not found: " + textureFile.getAbsolutePath());
        }
        BufferedImage image;
        try {
            image = ImageIo.loadImage(textureFile);
        } catch (IOException e) {
            return warnMissingTexture(atlas, "Failed to read texture: " + textureFile.getAbsolutePath());
        }
        if (image == null) {
            return warnMissingTexture(atlas, "Texture could not be decoded: " + textureFile.getAbsolutePath());
        }
        int index = ModLoader.getUniqueSpriteIndex(atlas.getAtlasPath());
        Object textureFx = createTextureFx(index, atlas.getAtlasId(), image);
        registerTextureFx(textureFx);
        return index;
    }

    /**
     * Resolves the luamods directory based on the mod jar location.
     *
     * @return the luamods directory or null when it cannot be resolved
     */
    private static Object createTextureFx(int index, int atlasId, BufferedImage image) {
        return new betamoon.resources.BetaMoonTextureStatic(index, atlasId, image);
    }

    private static int warnMissingTexture(EnumTexAtlas atlas, String detail) {
        warn("Texture", detail);
        return getFallbackTextureIndex(atlas);
    }

    private static int getFallbackTextureIndex(EnumTexAtlas atlas) {
        if (atlas == EnumTexAtlas.BLOCKS) {
            return 253;
        }
        return 223;
    }

    private static void registerTextureFx(Object textureFx) {
        try {
            Object renderEngine = ModLoader.getMinecraftInstance().renderEngine;
            if (renderEngine != null) {
                Method method = findTextureFxMethod(renderEngine.getClass(), textureFx);
                if (method != null) {
                    if (!method.isAccessible()) {
                        method.setAccessible(true);
                    }
                    method.invoke(renderEngine, new Object[] { textureFx });
                    return;
                }
            }
            Method modLoaderMethod = findTextureFxMethod(ModLoader.class, textureFx);
            if (modLoaderMethod != null) {
                if (!modLoaderMethod.isAccessible()) {
                    modLoaderMethod.setAccessible(true);
                }
                modLoaderMethod.invoke(null, new Object[] { textureFx });
                return;
            }
        } catch (Exception e) {
            throw new LuaError("LuaApi: registerTextureFX not available.");
        }
        throw new LuaError("LuaApi: registerTextureFX not available.");
    }

    private static Method findTextureFxMethod(Class targetClass, Object textureFx) {
        Method method = findTextureFxMethod(targetClass.getDeclaredMethods(), textureFx);
        if (method != null) {
            return method;
        }
        return findTextureFxMethod(targetClass.getMethods(), textureFx);
    }

    private static Method findTextureFxMethod(Method[] methods, Object textureFx) {
        if (methods == null || textureFx == null) {
            return null;
        }
        Class textureFxClass = textureFx.getClass();
        for (int i = 0; i < TEXTURE_FX_METHOD_NAMES.length; i++) {
            String expected = TEXTURE_FX_METHOD_NAMES[i];
            Method named = findTextureFxMethodByName(methods, textureFxClass, expected);
            if (named != null) {
                return named;
            }
        }
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            String name = method.getName();
            if (name == null) {
                continue;
            }
            String lower = name.toLowerCase();
            if (lower.indexOf("texturefx") == -1) {
                continue;
            }
            if (matchesTextureFxSignature(method, textureFxClass)) {
                return method;
            }
        }
        return null;
    }

    private static Method findTextureFxMethodByName(Method[] methods, Class textureFxClass, String name) {
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (!name.equals(method.getName())) {
                continue;
            }
            if (matchesTextureFxSignature(method, textureFxClass)) {
                return method;
            }
        }
        return null;
    }

    private static boolean matchesTextureFxSignature(Method method, Class textureFxClass) {
        Class[] params = method.getParameterTypes();
        if (params.length != 1) {
            return false;
        }
        Class param = params[0];
        if (param.isAssignableFrom(textureFxClass)) {
            return true;
        }
        return param.getName().endsWith("TextureFX");
    }
}
