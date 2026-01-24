package betamoon.io;

import java.awt.Desktop;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import betamoon.BetaMoonMain;

/**
 * Shared I/O helpers.
 */
public final class IoUtils {
    private static final java.util.logging.Logger LOGGER = BetaMoonMain.LOGGER;

    private IoUtils() {
    }

    /**
     * Closes a Closeable without throwing.
     */
    public static void closeQuietly(Closeable closeable, String context) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            String label = context == null ? "io" : context;
            LOGGER.log(Level.FINE, "Close failed: " + label, e);
        }
    }

    /**
     * Ensures a directory exists, creating it when needed.
     *
     * @param dir directory to validate
     * @return the directory when it exists, otherwise null
     */
    public static File ensureDirectory(File dir) {
        if (dir == null) {
            return null;
        }
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        return dir.isDirectory() ? dir : null;
    }

    /**
     * Resolves a child directory, optionally creating it.
     *
     * @param parent base directory
     * @param name directory name
     * @param create true to create the directory if missing
     * @return resolved directory when it exists, otherwise null
     */
    public static File resolveChildDirectory(File parent, String name, boolean create) {
        if (parent == null || name == null) {
            return null;
        }
        File child = new File(parent, name);
        if (create) {
            return ensureDirectory(child);
        }
        return child.isDirectory() ? child : null;
    }

    /**
     * Resolves the Minecraft directory based on a class code source location.
     *
     * @param anchor class anchored within the mod jar
     * @return Minecraft directory or null when unavailable
     */
    public static File resolveMinecraftDirFromCodeSource(Class anchor) {
        if (anchor == null) {
            return null;
        }
        try {
            File modLocation = new File(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
            File modsDir = modLocation.getParentFile();
            if (modsDir == null) {
                return null;
            }
            return modsDir.getParentFile();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolves the Lua scripts directory near the mod jar.
     *
     * @param anchor class anchored within the mod jar
     * @param create true to create the directory if missing
     * @return Lua scripts directory or null when unavailable
     */
    public static File resolveLuaModsDir(Class anchor, boolean create) {
        File minecraftDir = resolveMinecraftDirFromCodeSource(anchor);
        if (minecraftDir == null) {
            return null;
        }
        File luaModsDir = new File(minecraftDir, BetaMoonMain.LUA_SCRIPTS_DIR);
        if (create) {
            return ensureDirectory(luaModsDir);
        }
        return luaModsDir.isDirectory() ? luaModsDir : null;
    }

    /**
     * Attempts to open a file or folder using the native file explorer.
     *
     * @param path file or directory to open
     * @return true when the open command was dispatched
     */
    public static boolean openInFileExplorer(File path) {
        if (path == null) {
            return false;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(path);
                    return true;
                }
            }
        } catch (Throwable t) {
            // Fall through to shell-based open.
        }
        try {
            String os = System.getProperty("os.name");
            if (os != null) {
                os = os.toLowerCase();
            }
            String resolved = path.getAbsolutePath();
            if (os != null && os.indexOf("win") >= 0) {
                Runtime.getRuntime().exec(new String[] { "explorer", resolved });
            } else if (os != null && os.indexOf("mac") >= 0) {
                Runtime.getRuntime().exec(new String[] { "open", resolved });
            } else {
                Runtime.getRuntime().exec(new String[] { "xdg-open", resolved });
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Opens a file or directory using a sensible default.
     * Directories open in the file explorer, .lua files open with the default program,
     * other files attempt to open with selection in the file explorer when possible.
     *
     * @param path file or directory to open
     * @return true when the open command was dispatched
     */
    public static boolean openPath(File path) {
        if (path == null) {
            return false;
        }
        if (path.isDirectory()) {
            return openInFileExplorer(path);
        }
        String name = path.getName() == null ? "" : path.getName().toLowerCase();
        if (name.endsWith(".lua")) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop desktop = Desktop.getDesktop();
                    if (desktop.isSupported(Desktop.Action.OPEN)) {
                        desktop.open(path);
                        return true;
                    }
                }
            } catch (Throwable t) {
                // Fall through to explorer open.
            }
            return openInFileExplorer(path);
        }
        try {
            String os = System.getProperty("os.name");
            if (os != null) {
                os = os.toLowerCase();
            }
            String resolved = path.getAbsolutePath();
            if (os != null && os.indexOf("win") >= 0) {
                Runtime.getRuntime().exec(new String[] { "explorer", "/select,", resolved });
                return true;
            }
            if (os != null && os.indexOf("mac") >= 0) {
                Runtime.getRuntime().exec(new String[] { "open", "-R", resolved });
                return true;
            }
        } catch (Exception e) {
            // Fall through to directory open.
        }
        File parent = path.getParentFile();
        if (parent != null) {
            return openInFileExplorer(parent);
        }
        return openInFileExplorer(path);
    }
}
