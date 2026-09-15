package betamoon.luaapi.asset;

import betamoon.assets.AssetDefinition;
import betamoon.assets.AssetId;
import betamoon.assets.AssetKey;
import betamoon.assets.AssetKind;
import betamoon.assets.AssetPath;
import betamoon.client.assets.AssetLocation;
import betamoon.luamodloader.ScriptAssetScope;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/**
 * Parses Lua references without allocating textures, voices, or native content.
 */
public final class AssetInputs {
    private AssetInputs() {
    }

    public static AssetLocation texture(LuaValue value) {
        return read(value, AssetKind.TEXTURE);
    }

    public static AssetLocation sound(LuaValue value) {
        return read(value, AssetKind.SOUND);
    }

    public static AssetLocation read(LuaValue value, AssetKind kind) {
        try {
            AssetId id;
            if (value instanceof AssetReference) {
                id = ((AssetReference) value).getId();
                if (id.getKind() != kind) {
                    throw new LuaError("Expected a " + kind.getDirectory() + " asset reference, got " + id);
                }
            } else if (value.type() == LuaValue.TSTRING) {
                String text = value.checkjstring();
                if (text.indexOf(':') < 0) {
                    return new AssetLocation(kind, AssetPath.parse(text));
                }
                id = new AssetId(kind, AssetKey.parse(text));
            } else {
                throw new LuaError("Asset must be a registered reference, namespaced key, or relative file path");
            }
            AssetDefinition definition = ScriptAssetScope.findVisible(id);
            if (definition == null) {
                throw new LuaError("Asset not registered: " + id);
            }
            return new AssetLocation(definition);
        } catch (IllegalArgumentException error) {
            throw new LuaError("Asset: " + error.getMessage());
        }
    }
}
