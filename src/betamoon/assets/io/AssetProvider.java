package betamoon.assets.io;

import betamoon.assets.AssetPath;
import java.io.IOException;

/**
 * Reads bounded, detached bytes. A missing resource returns null; I/O failures
 * remain explicit.
 */
public interface AssetProvider {
    boolean exists(AssetPath path) throws IOException;

    byte[] read(AssetPath path, int maxBytes) throws IOException;

    String getName();
}
