package betamoon.interop.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Reproducible SHA-256 identities; timestamps and absolute paths are never content identities. */
public final class CatalogFingerprint {
    private CatalogFingerprint() {
    }

    public static String sha256(Path path) throws IOException {
        return sha256(path, Long.MAX_VALUE);
    }

    public static String sha256(Path path, long maximumBytes) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[16384];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted() || (total += count) > maximumBytes) {
                    throw new IOException("fingerprint_cancelled_or_size_limit");
                }
                digest.update(buffer, 0, count);
            }
        }
        return hex(digest.digest());
    }

    public static String sha256(byte[] bytes) {
        return hex(newDigest().digest(bytes));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java must provide SHA-256", exception);
        }
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(digits[(value & 255) >>> 4]).append(digits[value & 15]);
        }
        return result.toString();
    }
}
