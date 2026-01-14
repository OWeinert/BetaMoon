package betamoon.luaapi;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.minecraft.src.ModLoader;
import net.minecraft.src.ModTextureStatic;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import betamoon.BetaMoonConstants;

final class LuaApiUtils {
    /**
     * Utility class for extracting typed arguments from Lua varargs.
     */
    private LuaApiUtils() {
    }

    /**
     * Reads a numeric argument, supporting an optional table as the first argument.
     *
     * @param args Lua varargs passed to the API function
     * @param index positional index to read when no leading table is provided
     * @return the numeric value coerced to double
     */
    static double getNumberArg(Varargs args, int index) {
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
    static String getStringArg(Varargs args, int index) {
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
    static LuaValue getVarArg(Varargs args, int index) {
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
    static ItemStack readItemStack(LuaValue value, boolean allowCount, String context) {
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

    static int resolveItemId(LuaValue value) {
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
    static int registerTexture(EnumTexAtlas atlas, String relativePath) {
        File luaModsDir = resolveLuaModsDir();
        if (luaModsDir == null) {
            throw new LuaError("LuaApi: lua mods directory not found.");
        }
        String trimmed = relativePath;
        while (trimmed.startsWith("/") || trimmed.startsWith("\\")) {
            trimmed = trimmed.substring(1);
        }
        File textureFile = new File(luaModsDir, trimmed);
        if (!textureFile.isFile()) {
            throw new LuaError("LuaApi: texture file not found: " + textureFile.getAbsolutePath());
        }
        BufferedImage image;
        try {
            image = ImageIO.read(textureFile);
        } catch (IOException e) {
            throw new LuaError("LuaApi: failed to read texture: " + textureFile.getAbsolutePath());
        }
        if (image == null) {
            throw new LuaError("LuaApi: texture could not be decoded: " + textureFile.getAbsolutePath());
        }
        int index = ModLoader.getUniqueSpriteIndex(atlas.getAtlasPath());
        ModLoader.getMinecraftInstance().renderEngine.registerTextureFX(
            new ModTextureStatic(index, atlas.getAtlasId(), image));
        return index;
    }

    /**
     * Resolves the luamods directory based on the mod jar location.
     *
     * @return the luamods directory or null when it cannot be resolved
     */
    private static File resolveLuaModsDir() {
        try {
            File modLocation = new File(LuaApiUtils.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File modsDir = modLocation.getParentFile();
            if (modsDir == null) {
                return null;
            }
            File minecraftDir = modsDir.getParentFile();
            if (minecraftDir == null) {
                return null;
            }
            return new File(minecraftDir, BetaMoonConstants.LUA_SCRIPTS_DIR);
        } catch (Exception e) {
            return null;
        }
    }
}
