package betamoon.worldgen.terrain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Canonical, length-delimited signatures for resolved generation dependencies.
 */
public final class GenerationSignature {
    private GenerationSignature() {
    }

    public static String of(Map<String, String> dependencies) {
        MessageDigest digest = digest();
        update(digest, "betamoon_generation_v1");
        for (Map.Entry<String, String> entry : new TreeMap<String, String>(dependencies).entrySet()) {
            update(digest, entry.getKey());
            update(digest, entry.getValue());
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) {
            result.append(Character.forDigit((value >>> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        if (value == null) {
            throw new IllegalArgumentException("Generation signature values cannot be null");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }
}
