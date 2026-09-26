package betamoon.luamodloader;

import betamoon.io.FileIo;
import betamoon.io.ZipArchiveIndex;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable storage description of one discovered Lua mod. */
final class LuaModSource {
    static final int MAX_LUA_SOURCE_BYTES = 8 * 1024 * 1024;
    static final int MAX_LUA_SOURCE_FILES = 4096;
    static final long MAX_TOTAL_LUA_SOURCE_BYTES = 64L * 1024L * 1024L;

    enum Layout {
        SINGLE_FILE,
        DIRECTORY,
        ZIP
    }

    private final File scriptsRoot;
    private final File entrypoint;
    private final File moduleRoot;
    private final File archiveFile;
    private final ZipArchiveIndex archive;
    private final String entrypointPath;
    private final String ownerId;
    private final String entrypointRelative;
    private final String modulePathPrefix;
    private final List<String> sourcePaths;
    private final Layout layout;
    private final LuaModManifest manifest;

    LuaModSource(File scriptsRoot, File entrypoint, File moduleRoot, String ownerId, String entrypointRelative,
            String modulePathPrefix, List<File> sourceFiles, Layout layout, LuaModManifest manifest) {
        this.scriptsRoot = scriptsRoot;
        this.entrypoint = entrypoint;
        this.moduleRoot = moduleRoot;
        this.archiveFile = null;
        this.archive = null;
        this.ownerId = ownerId;
        this.entrypointRelative = entrypointRelative;
        this.modulePathPrefix = modulePathPrefix;
        this.layout = layout;
        this.manifest = manifest;
        this.entrypointPath = layout == Layout.DIRECTORY ? relative(moduleRoot, entrypoint) : entrypoint.getName();
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < sourceFiles.size(); i++) {
            paths.add(layout == Layout.DIRECTORY ? relative(moduleRoot, sourceFiles.get(i))
                    : sourceFiles.get(i).getName());
        }
        this.sourcePaths = Collections.unmodifiableList(paths);
    }

    private LuaModSource(File scriptsRoot, File archiveFile, ZipArchiveIndex archive, String ownerId,
            String entrypointPath, List<String> sourcePaths, LuaModManifest manifest) {
        this.scriptsRoot = scriptsRoot;
        this.entrypoint = null;
        this.moduleRoot = null;
        this.archiveFile = archiveFile;
        this.archive = archive;
        this.entrypointPath = entrypointPath;
        this.ownerId = ownerId;
        this.entrypointRelative = archiveFile.getName() + "!/" + entrypointPath;
        this.modulePathPrefix = archiveFile.getName() + "!/";
        this.sourcePaths = Collections.unmodifiableList(new ArrayList<>(sourcePaths));
        this.layout = Layout.ZIP;
        this.manifest = manifest;
    }

    static LuaModSource archive(File scriptsRoot, File archiveFile, ZipArchiveIndex archive, String ownerId,
            String entrypointPath, List<String> sourcePaths, LuaModManifest manifest) {
        return new LuaModSource(scriptsRoot, archiveFile, archive, ownerId, entrypointPath, sourcePaths, manifest);
    }

    File moduleRoot() {
        return moduleRoot;
    }

    String ownerId() {
        return ownerId;
    }

    String entrypointRelative() {
        return entrypointRelative;
    }

    String entrypointPath() {
        return entrypointPath;
    }

    String modulePathPrefix() {
        return modulePathPrefix;
    }

    List<String> sourcePaths() {
        return sourcePaths;
    }

    Layout layout() {
        return layout;
    }

    LuaModManifest manifest() {
        return manifest;
    }

    String readEntrypoint() throws IOException {
        return readLuaSource(entrypointPath);
    }

    String readLuaSource(String path) throws IOException {
        return FileIo.decodeUtf8Normalized(readResource(path, MAX_LUA_SOURCE_BYTES));
    }

    boolean hasResource(String path) throws IOException {
        if (layout == Layout.ZIP) {
            return archive.containsFile(path);
        }
        File file = resolveFile(path);
        return file != null && file.isFile();
    }

    byte[] readResource(String path, int maxBytes) throws IOException {
        if (layout == Layout.ZIP) {
            byte[] value = archive.read(path, maxBytes);
            if (value == null) {
                throw new IOException("ZIP package resource does not exist: " + path);
            }
            return value;
        }
        File file = resolveFile(path);
        if (file == null || !file.isFile()) {
            throw new IOException("Lua mod resource does not exist: " + path);
        }
        if (file.length() > maxBytes) {
            throw new IOException("Lua mod resource exceeds " + maxBytes + " bytes: " + path);
        }
        byte[] value = Files.readAllBytes(file.toPath());
        if (value.length > maxBytes) {
            throw new IOException("Lua mod resource exceeds " + maxBytes + " bytes: " + path);
        }
        return value;
    }

    String sourceDisplayPath(String path) {
        if (layout == Layout.ZIP) {
            return archiveFile.getName() + "!/" + path;
        }
        if (layout == Layout.DIRECTORY) {
            return ownerId + "/" + path;
        }
        return path;
    }

    String resourceCacheKey(String path) {
        if (layout == Layout.ZIP) {
            return archiveFile.getAbsolutePath() + "!/" + path;
        }
        try {
            File file = resolveFile(path);
            return file == null ? path : file.getAbsolutePath();
        } catch (IOException error) {
            return path;
        }
    }

    long storageRevision() {
        File storage = layout == Layout.ZIP ? archiveFile : layout == Layout.DIRECTORY ? moduleRoot : entrypoint;
        return storage == null ? 0L : storage.lastModified() * 31L + storage.length();
    }

    private File resolveFile(String path) throws IOException {
        File root = layout == Layout.DIRECTORY ? moduleRoot : scriptsRoot;
        if (layout == Layout.SINGLE_FILE && !entrypointPath.equals(path)) {
            return null;
        }
        File file = layout == Layout.SINGLE_FILE ? entrypoint : new File(root, path);
        File canonicalRoot = root.getCanonicalFile();
        File canonical = file.getCanonicalFile();
        String rootPath = canonicalRoot.getPath();
        if (canonical.equals(canonicalRoot) || !canonical.getPath().startsWith(rootPath + File.separator)) {
            throw new IOException("Lua mod resource escapes its package root: " + path);
        }
        if (layout == Layout.SINGLE_FILE && !canonical.equals(entrypoint.getCanonicalFile())) {
            return null;
        }
        return canonical;
    }

    private static String relative(File root, File file) {
        return root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
    }
}
