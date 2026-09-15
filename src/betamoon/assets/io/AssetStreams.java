package betamoon.assets.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class AssetStreams {
    private AssetStreams() {
    }

    static byte[] read(InputStream input, int limit) throws IOException {
        if (limit <= 0) {
            throw new IllegalArgumentException("Asset byte limit must be positive");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count > limit - output.size()) {
                throw new IOException("Asset exceeds " + limit + " bytes");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
