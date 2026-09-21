package betamoon.instrumentation.hooks.texture;

import betamoon.client.assets.ClientAssets;
import betamoon.resources.LuaTextureResources;
import java.io.InputStream;
import net.minecraft.src.TexturePackBase;

public final class TextureResourceCallbacks {
    private TextureResourceCallbacks() {
    }

    public static InputStream openTexture(TexturePackBase pack, String path) {
        InputStream lua = LuaTextureResources.open(path);
        return lua == null ? pack.getResourceAsStream(path) : lua;
    }

    public static int beforeRefresh() {
        ClientAssets.onNativeRefresh();
        return 0;
    }

    public static void afterRefresh() {
        ClientAssets.onNativeRefreshFinished();
    }
}
