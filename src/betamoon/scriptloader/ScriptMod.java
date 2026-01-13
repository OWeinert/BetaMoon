package betamoon.scriptloader;

import java.util.List;
import org.luaj.vm2.LuaValue;

public class ScriptMod {
    final String sourceFileName;
    String name;
    List dependencies;
    LuaValue modInit;
    String description;
    String version;
    boolean loaded;
    boolean failed;
    String failureReason;

    /**
     * Creates a script entry for a file that has not yet been parsed.
     *
     * @param sourceFileName script file name on disk
     */
    public ScriptMod(String sourceFileName) {
        this.sourceFileName = sourceFileName;
        this.description = "";
        this.version = "0.0.0";
    }

    /**
     * Creates a parsed Lua mod definition with its name, dependencies, and init function.
     *
     * @param name declared mod name used for dependency ordering
     * @param dependencies list of mod names this mod depends on
     * @param modInit Lua function to invoke during mod initialization
     * @param sourceFileName script file name on disk
     */
    public ScriptMod(String name, List dependencies, LuaValue modInit, String sourceFileName, String description, String version) {
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
    public List getDependencies() {
        return dependencies;
    }
}
