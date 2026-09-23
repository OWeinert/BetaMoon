package betamoon.luamodloader;

import betamoon.io.FileIo;
import betamoon.io.ZipArchiveIndex;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Discovers complete Lua mod sources and performs source-only loader checks. */
final class LuaScriptFiles {
    private static final long EMPTY_FINGERPRINT = 1125899906842597L;

    List<LuaModSource> discover(File directory, List<PreflightFailure> failures) {
        List<LuaModSource> sources = new ArrayList<>();
        if (directory == null || !directory.isDirectory()) {
            return sources;
        }

        final File root;
        try {
            root = directory.getCanonicalFile();
        } catch (IOException error) {
            failures.add(new PreflightFailure(directory.getPath(), error.getMessage()));
            return sources;
        }

        File[] children = root.listFiles();
        if (children == null) {
            return sources;
        }
        Arrays.sort(children, FILE_NAME_ORDER);

        Map<String, LuaModSource> owners = new HashMap<>();
        for (int i = 0; i < children.length; i++) {
            File child = children[i];
            try {
                LuaModSource source = source(root, child);
                if (source == null) {
                    continue;
                }
                String collisionKey = source.ownerId().toLowerCase(Locale.ROOT);
                LuaModSource previous = owners.put(collisionKey, source);
                if (previous != null) {
                    sources.remove(previous);
                    failures.add(new PreflightFailure(source.entrypointRelative(),
                            "Mod source conflicts with '" + previous.entrypointRelative() + "' because both use owner '"
                                    + source.ownerId() + "'."));
                    continue;
                }
                sources.add(source);
            } catch (IOException error) {
                failures.add(new PreflightFailure(relativeOrName(root, child), message(error)));
            }
        }
        return sources;
    }

    List<PreflightFailure> preflight(File directory) {
        List<PreflightFailure> failures = new ArrayList<>();
        List<LuaModSource> sources = discover(directory, failures);
        for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
            failures.addAll(preflight(sources.get(sourceIndex)));
        }
        return failures;
    }

    List<PreflightFailure> preflight(LuaModSource source) {
        List<PreflightFailure> failures = new ArrayList<>();
        long totalBytes = 0L;
        for (int fileIndex = 0; fileIndex < source.sourcePaths().size(); fileIndex++) {
            String path = source.sourcePaths().get(fileIndex);
            try {
                String text = source.readLuaSource(path);
                totalBytes += text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (totalBytes > LuaModSource.MAX_TOTAL_LUA_SOURCE_BYTES) {
                    failures.add(new PreflightFailure(source.entrypointRelative(), "Lua mod sources exceed "
                            + LuaModSource.MAX_TOTAL_LUA_SOURCE_BYTES + " bytes in total"));
                    break;
                }
                String displayPath = source.sourceDisplayPath(path);
                JsePlatform.standardGlobals().load(text, "@" + displayPath);
            } catch (Throwable error) {
                failures.add(new PreflightFailure(source.sourceDisplayPath(path), message(error)));
            }
        }
        return failures;
    }

    long fingerprint(File directory) {
        List<File> files = fingerprintFiles(directory);
        long value = EMPTY_FINGERPRINT;
        if (directory == null) {
            return value;
        }
        File root;
        try {
            root = directory.getCanonicalFile();
        } catch (IOException error) {
            return value * 31L + error.toString().hashCode();
        }
        for (int i = 0; i < files.size(); i++) {
            value = addFile(value, relative(root, files.get(i)), files.get(i));
        }
        return value;
    }

    private List<File> fingerprintFiles(File directory) {
        List<File> result = new ArrayList<>();
        if (directory == null || !directory.isDirectory()) {
            return result;
        }
        try {
            File root = directory.getCanonicalFile();
            File[] children = root.listFiles();
            if (children == null) {
                return result;
            }
            for (int i = 0; i < children.length; i++) {
                File child = children[i];
                if (isLuaScript(child)) {
                    result.add(child.getCanonicalFile());
                } else if (isZipArchive(child)) {
                    result.add(child.getCanonicalFile());
                } else if (child.isDirectory()) {
                    File manifest = new File(child, LuaModManifest.FILE_NAME);
                    if (manifest.isFile()) {
                        collectPackageFiles(child.getCanonicalFile(), child, result, new HashSet<String>());
                    }
                }
            }
            Collections.sort(result, new Comparator<File>() {
                @Override
                public int compare(File left, File right) {
                    return relative(root, left).compareTo(relative(root, right));
                }
            });
        } catch (IOException error) {
            result.clear();
        }
        return result;
    }

    private LuaModSource source(File root, File child) throws IOException {
        if (isLuaScript(child)) {
            File script = contained(root, child, "Lua script");
            String fileName = script.getName();
            String owner = fileName.substring(0, fileName.length() - 4);
            return new LuaModSource(root, script, null, owner, relative(root, script), owner + "/",
                    Collections.singletonList(script), LuaModSource.Layout.SINGLE_FILE, null);
        }
        if (!child.isDirectory()) {
            return isZipArchive(child) ? archiveSource(root, child) : null;
        }

        File packageRoot = contained(root, child, "Lua mod directory");
        File manifestFile = new File(packageRoot, LuaModManifest.FILE_NAME);
        if (!manifestFile.isFile()) {
            return null;
        }
        manifestFile = contained(packageRoot, manifestFile, "Lua mod manifest");
        LuaModManifest manifest = LuaModManifest.read(manifestFile);
        File entrypoint = contained(packageRoot, new File(packageRoot, manifest.entrypoint()), "Manifest entrypoint");
        if (!entrypoint.isFile()) {
            throw new IOException("Manifest entrypoint does not exist: " + manifest.entrypoint());
        }

        List<File> packageFiles = new ArrayList<>();
        collectPackageFiles(packageRoot, packageRoot, packageFiles, new HashSet<String>());
        List<File> files = luaFiles(packageFiles);
        validateLuaSources(files, "Directory package");
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return relative(packageRoot, left).compareTo(relative(packageRoot, right));
            }
        });
        String owner = relative(root, packageRoot);
        return new LuaModSource(root, entrypoint, packageRoot, owner, relative(root, entrypoint), owner + "/", files,
                LuaModSource.Layout.DIRECTORY, manifest);
    }

    private LuaModSource archiveSource(File root, File file) throws IOException {
        File archiveFile = contained(root, file, "Lua mod ZIP archive");
        ZipArchiveIndex archive = ZipArchiveIndex.open(archiveFile);
        if (!archive.containsFile(LuaModManifest.FILE_NAME)) {
            throw new IOException("ZIP package requires " + LuaModManifest.FILE_NAME + " at its archive root");
        }
        byte[] manifestBytes = archive.read(LuaModManifest.FILE_NAME, LuaModManifest.MAX_BYTES);
        LuaModManifest manifest = LuaModManifest.parse(FileIo.decodeUtf8Normalized(manifestBytes));
        if (!archive.containsFile(manifest.entrypoint())) {
            throw new IOException("Manifest entrypoint does not exist in ZIP package: " + manifest.entrypoint());
        }

        List<String> sourcePaths = new ArrayList<>();
        long totalBytes = 0L;
        List<String> entries = archive.getFiles();
        for (int i = 0; i < entries.size(); i++) {
            String path = entries.get(i);
            if (!path.toLowerCase(Locale.ROOT).endsWith(".lua")) {
                continue;
            }
            if (sourcePaths.size() >= LuaModSource.MAX_LUA_SOURCE_FILES) {
                throw new IOException("ZIP package exceeds " + LuaModSource.MAX_LUA_SOURCE_FILES
                        + " Lua source files");
            }
            long size = archive.sizeOf(path);
            if (size > LuaModSource.MAX_LUA_SOURCE_BYTES) {
                throw new IOException("Lua source exceeds " + LuaModSource.MAX_LUA_SOURCE_BYTES + " bytes: " + path);
            }
            if (size > 0L) {
                totalBytes += size;
                if (totalBytes > LuaModSource.MAX_TOTAL_LUA_SOURCE_BYTES) {
                    throw new IOException("ZIP package Lua sources exceed "
                            + LuaModSource.MAX_TOTAL_LUA_SOURCE_BYTES + " bytes in total");
                }
            }
            sourcePaths.add(path);
        }
        String name = archiveFile.getName();
        String owner = name.substring(0, name.length() - 4);
        return LuaModSource.archive(root, archiveFile, archive, owner, manifest.entrypoint(), sourcePaths, manifest);
    }

    private void collectPackageFiles(File root, File directory, List<File> result, Set<String> visited)
            throws IOException {
        File canonicalDirectory = contained(root, directory, "Lua module directory");
        if (!visited.add(canonicalDirectory.getPath())) {
            throw new IOException("Lua mod directory contains a filesystem cycle: " + relative(root, directory));
        }
        File[] children = canonicalDirectory.listFiles();
        if (children == null) {
            throw new IOException("Could not read Lua mod directory: " + relative(root, canonicalDirectory));
        }
        Arrays.sort(children, FILE_NAME_ORDER);
        for (int i = 0; i < children.length; i++) {
            File child = children[i];
            if (child.isDirectory()) {
                collectPackageFiles(root, child, result, visited);
            } else if (child.isFile()) {
                if (result.size() >= ZipArchiveIndex.MAX_ENTRIES) {
                    throw new IOException("Lua mod directory exceeds " + ZipArchiveIndex.MAX_ENTRIES
                            + " resource files");
                }
                result.add(contained(root, child, "Lua mod resource"));
            }
        }
    }

    private List<File> luaFiles(List<File> packageFiles) {
        List<File> result = new ArrayList<>();
        for (int i = 0; i < packageFiles.size(); i++) {
            File file = packageFiles.get(i);
            if (isLuaScript(file)) {
                result.add(file);
            }
        }
        return result;
    }

    private void validateLuaSources(List<File> files, String description) throws IOException {
        if (files.size() > LuaModSource.MAX_LUA_SOURCE_FILES) {
            throw new IOException(description + " exceeds " + LuaModSource.MAX_LUA_SOURCE_FILES
                    + " Lua source files");
        }
        long totalBytes = 0L;
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            long size = file.length();
            if (size > LuaModSource.MAX_LUA_SOURCE_BYTES) {
                throw new IOException("Lua source exceeds " + LuaModSource.MAX_LUA_SOURCE_BYTES + " bytes: "
                        + file.getName());
            }
            totalBytes += size;
            if (totalBytes > LuaModSource.MAX_TOTAL_LUA_SOURCE_BYTES) {
                throw new IOException(description + " Lua sources exceed "
                        + LuaModSource.MAX_TOTAL_LUA_SOURCE_BYTES + " bytes in total");
            }
        }
    }

    private File contained(File root, File file, String description) throws IOException {
        File canonical = file.getCanonicalFile();
        String rootPath = root.getCanonicalPath();
        String candidatePath = canonical.getPath();
        if (!candidatePath.equals(rootPath) && !candidatePath.startsWith(rootPath + File.separator)) {
            throw new IOException(description + " escapes its mod directory: " + file.getPath());
        }
        return canonical;
    }

    private long addFile(long value, String relative, File file) {
        value = value * 31L + relative.hashCode();
        value = value * 31L + file.lastModified();
        return value * 31L + file.length();
    }

    private boolean isLuaScript(File file) {
        return file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".lua");
    }

    private boolean isZipArchive(File file) {
        return file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private static String relative(File root, File file) {
        return root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
    }

    private static String relativeOrName(File root, File file) {
        try {
            return relative(root, file.getCanonicalFile());
        } catch (IOException error) {
            return file.getName();
        }
    }

    private static String message(Throwable error) {
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }

    private static final Comparator<File> FILE_NAME_ORDER = new Comparator<File>() {
        @Override
        public int compare(File left, File right) {
            int insensitive = left.getName().compareToIgnoreCase(right.getName());
            return insensitive != 0 ? insensitive : left.getName().compareTo(right.getName());
        }
    };

    static final class PreflightFailure {
        private final String fileName;
        private final String cause;

        private PreflightFailure(String fileName, String cause) {
            this.fileName = fileName;
            this.cause = cause;
        }

        String getFileName() {
            return fileName;
        }

        String getCause() {
            return cause;
        }
    }
}
