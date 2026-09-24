package betamoon.assets.io;

import betamoon.assets.AssetPath;
import java.io.IOException;

/**
 * Reads bounded, detached bytes. A missing resource returns null; I/O failures
 * remain explicit.
 */
public interface AssetProvider {
    /**
     * Selects the default asset root for one Lua source. Providers without
     * source-specific roots retain their current behavior.
     */
    default AssetProvider forSource(String source) throws IOException {
        return this;
    }

    boolean exists(AssetPath path) throws IOException;

    byte[] read(AssetPath path, int maxBytes) throws IOException;

    String getName();
}
