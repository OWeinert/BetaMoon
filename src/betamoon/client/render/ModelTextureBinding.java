package betamoon.client.render;

import betamoon.assets.NativeTextureSource;
import betamoon.client.assets.AssetLocation;
import betamoon.client.assets.NativeTextureResources;
import betamoon.resources.LuaTextureResources;
import java.io.IOException;

/** One retained model material texture, with optional native atlas-local UVs. */
public final class ModelTextureBinding implements AutoCloseable {
    private final String resource;
    private final NativeTextureSource nativeSource;
    private int width;
    private int height;

    public ModelTextureBinding(AssetLocation location) throws IOException {
        nativeSource = location.getNativeTexture();
        resource = nativeSource == null ? LuaTextureResources.register(location) : nativeSource.getResource();
        refresh();
    }

    public String resource() {
        return resource;
    }

    public void refresh() throws IOException {
        if (nativeSource != null) {
            NativeTextureResources.Selection selection = NativeTextureResources.inspect(nativeSource);
            width = selection.width;
            height = selection.height;
        }
    }

    public double mapU(double local) {
        return nativeSource == null ? local : nativeSource.mapU(local, width);
    }

    public double mapV(double local) {
        return nativeSource == null ? local : nativeSource.mapV(local, height);
    }

    @Override
    public void close() {
        if (nativeSource == null) {
            LuaTextureResources.release(resource);
        }
    }
}
