package betamoon.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

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
        byte[] data = Files.readAllBytes(file.toPath());
        String text = new String(data, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }
}
