package betamoon.client.assets;

import betamoon.assets.AssetDefinition;
import betamoon.assets.AssetId;
import betamoon.assets.AssetKind;
import betamoon.assets.AssetPath;
import betamoon.luamodloader.LuaScriptRegistry;
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
    private final String defaultSource;

    public AssetLocation(AssetDefinition definition) {
        builtin = definition.isBuiltin();
        kind = definition.getId().getKind();
        id = definition.getId();
        fallback = definition.getFallbackPath();
        override = definition.getOverridePath();
        defaultSource = definition.getDefaultSource();
    }

    public AssetLocation(AssetKind kind, AssetPath path) {
        builtin = false;
        this.kind = kind;
        id = null;
        fallback = path;
        override = path.getDirectOverridePath();
        defaultSource = LuaScriptRegistry.getCurrentScriptFile();
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

    public String getDefaultSource() {
        AssetDefinition current = id == null ? null : ScriptAssetScope.findVisible(id);
        return current == null ? defaultSource : current.getDefaultSource();
    }

    public String getCacheKey() {
        if (id != null) {
            return kind.getDirectory() + ":key:" + id.getKey();
        }
        return kind.getDirectory() + ":path:" + (defaultSource == null ? "" : defaultSource + ":") + fallback;
    }
}
