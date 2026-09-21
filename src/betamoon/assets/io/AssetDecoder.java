package betamoon.assets.io;

import java.io.IOException;

/**
 * Validates bytes before publication. A failed decode must release any
 * temporary resources.
 */
@FunctionalInterface
public interface AssetDecoder<T> {
    T decode(byte[] bytes) throws IOException;
}
