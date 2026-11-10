package betamoon.luamodloader;

import betamoon.assets.AssetDefinition;
import betamoon.assets.AssetId;
import betamoon.assets.AssetKey;
import betamoon.assets.AssetKind;
import betamoon.assets.AssetPath;

/**
 * Publishes a separately owned image for the offscreen loading lifecycle
 * fixture.
 */
public final class LoadingAssetFixture {
    public static void publish(String owner) {
        ScriptExecution.withOwner(owner, () -> {
            try (ScriptAssetScope scope = ScriptAssetScope.open(owner)) {
                ScriptAssetScope.stage(new AssetDefinition(
                        new AssetId(AssetKind.TEXTURE, AssetKey.parse("fixture:loading-background")),
                        AssetPath.parse("builtin/betamoon/textures/gui/controls/button/normal.png"), "png"));
                scope.publish();
            }
            return null;
        });
    }
}
