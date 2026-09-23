package betamoon.luamodloader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaValue;

public final class LuaScriptRegistry {
    private static final List<ScriptMod> entries = new ArrayList<>();
    private static final Map<String, ScriptMod> byFile = new HashMap<>();
    private static final Map<String, ScriptMod> byName = new HashMap<>();
    private static final ThreadLocal<String> currentScriptFile = new ThreadLocal<>();

    private LuaScriptRegistry() {
    }

    /** Immutable, callback-free script description for diagnostics and tooling. */
    public static final class Description {
        public final String displayName;
        public final String sourceFileName;
        public final String version;
        public final String description;
        public final String imagePath;
        public final String packageLayout;
        public final String entrypointPath;
        public final List<String> sourcePaths;
        public final List<String> dependencies;
        public final List<String> missingDependencies;
        public final boolean loaded;
        public final boolean failed;
        public final String failureReason;
        public final boolean supportsReload;
        public final boolean supportsUnload;

        private Description(ScriptMod script) {
            displayName = script.getDisplayName();
            sourceFileName = script.getSourceFileName();
            version = script.getVersion();
            description = script.getDescription();
            imagePath = script.getImagePath();
            packageLayout = script.getPackageLayout();
            entrypointPath = script.getEntrypointPath();
            sourcePaths = copy(script.getSourcePaths());
            dependencies = copy(script.getDependencies());
            missingDependencies = copy(script.getMissingDependencies());
            loaded = script.isLoaded();
            failed = script.isFailed();
            failureReason = script.getFailureReason();
            supportsReload = script.supportsReload();
            supportsUnload = script.supportsUnload();
        }

        private static List<String> copy(List<String> values) {
            return values == null ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(values));
        }
    }

    /**
     * Clears all cached script entries for a new load pass.
     */
    public static synchronized void clear() {
        entries.clear();
        byFile.clear();
        byName.clear();
    }

    /**
     * Ensures a file name has a tracked entry, returning the existing entry when
     * present.
     *
     * @param fileName
     *            script file name
     * @return tracked script entry
     */
    public static synchronized ScriptMod registerFile(String fileName) {
        ScriptMod entry = byFile.get(fileName);
        if (entry != null) {
            return entry;
        }
        entry = new ScriptMod(fileName);
        entries.add(entry);
        byFile.put(fileName, entry);
        return entry;
    }

    static synchronized ScriptMod registerSource(LuaModSource source) {
        ScriptMod entry = registerFile(source.entrypointRelative());
        entry.source = source;
        return entry;
    }

    /**
     * Updates a script entry with parsed metadata and returns the tracked entry.
     *
     * @param fileName
     *            script file name
     * @param name
     *            declared mod name
     * @param dependencies
     *            dependency list
     * @param modInit
     *            init function
     * @param description
     *            description string or null
     * @param version
     *            version string or null
     * @param imagePath
     *            optional image path relative to the Lua mods directory
     * @return tracked script entry
     */
    public static synchronized ScriptMod updateParsed(String fileName, String name, List<String> dependencies,
            LuaValue modInit, LuaValue modReload, LuaValue modUnload, String description, String version,
            String imagePath) {
        ScriptMod entry = byFile.get(fileName);
        if (entry == null) {
            entry = registerFile(fileName);
        }
        entry.name = name;
        entry.dependencies = dependencies;
        entry.modInit = modInit;
        entry.modReload = modReload;
        entry.modUnload = modUnload;
        entry.description = description != null ? description : "";
        if (version == null || version.trim().isEmpty()) {
            entry.version = "0.0.0";
        } else {
            entry.version = version;
        }
        if (imagePath != null && !imagePath.trim().isEmpty()) {
            entry.imagePath = imagePath.trim();
        } else {
            entry.imagePath = null;
        }
        if (name != null && !byName.containsKey(name)) {
            byName.put(name, entry);
        }
        return entry;
    }

    static synchronized ScriptMod updateParsed(LuaModSource source, String name, List<String> dependencies,
            LuaValue modInit, LuaValue modReload, LuaValue modUnload, String description, String version,
            String imagePath) {
        registerSource(source);
        ScriptMod entry = updateParsed(source.entrypointRelative(), name, dependencies, modInit, modReload, modUnload,
                description, version, imagePath);
        entry.source = source;
        return entry;
    }

    static synchronized ScriptMod findByFile(String fileName) {
        return byFile.get(fileName);
    }

    /**
     * Records a failure for a script identified by its file name.
     *
     * @param fileName
     *            script file name
     * @param reason
     *            failure description
     */
    public static synchronized void markFailedByFile(String fileName, String reason) {
        ScriptMod entry = byFile.get(fileName);
        if (entry == null) {
            entry = registerFile(fileName);
        }
        entry.failed = true;
        entry.failureReason = reason;
    }

    /**
     * Records a failure for a script identified by its declared name.
     *
     * @param name
     *            declared mod name
     * @param reason
     *            failure description
     */
    public static synchronized void markFailedByName(String name, String reason) {
        ScriptMod entry = byName.get(name);
        if (entry == null) {
            return;
        }
        entry.failed = true;
        entry.failureReason = reason;
    }

    /**
     * Marks a script as successfully loaded by file name.
     *
     * @param fileName
     *            script file name
     */
    public static synchronized void markLoadedByFile(String fileName) {
        ScriptMod entry = byFile.get(fileName);
        if (entry == null) {
            entry = registerFile(fileName);
        }
        entry.loaded = true;
        entry.failed = false;
    }

    /**
     * Returns a snapshot of tracked script entries.
     *
     * @return list of tracked entries
     */
    public static synchronized List<ScriptMod> getEntries() {
        return new ArrayList<>(entries);
    }

    public static synchronized List<Description> snapshot() {
        List<Description> result = new ArrayList<Description>();
        for (ScriptMod entry : entries) {
            result.add(new Description(entry));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns true when a script with the given name has been registered.
     *
     * @param name
     *            script name
     * @return true when the name exists
     */
    public static synchronized boolean hasScriptName(String name) {
        if (name == null) {
            return false;
        }
        return byName.containsKey(name);
    }

    /**
     * Tracks the currently executing script file for warnings/errors.
     */
    static void setCurrentScriptFile(String fileName) {
        if (fileName == null) {
            currentScriptFile.remove();
        } else {
            currentScriptFile.set(fileName);
        }
    }

    /**
     * Returns the current script file, if a script is executing.
     */
    public static String getCurrentScriptFile() {
        return currentScriptFile.get();
    }
}
