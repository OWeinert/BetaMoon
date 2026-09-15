package betamoon.luaapi.asset;

import betamoon.assets.AssetDefinition;
import betamoon.assets.AssetId;
import betamoon.assets.AssetKind;
import betamoon.assets.io.ResolvedAsset;
import betamoon.client.assets.AssetLocation;
import betamoon.client.assets.ClientAssets;
import betamoon.client.assets.TextureAsset;
import betamoon.client.audio.ClientSounds;
import betamoon.client.audio.SoundAsset;
import betamoon.luamodloader.ScriptAssetScope;
import java.io.IOException;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

/**
 * Logical reference; its private typed ID is unaffected by user edits to Lua
 * table fields.
 */
public final class AssetReference extends LuaTable {
    private final AssetId id;

    AssetReference(AssetId id) {
        this.id = id;
        set("getKey", new ZeroArgFunction() {
            public LuaValue call() {
                return valueOf(id.getKey().toString());
            }
        });
        set("getKind", new ZeroArgFunction() {
            public LuaValue call() {
                return valueOf(id.getKind().getDirectory());
            }
        });
        set("getPath", new ZeroArgFunction() {
            public LuaValue call() {
                return valueOf(definition().getFallbackPath().toString());
            }
        });
        set("getOverridePath", new ZeroArgFunction() {
            public LuaValue call() {
                return valueOf(definition().getOverridePath().toString());
            }
        });
        set("getSource", new ZeroArgFunction() {
            public LuaValue call() {
                return source();
            }
        });
    }

    public AssetId getId() {
        return id;
    }

    private AssetDefinition definition() {
        AssetDefinition definition = ScriptAssetScope.findVisible(id);
        if (definition == null) {
            throw new LuaError("Asset is no longer registered: " + id);
        }
        return definition;
    }

    private LuaValue source() {
        AssetLocation location = new AssetLocation(definition());
        try {
            if (id.getKind() == AssetKind.TEXTURE) {
                try (TextureAsset texture = ClientAssets.acquireTexture(location)) {
                    return describe(texture.getContent());
                }
            }
            try (SoundAsset sound = ClientSounds.acquire(location)) {
                return describe(sound.getContent());
            }
        } catch (IOException error) {
            throw new LuaError("Asset: " + error.getMessage());
        }
    }

    private static LuaTable describe(ResolvedAsset<?> content) {
        LuaTable result = new LuaTable();
        result.set("kind", content.getSourceKind());
        result.set("name", content.getSourceName());
        result.set("path", content.getSourcePath().toString());
        return result;
    }
}
