package betamoon.luamodloader;

import java.io.File;
import java.util.List;
import org.luaj.vm2.LuaValue;

public class ScriptMod {
    final String sourceFileName;
    String name;
    List<String> dependencies;
    LuaValue modInit;
    LuaValue modReload;
    LuaValue modUnload;
    String description;
    String version;
    String imagePath;
    boolean loaded;
    boolean failed;
    String failureReason;
    List<String> missingDependencies;
    LuaModSource source;

    /**
     * Creates a script entry for a file that has not yet been parsed.
     *
     * @param sourceFileName
     *            script file name on disk
     */
    public ScriptMod(String sourceFileName) {
        this.sourceFileName = sourceFileName;
        this.description = "";
        this.version = "0.0.0";
        this.imagePath = null;
    }

    /**
     * Creates a parsed Lua mod definition with its name, dependencies, and init
     * function.
     *
     * @param name
     *            declared mod name used for dependency ordering
     * @param dependencies
     *            list of mod names this mod depends on
     * @param modInit
     *            Lua function to invoke during mod initialization
     * @param sourceFileName
     *            script file name on disk
     */
    public ScriptMod(String name, List<String> dependencies, LuaValue modInit, String sourceFileName,
            String description, String version, String imagePath) {
        this.sourceFileName = sourceFileName;
        this.name = name;
        this.dependencies = dependencies;
        this.modInit = modInit;
        this.description = description != null ? description : "";
        if (version == null || version.trim().isEmpty()) {
            this.version = "0.0.0";
        } else {
            this.version = version;
        }
        this.imagePath = imagePath;
    }

    /**
     * Returns the mod name when present, otherwise the source file name.
     *
     * @return display name for the script
     */
    public String getDisplayName() {
        if (name == null || name.trim().isEmpty()) {
            return sourceFileName;
        }
        return name;
    }

    /**
     * Returns the source file name for this script.
     *
     * @return source file name
     */
    public String getSourceFileName() {
        return sourceFileName;
    }

    /**
     * Returns a sort key that falls back to the file name when name is empty.
     *
     * @return sort key for UI lists
     */
    public String getSortName() {
        if (name == null || name.trim().isEmpty()) {
            return sourceFileName;
        }
        return name;
    }

    /**
     * True when a script ran without a recorded failure.
     *
     * @return true when loaded successfully
     */
    public boolean isLoaded() {
        return loaded && !failed;
    }

    /**
     * True when a script ran without a recorded failure.
     *
     * @return true when loaded successfully
     */
    public boolean isFailed() {
        return failed;
    }

    /**
     * Returns the script description, or an empty string when not provided.
     *
     * @return description text (may be empty)
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the script version string.
     *
     * @return version string, defaults to "0.0.0"
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns the optional image path for this script, when provided.
     *
     * @return image path string or null
     */
    public String getImagePath() {
        return imagePath;
    }

    /** Resolves the optional image relative to this mod's storage root. */
    public File resolveImageFile(File scriptsDirectory) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return null;
        }
        if (source != null && source.layout() == LuaModSource.Layout.ZIP) {
            return null;
        }
        File root = source != null && source.layout() == LuaModSource.Layout.DIRECTORY
                ? source.moduleRoot() : scriptsDirectory;
        if (root == null) {
            return null;
        }
        try {
            File canonicalRoot = root.getCanonicalFile();
            File image = new File(canonicalRoot, imagePath).getCanonicalFile();
            String rootPath = canonicalRoot.getPath();
            if (!image.getPath().startsWith(rootPath + File.separator)) {
                return null;
            }
            return image;
        } catch (java.io.IOException ignored) {
            return null;
        }
    }

    /** Reads a package image stored inside a ZIP, or returns null for file-backed mods. */
    public byte[] readArchivedImage(int maxBytes) throws java.io.IOException {
        if (source == null || source.layout() != LuaModSource.Layout.ZIP || imagePath == null) {
            return null;
        }
        return source.readResource(imagePath, maxBytes);
    }

    public String getImageCacheKey() {
        return source == null || imagePath == null ? null : source.resourceCacheKey(imagePath);
    }

    public long getStorageRevision() {
        return source == null ? 0L : source.storageRevision();
    }

    /** Returns single_file, directory, zip, or unknown without exposing disk paths. */
    public String getPackageLayout() {
        return source == null ? "unknown" : source.layout().name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Returns the package-relative entrypoint used by the loader. */
    public String getEntrypointPath() {
        return source == null ? sourceFileName : source.entrypointPath();
    }

    /** Returns package-relative Lua source paths as an immutable snapshot. */
    public List<String> getSourcePaths() {
        return source == null ? java.util.Collections.singletonList(sourceFileName) : source.sourcePaths();
    }

    public boolean supportsReload() {
        return modReload != null && !modReload.isnil();
    }

    public boolean supportsUnload() {
        return modUnload != null && !modUnload.isnil();
    }

    /**
     * Returns the most recent failure reason, if any.
     *
     * @return failure description or null
     */
    public String getFailureReason() {
        return failureReason;
    }

    /**
     * Returns the dependency list for this script.
     *
     * @return list of dependency names or null
     */
    public List<String> getDependencies() {
        return dependencies;
    }

    /**
     * Returns a list of missing dependencies for this script, when known.
     *
     * @return list of missing dependency names or null
     */
    public List<String> getMissingDependencies() {
        return missingDependencies;
    }
}
