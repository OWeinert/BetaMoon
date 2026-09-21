package betamoon.luaapi;

import betamoon.BetaMoonCommon;

import betamoon.client.assets.AssetLocation;
import betamoon.client.assets.AtlasTextures;
import betamoon.luaapi.asset.AssetInputs;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.resources.EnumTexAtlas;
import java.io.IOException;
import java.util.logging.Logger;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public final class LuaApiUtils {
    private static final Logger LOGGER = BetaMoonCommon.LOGGER;
    /**
     * Utility class for extracting typed arguments from Lua varargs.
     */
    private LuaApiUtils() {
    }

    public static void warn(String source, String message) {
        warnForScript(LuaScriptRegistry.getCurrentScriptFile(), source, message);
    }

    /** Records a warning against a previously captured script owner. */
    public static void warnForScript(String script, String source, String message) {
        String safeSource = normalize(source, "Lua");
        String safeMessage = normalizePreserveFormatting(message, "Unknown warning");
        String scriptLabel = script == null ? safeSource : script;
        String combined = safeSource + ": " + safeMessage;
        LOGGER.warning("[Lua Warning] " + combined);
        LuaScriptErrors.addWarning(scriptLabel, combined);
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
     * @param args
     *            Lua varargs passed to the API function
     * @param index
     *            positional index to read when no leading table is provided
     * @return the numeric value coerced to double
     */
    public static double getNumberArg(Varargs args, int index) {
        return getVarArg(args, index).checkdouble();
    }

    /**
     * Reads a string argument, supporting an optional table as the first argument.
     *
     * @param args
     *            Lua varargs passed to the API function
     * @param index
     *            positional index to read when no leading table is provided
     * @return the string value
     */
    public static String getStringArg(Varargs args, int index) {
        return getVarArg(args, index).checkjstring();
    }

    /**
     * Reads a raw argument, skipping a leading table when called via ':'.
     *
     * @param args
     *            Lua varargs passed to the API function
     * @param index
     *            positional index to read when no leading table is provided
     * @return the raw Lua value at the resolved index
     */
    public static LuaValue getVarArg(Varargs args, int index) {
        int offset = (args.narg() >= 1 && args.arg(1).istable()) ? 1 : 0;
        return args.arg(index + offset);
    }

    /**
     * Reads an ItemStack from a Lua value, accepting numbers or tables.
     *
     * @param value
     *            Lua value representing the item stack
     * @param allowCount
     *            true to accept count values, false to force count 1
     * @param context
     *            error context label
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
     * @param atlas
     *            texture atlas to register against
     * @param relativePath
     *            path to the texture relative to the luamods directory
     * @return allocated texture index on the atlas
     */
    public static int registerTexture(EnumTexAtlas atlas, String relativePath) {
        return registerTexture(atlas, AssetInputs.texture(LuaValue.valueOf(relativePath)));
    }

    public static int registerTexture(EnumTexAtlas atlas, AssetLocation location) {
        try {
            return AtlasTextures.register(atlas, location);
        } catch (IOException error) {
            return warnMissingTexture(atlas, error.getMessage());
        }
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

}
