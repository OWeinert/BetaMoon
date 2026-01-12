package betamoon.luaapi;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.minecraft.src.ModLoader;
import net.minecraft.src.ModTextureStatic;
import org.luaj.vm2.LuaError;
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
        if (args.narg() >= 2 && args.arg(1).istable()) {
            return args.arg(2).checkdouble();
        }
        return args.arg(index).checkdouble();
    }

    /**
     * Reads a string argument, supporting an optional table as the first argument.
     *
     * @param args Lua varargs passed to the API function
     * @param index positional index to read when no leading table is provided
     * @return the string value
     */
    static String getStringArg(Varargs args, int index) {
        if (args.narg() >= 2 && args.arg(1).istable()) {
            return args.arg(2).checkjstring();
        }
        return args.arg(index).checkjstring();
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
            throw new LuaError("Lua mods directory not found.");
        }
        String trimmed = relativePath;
        while (trimmed.startsWith("/") || trimmed.startsWith("\\")) {
            trimmed = trimmed.substring(1);
        }
        File textureFile = new File(luaModsDir, trimmed);
        if (!textureFile.isFile()) {
            throw new LuaError("Texture file not found: " + textureFile.getAbsolutePath());
        }
        BufferedImage image;
        try {
            image = ImageIO.read(textureFile);
        } catch (IOException e) {
            throw new LuaError("Failed to read texture: " + textureFile.getAbsolutePath());
        }
        if (image == null) {
            throw new LuaError("Texture could not be decoded: " + textureFile.getAbsolutePath());
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
