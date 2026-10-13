package betamoon.io;

import betamoon.BetaMoonCommon;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/** Resolves instance-owned script content and migrates the former scripts directory. */
public final class BetaMoonDirectories {
    public static final String HOME_NAME = "betamoon";
    public static final String SCRIPTS_NAME = "scripts";
    public static final String CONFIG_NAME = "config";
    private static final String LEGACY_SCRIPTS_NAME = "lua_scripts";
    private static final Map<Path, File> activeScriptsDirectories = new HashMap<>();

    private BetaMoonDirectories() {
    }

    public static synchronized File scriptsDirectory(File instanceDirectory, boolean create) {
        if (instanceDirectory == null) {
            return null;
        }

        Path instancePath = instanceDirectory.toPath().toAbsolutePath().normalize();
        File selected = activeScriptsDirectories.get(instancePath);
        if (selected != null) {
            return create ? IoUtils.ensureDirectory(selected) : selected.isDirectory() ? selected : null;
        }

        File current = new File(new File(instanceDirectory, HOME_NAME), SCRIPTS_NAME);
        File legacy = new File(instanceDirectory, LEGACY_SCRIPTS_NAME);
        if (!create) {
            if (current.isDirectory()) {
                return current;
            }
            return legacy.isDirectory() ? legacy : null;
        }

        selected = createOrMigrateScriptsDirectory(current, legacy);
        if (selected != null) {
            activeScriptsDirectories.put(instancePath, selected);
        }
        return selected;
    }

    public static File scriptConfigDirectory(File scriptsDirectory) {
        if (scriptsDirectory == null) {
            return null;
        }

        File root = scriptsDirectory.getAbsoluteFile();
        File parent = root.getParentFile();
        if (parent == null) {
            return null;
        }
        File home = SCRIPTS_NAME.equals(root.getName()) && HOME_NAME.equals(parent.getName())
                ? parent : new File(parent, HOME_NAME);
        return new File(home, CONFIG_NAME);
    }

    public static File scriptConfigDirectoryForInstance(File instanceDirectory) {
        if (instanceDirectory == null) {
            return null;
        }
        return new File(new File(instanceDirectory, HOME_NAME), CONFIG_NAME);
    }

    private static File createOrMigrateScriptsDirectory(File current, File legacy) {
        Path currentPath = current.toPath();
        Path legacyPath = legacy.toPath();
        if (current.isDirectory()) {
            if (legacy.isDirectory()) {
                BetaMoonCommon.LOGGER.warning("Both BetaMoon scripts directories exist; using " + current
                        + " and leaving " + legacy + " unchanged.");
            }
            return current;
        }
        if (Files.exists(currentPath, LinkOption.NOFOLLOW_LINKS)) {
            BetaMoonCommon.LOGGER.warning("BetaMoon scripts path is not a directory: " + current);
            return legacy.isDirectory() ? legacy : null;
        }
        if (legacy.isDirectory()) {
            if (Files.isSymbolicLink(legacyPath)) {
                BetaMoonCommon.LOGGER.warning("Legacy scripts path is a symbolic link; using it without moving: "
                        + legacy);
                return legacy;
            }
            if (IoUtils.ensureDirectory(current.getParentFile()) == null) {
                BetaMoonCommon.LOGGER.warning("Could not create BetaMoon directory; using legacy scripts at " + legacy);
                return legacy;
            }
            try {
                Files.move(legacyPath, currentPath);
                BetaMoonCommon.LOGGER.info("Moved BetaMoon scripts from " + legacy + " to " + current);
                return current;
            } catch (IOException | SecurityException error) {
                BetaMoonCommon.LOGGER.log(Level.WARNING,
                        "Could not move BetaMoon scripts to " + current + "; using " + legacy + " instead.", error);
                return legacy;
            }
        }

        File created = IoUtils.ensureDirectory(current);
        if (created == null) {
            BetaMoonCommon.LOGGER.warning("Could not create BetaMoon scripts directory: " + current);
        }
        return created;
    }
}
