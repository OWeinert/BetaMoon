package betamoon.luaapi.asset;

import betamoon.assets.AssetDefinition;
import betamoon.assets.AssetId;
import betamoon.assets.AssetKind;
import betamoon.assets.BuiltinAssets;
import betamoon.assets.io.ResolvedAsset;
import betamoon.client.assets.AssetLocation;
import betamoon.client.assets.ClientModelAssets;
import betamoon.client.assets.ModelAsset;
import betamoon.assets.model.ModelGeometry;
import betamoon.assets.model.ModelAnimations;
import betamoon.assets.model.ModelPose;
import betamoon.client.assets.ClientAssets;
import betamoon.client.assets.TextureAsset;
import betamoon.client.audio.ClientSounds;
import betamoon.client.audio.SoundAsset;
import betamoon.luamodloader.ScriptAssetScope;
import java.io.IOException;
import java.util.Map;
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
        attachModelOperations();
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

    private void attachModelOperations() {
        if (id.getKind() == AssetKind.MODEL) {
            set("createPose", new ZeroArgFunction() {
                public LuaValue call() {
                    try (ModelAsset<ModelGeometry> model = ClientModelAssets.model(new AssetLocation(definition()))) {
                        return new PoseReference(new ModelPose(model.getContent().getValue()));
                    } catch (IOException error) {
                        throw new LuaError("Model: " + error.getMessage());
                    }
                }
            });
        } else if (id.getKind() == AssetKind.ANIMATION) {
            set("getClips", new ZeroArgFunction() {
                public LuaValue call() {
                    try (ModelAsset<ModelAnimations> animation = ClientModelAssets
                            .animations(new AssetLocation(definition()))) {
                        LuaTable clips = new LuaTable();
                        for (Map.Entry<String, ModelAnimations.Clip> entry : animation.getContent().getValue().clips
                                .entrySet()) {
                            LuaTable clip = new LuaTable();
                            clip.set("duration", entry.getValue().duration);
                            clip.set("loop", entry.getValue().loop);
                            clips.set(entry.getKey(), clip);
                        }
                        return clips;
                    } catch (IOException error) {
                        throw new LuaError("Animation: " + error.getMessage());
                    }
                }
            });
        }
    }

    public AssetId getId() {
        return id;
    }

    private AssetDefinition definition() {
        AssetDefinition definition = ScriptAssetScope.findVisible(id);
        if (definition == null) {
            definition = BuiltinAssets.find(id);
        }
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
            if (id.getKind() == AssetKind.MODEL) {
                try (ModelAsset<ModelGeometry> model = ClientModelAssets.model(location)) {
                    return describe(model.getContent());
                }
            }
            if (id.getKind() == AssetKind.ANIMATION) {
                try (ModelAsset<ModelAnimations> animation = ClientModelAssets.animations(location)) {
                    return describe(animation.getContent());
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
