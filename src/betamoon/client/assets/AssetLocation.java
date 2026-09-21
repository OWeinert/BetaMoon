package betamoon.client.assets;

import betamoon.assets.AssetDefinition;
import betamoon.assets.AssetId;
import betamoon.assets.AssetKind;
import betamoon.assets.AssetPath;
import betamoon.luamodloader.ScriptAssetScope;

/**
 * Captures a fallback for retained visuals while resolving live registered
 * metadata when available.
 */
public final class AssetLocation {
    private final AssetKind kind;
    private final AssetId id;
    private final AssetPath fallback;
    private final AssetPath override;
    private final boolean builtin;

    public AssetLocation(AssetDefinition definition) {
        builtin = definition.isBuiltin();
        kind = definition.getId().getKind();
        id = definition.getId();
        fallback = definition.getFallbackPath();
        override = definition.getOverridePath();
    }

    public AssetLocation(AssetKind kind, AssetPath path) {
        builtin = false;
        this.kind = kind;
        id = null;
        fallback = path;
        override = path.getDirectOverridePath();
    }

    public AssetKind getKind() {
        return kind;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public AssetPath getFallback() {
        AssetDefinition current = id == null ? null : ScriptAssetScope.findVisible(id);
        return current == null ? fallback : current.getFallbackPath();
    }

    public AssetPath getOverride() {
        AssetDefinition current = id == null ? null : ScriptAssetScope.findVisible(id);
        return current == null ? override : current.getOverridePath();
    }

    public String getCacheKey() {
        return id == null ? kind.getDirectory() + ":path:" + fallback : kind.getDirectory() + ":key:" + id.getKey();
    }
}
