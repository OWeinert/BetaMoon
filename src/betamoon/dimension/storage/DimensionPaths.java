package betamoon.dimension.storage;

import betamoon.dimension.DimensionKey;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.minecraft.src.ISaveHandler;

/**
 * Engine-owned paths below a root save, without following child
 * symlinks/junctions outside it.
 */
public final class DimensionPaths {
    private final Path root;

    public DimensionPaths(Path saveRoot) throws IOException {
        root = saveRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IOException("World save root is unavailable");
        }
    }

    public static DimensionPaths forSave(ISaveHandler save) throws IOException {
        File probe = save.func_28113_a("betamoon_dimension_probe");
        if (probe == null || probe.getParentFile() == null || probe.getParentFile().getParentFile() == null) {
            throw new IOException("Save handler does not expose persistent root storage");
        }
        return new DimensionPaths(probe.getParentFile().getParentFile().toPath());
    }

    public Path root() {
        return root;
    }

    public Path resolve(String relative) throws IOException {
        Path result = root.resolve(relative).normalize();
        if (!result.startsWith(root) || result.equals(root)) {
            throw new IOException("Dimension storage path escapes its root save");
        }
        Path realRoot = root.toRealPath();
        Path current = root;
        for (Path component : root.relativize(result)) {
            current = current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !current.toRealPath().startsWith(realRoot)) {
                    throw new IOException("Dimension storage follows an unsafe link: " + current);
                }
            } else if (!Files.notExists(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Cannot inspect dimension storage path: " + current);
            }
        }
        return result;
    }

    public Path dimension(DimensionKey key) throws IOException {
        return resolve("betamoon/dimensions/" + key.storagePath());
    }
}
