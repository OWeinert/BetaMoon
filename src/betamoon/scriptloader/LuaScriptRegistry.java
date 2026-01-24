package betamoon.scriptloader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaValue;

public final class LuaScriptRegistry {
    private static final List entries = new ArrayList();
    private static final Map byFile = new HashMap();
    private static final Map byName = new HashMap();
    private static final ThreadLocal currentScriptFile = new ThreadLocal();

    private LuaScriptRegistry() {
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
     * Ensures a file name has a tracked entry, returning the existing entry when present.
     *
     * @param fileName script file name
     * @return tracked script entry
     */
    public static synchronized ScriptMod registerFile(String fileName) {
        ScriptMod entry = (ScriptMod) byFile.get(fileName);
        if (entry != null) {
            return entry;
        }
        entry = new ScriptMod(fileName);
        entries.add(entry);
        byFile.put(fileName, entry);
        return entry;
    }

    /**
     * Updates a script entry with parsed metadata and returns the tracked entry.
     *
     * @param fileName script file name
     * @param name declared mod name
     * @param dependencies dependency list
     * @param modInit init function
     * @param description description string or null
     * @param version version string or null
     * @param imagePath optional image path relative to the Lua mods directory
     * @return tracked script entry
     */
    public static synchronized ScriptMod updateParsed(String fileName, String name, List dependencies, LuaValue modInit,
        String description, String version, String imagePath) {
        ScriptMod entry = (ScriptMod) byFile.get(fileName);
        if (entry == null) {
            entry = registerFile(fileName);
        }
        entry.name = name;
        entry.dependencies = dependencies;
        entry.modInit = modInit;
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

    /**
     * Records a failure for a script identified by its file name.
     *
     * @param fileName script file name
     * @param reason failure description
     */
    public static synchronized void markFailedByFile(String fileName, String reason) {
        ScriptMod entry = (ScriptMod) byFile.get(fileName);
        if (entry == null) {
            entry = registerFile(fileName);
        }
        entry.failed = true;
        entry.failureReason = reason;
    }

    /**
     * Records a failure for a script identified by its declared name.
     *
     * @param name declared mod name
     * @param reason failure description
     */
    public static synchronized void markFailedByName(String name, String reason) {
        ScriptMod entry = (ScriptMod) byName.get(name);
        if (entry == null) {
            return;
        }
        entry.failed = true;
        entry.failureReason = reason;
    }

    /**
     * Marks a script as successfully loaded by file name.
     *
     * @param fileName script file name
     */
    public static synchronized void markLoadedByFile(String fileName) {
        ScriptMod entry = (ScriptMod) byFile.get(fileName);
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
    public static synchronized List getEntries() {
        return new ArrayList(entries);
    }

    /**
     * Returns true when a script with the given name has been registered.
     *
     * @param name script name
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
        Object value = currentScriptFile.get();
        return value == null ? null : value.toString();
    }
}
