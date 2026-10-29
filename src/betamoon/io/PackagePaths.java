package betamoon.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Shared package containment, including NTFS junctions and missing descendants
 * of existing links.
 */
public final class PackagePaths {
    private PackagePaths() {
    }

    public static File contained(File root, File file, boolean allowRoot, String description) throws IOException {
        File logical = file.getCanonicalFile();
        Path realRoot = root.toPath().toRealPath();
        Path existing = logical.toPath();
        Deque<Path> missing = new ArrayDeque<>();
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            missing.addFirst(existing.getFileName());
            existing = existing.getParent();
        }
        if (existing == null) {
            throw new IOException(description + " has no existing filesystem ancestor: " + file);
        }
        Path real = existing.toRealPath();
        for (Path segment : missing) {
            real = real.resolve(segment);
        }
        if (!real.startsWith(realRoot) || !allowRoot && real.equals(realRoot)) {
            throw new IOException(description + " escapes its package root: " + file);
        }
        // Preserve authored logical names for owner IDs/module paths. Directory cycle
        // checks use real paths separately.
        return logical;
    }
}
