package betamoon.network;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Stable digest of Lua gameplay sources. Assets are intentionally excluded. */
public final class GameplayContentDigest {
    private GameplayContentDigest() {
    }

    public static String calculate(File scriptsDirectory) throws IOException {
        if (scriptsDirectory == null || !scriptsDirectory.isDirectory()) {
            return digest(new byte[0]);
        }
        Path root = scriptsDirectory.toPath().toRealPath();
        List<Path> scripts = new ArrayList<Path>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.forEach(path -> {
                if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".lua")) {
                    scripts.add(path);
                }
            });
        }
        Collections.sort(scripts, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                return relative(root, left).compareTo(relative(root, right));
            }
        });

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        for (Path script : scripts) {
            byte[] name = relative(root, script).getBytes(StandardCharsets.UTF_8);
            byte[] source = normalizeLineEndings(Files.readAllBytes(script));
            output.writeInt(name.length);
            output.write(name);
            output.writeInt(source.length);
            output.write(source);
        }
        output.flush();
        return digest(bytes.toByteArray());
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace(File.separatorChar, '/');
    }

    private static byte[] normalizeLineEndings(byte[] source) {
        String text = new String(source, StandardCharsets.UTF_8);
        return text.replace("\r\n", "\n").replace('\r', '\n').getBytes(StandardCharsets.UTF_8);
    }

    private static String digest(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
