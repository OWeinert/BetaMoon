package betamoon.instrumentation.hooks.texture;

import betamoon.client.assets.ClientAssets;
import betamoon.gui.texture.BuiltinGuiSprites;
import betamoon.resources.LuaTextureResources;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.src.TexturePackBase;

public final class TextureResourceCallbacks {
    private TextureResourceCallbacks() {
    }

    public static InputStream openTexture(TexturePackBase pack, String path) throws IOException {
        InputStream lua = LuaTextureResources.open(path);
        if (lua != null) {
            return lua;
        }
        InputStream sprite = BuiltinGuiSprites.open(pack, path);
        return sprite == null ? pack.getResourceAsStream(path) : sprite;
    }

    public static int beforeRefresh() {
        ClientAssets.onNativeRefresh();
        return 0;
    }

    public static void afterRefresh() {
        ClientAssets.onNativeRefreshFinished();
    }
}
