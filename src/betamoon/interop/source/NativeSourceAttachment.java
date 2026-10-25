package betamoon.interop.source;

import java.nio.file.Path;

/** Explicit local attachment; its content does not become a runtime classpath entry. */
public final class NativeSourceAttachment {
    public final String id;
    public final Path path;
    public final String originFingerprint;

    public NativeSourceAttachment(String id, Path path, String originFingerprint) {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid source attachment ID");
        }
        this.id = id;
        this.path = path.toAbsolutePath().normalize();
        this.originFingerprint = originFingerprint;
    }
}
