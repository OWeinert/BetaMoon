package betamoon.io;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Shared file reading helpers.
 */
public final class FileIo {
    private FileIo() {
    }

    /**
     * Reads a UTF-8 text file, strips a BOM, and normalizes line endings to LF.
     */
    public static String readUtf8Normalized(File file) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        FileInputStream input = new FileInputStream(file);
        try {
            byte[] data = new byte[4096];
            int count;
            while ((count = input.read(data)) != -1) {
                buffer.write(data, 0, count);
            }
        } finally {
            input.close();
        }
        String text = buffer.toString(StandardCharsets.UTF_8.name());
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }
}
