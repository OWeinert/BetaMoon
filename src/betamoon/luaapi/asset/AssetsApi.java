package betamoon.luaapi.asset;

import betamoon.assets.AssetDefinition;
import betamoon.assets.AssetId;
import betamoon.assets.AssetKey;
import betamoon.assets.AssetKind;
import betamoon.assets.BuiltinAssets;
import betamoon.client.assets.AssetLocation;
import betamoon.client.assets.ClientModelAssets;
import betamoon.client.assets.ClientAssets;
import betamoon.client.assets.TextureAsset;
import betamoon.client.audio.ClientSounds;
import betamoon.client.audio.SoundAsset;
import betamoon.luamodloader.ScriptAssetScope;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.IOException;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/**
 * User-facing asset declarations; parsing, script ownership and client loading
 * stay separate.
 */
public final class AssetsApi {
    private AssetsApi() {
    }

    public static void attach(LuaTable module) {
        LuaTable assets = new LuaTable();
        assets.set("textures", registry(AssetKind.TEXTURE));
        assets.set("sounds", registry(AssetKind.SOUND));
        assets.set("models", registry(AssetKind.MODEL));
        assets.set("animations", registry(AssetKind.ANIMATION));
        assets.set("refresh", new ZeroArgFunction() {
            public LuaValue call() {
                ClientAssets.requestRefresh();
                return NIL;
            }
        });
        module.set("assets", assets);
    }

    private static LuaTable registry(AssetKind kind) {
        LuaTable registry = new LuaTable();
        registry.set("add", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                AssetDefinition definition = AssetDeclaration.read(kind, args.arg(args.arg1() == registry ? 2 : 1));
                try {
                    ScriptAssetScope.stage(definition);
                    AssetLocation location = new AssetLocation(definition);
                    if (kind == AssetKind.TEXTURE) {
                        TextureAsset texture = ClientAssets.acquireTexture(location);
                        ScriptResourceTracker.track(texture::close);
                    } else if (kind == AssetKind.MODEL) {
                        ScriptResourceTracker.track(ClientModelAssets.model(location)::close);
                    } else if (kind == AssetKind.ANIMATION) {
                        ScriptResourceTracker.track(ClientModelAssets.animations(location)::close);
                    } else {
                        SoundAsset sound = ClientSounds.acquire(location);
                        ScriptResourceTracker.track(sound::close);
                    }
                    ClientAssets.requestRefresh();
                    return new AssetReference(definition.getId());
                } catch (IOException | IllegalArgumentException | IllegalStateException error) {
                    ScriptAssetScope.discardPending(definition);
                    throw new LuaError("Asset: " + error.getMessage());
                }
            }
        });
        registry.set("get", lookup(registry, kind, false));
        registry.set("getRequired", lookup(registry, kind, true));
        return registry;
    }

    private static VarArgFunction lookup(LuaTable registry, AssetKind kind, boolean required) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                try {
                    AssetId id = new AssetId(kind,
                            AssetKey.parse(args.arg(args.arg1() == registry ? 2 : 1).checkjstring()));
                    if (ScriptAssetScope.findVisible(id) == null && BuiltinAssets.find(id) == null) {
                        if (required) {
                            throw new LuaError("Asset not registered: " + id);
                        }
                        return NIL;
                    }
                    return new AssetReference(id);
                } catch (IllegalArgumentException error) {
                    throw new LuaError("Asset: " + error.getMessage());
                }
            }
        };
    }
}
