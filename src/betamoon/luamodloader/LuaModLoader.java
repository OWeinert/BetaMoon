package betamoon.luamodloader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import betamoon.BetaMoonMain;
import betamoon.io.FileIo;
import betamoon.io.IoUtils;
import betamoon.luaapi.BetaMoonModule;
import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenRegistry;
import net.minecraft.src.ModLoader;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.JsePlatform;

public final class LuaModLoader {
    private static final java.util.logging.Logger LOGGER = BetaMoonMain.LOGGER;
    private static final String SCRIPT_ERROR_PREFIX = "Lua mod failed to load: ";
    private long lastScanTime;
    private long knownFingerprint = Long.MIN_VALUE;
    private long pendingFingerprint = Long.MIN_VALUE;
    private long pendingSince;
    private boolean loading;
    private boolean hotReload;

    /**
     * Loads Lua mods, validates dependencies, and executes each modInit in order.
     */
    public synchronized void loadAndRun() {
        if (loading) return;
        loading = true;
        try {
            LuaScriptErrors.clear();
            loadAndRunInternal();
            knownFingerprint = calculateFingerprint();
        } finally {
            loading = false;
        }
    }

    private void loadAndRunInternal() {
        LuaContentRegistry.beginLoadPass();
        LuaScriptRegistry.clear();
        List errors = new ArrayList();
        List mods = loadLuaMods(errors);
        reportLoadedMods(mods);
        Map modsByName = indexModsByName(mods, errors);
        List ordered = orderMods(modsByName, errors);
        if (!errors.isEmpty()) {
            reportErrors(errors);
        }
        List failedMods = new ArrayList();
        runModsInOrder(ordered, failedMods);
        reportFailedMods(failedMods);
        reportLoadSummary(ordered, failedMods);
        BiomeGenRegistry.applyBiomeGenerators();
        List dropErrors = new ArrayList();
        betamoon.wrappers.BlockWrapper.validatePendingDrops(dropErrors);
        if (!dropErrors.isEmpty()) {
            for (int i = 0; i < dropErrors.size(); i++) {
                LuaScriptErrors.add("Block drops", (String) dropErrors.get(i));
            }
            reportErrors(dropErrors);
        }
        List retainedWarnings = LuaContentRegistry.finishLoadPass();
        for (int i = 0; i < retainedWarnings.size(); i++) {
            String warning = (String) retainedWarnings.get(i);
            LOGGER.warning(warning);
            LuaScriptErrors.addWarning("Hot reload", warning);
        }
        reportIssuesInChat();
    }

    /** Removes reversible script effects and performs a complete dependency-ordered reload. */
    public synchronized void reloadAll() {
        if (loading) return;
        loading = true;
        ScriptReloadStatus.begin();
        boolean reloadFinished = false;
        try {
            LuaScriptErrors.clear();
            if (!preflightScripts()) {
                reportIssuesInChat();
                reportReloadSummaryInChat();
                knownFingerprint = calculateFingerprint();
                reloadFinished = true;
                return;
            }
            invokeUnloadCallbacks();
            ScriptResourceTracker.unloadAll();
            WorldGenRegistry.clear();
            BiomeGenRegistry.clear();
            betamoon.recipes.RecipeModificationHandler.createRecipeMap();
            hotReload = true;
            loadAndRunInternal();
            hotReload = false;
            knownFingerprint = calculateFingerprint();
            LOGGER.info("Lua scripts reloaded.");
            reportReloadSummaryInChat();
            reloadFinished = true;
        } catch (RuntimeException error) {
            LuaScriptErrors.add("Hot reload", "Unexpected reload failure: " + error.getMessage());
            throw error;
        } catch (Error error) {
            LuaScriptErrors.add("Hot reload", "Unexpected reload failure: " + error.getMessage());
            throw error;
        } finally {
            hotReload = false;
            loading = false;
            int errorCount = LuaScriptErrors.getErrorCount();
            ScriptReloadStatus.complete(!reloadFinished && errorCount == 0 ? 1 : errorCount);
        }
    }

    /** Polls cheaply for script changes. Must be called from Minecraft's main thread. */
    public synchronized void pollForChanges() {
        long now = System.currentTimeMillis();
        if (loading || now - lastScanTime < 500L) return;
        lastScanTime = now;
        long fingerprint = calculateFingerprint();
        if (knownFingerprint == Long.MIN_VALUE) {
            knownFingerprint = fingerprint;
        } else if (fingerprint != knownFingerprint) {
            if (fingerprint != pendingFingerprint) {
                pendingFingerprint = fingerprint;
                pendingSince = now;
            } else if (now - pendingSince >= 500L) {
                pendingFingerprint = Long.MIN_VALUE;
                reloadAll();
            }
        }
    }

    /** Compiles every script before active resources are touched. */
    private boolean preflightScripts() {
        File dir = getLuaModsDir();
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return true;
        boolean valid = true;
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (!file.isFile() || !file.getName().endsWith(".lua")) continue;
            try {
                String source = FileIo.readUtf8Normalized(file);
                JsePlatform.standardGlobals().load(source, file.getName());
            } catch (Throwable error) {
                valid = false;
                String message = "Reload kept the active scripts because " + file.getName()
                    + " did not compile: " + error.getMessage();
                reportError(message);
                LuaScriptErrors.add(file.getName(), message);
            }
        }
        return valid;
    }

    private long calculateFingerprint() {
        File dir = getLuaModsDir();
        File[] files = dir == null ? null : dir.listFiles();
        long value = 1125899906842597L;
        if (files == null) return value;
        java.util.Arrays.sort(files, new java.util.Comparator() {
            public int compare(Object left, Object right) {
                return ((File) left).getName().compareTo(((File) right).getName());
            }
        });
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (!file.isFile() || !file.getName().endsWith(".lua")) continue;
            value = value * 31L + file.getName().hashCode();
            value = value * 31L + file.lastModified();
            value = value * 31L + file.length();
        }
        return value;
    }

    /**
     * Resolves the .minecraft/luamods directory and creates it if missing.
     *
     * @return the luamods directory or null if it cannot be resolved
     */
    File getOrCreateLuaModsDir() {
        return resolveLuaModsDir(true);
    }

    /**
     * Resolves the .minecraft/luamods directory for UI access.
     *
     * @return the luamods directory or null if it cannot be resolved
     */
    public static File getLuaModsDir() {
        return resolveLuaModsDir(true);
    }

    /**
     * Resolves the Lua mods directory via IoUtils.
     */
    private static File resolveLuaModsDir(boolean create) {
        return IoUtils.resolveLuaModsDir(LuaModLoader.class, create);
    }

    /**
     * Reads all .lua files from the Lua mods directory and parses them into ScriptMod entries.
     *
     * @param errors collector for human-readable load errors
     * @return list of successfully parsed mods
     */
    List loadLuaMods(List errors) {
        List mods = new ArrayList();
        File scriptsDir = getOrCreateLuaModsDir();
        if (scriptsDir == null || !scriptsDir.isDirectory()) {
            return mods;
        }
        File[] scriptFiles = scriptsDir.listFiles();
        if (scriptFiles == null) {
            return mods;
        }
        for (int i = 0; i < scriptFiles.length; i++) {
            File scriptFile = scriptFiles[i];
            if (!scriptFile.isFile()) {
                continue;
            }
            String name = scriptFile.getName();
            if (!name.endsWith(".lua")) {
                continue;
            }
            LuaScriptRegistry.registerFile(name);
            try {
                String scriptText = FileIo.readUtf8Normalized(scriptFile);
                ScriptMod mod = parseLuaMod(scriptFile, scriptText, errors);
                if (mod != null) {
                    mods.add(mod);
                }
            } catch (IOException e) {
                errors.add("Failed to read Lua script: " + scriptFile.getName());
                LuaScriptErrors.add(scriptFile.getName(), "Failed to read Lua script.");
                LuaScriptRegistry.markFailedByFile(scriptFile.getName(), "Failed to read Lua script.");
            }
        }
        return mods;
    }

    /**
     * Executes a Lua script to extract name, dependencies, and modInit into a ScriptMod.
     *
     * @param scriptFile source file used for error context
     * @param scriptText Lua script contents
     * @param errors collector for human-readable load errors
     * @return ScriptMod or null if required fields are missing or script errors occur
     */
    ScriptMod parseLuaMod(File scriptFile, String scriptText, List errors) {
        Globals globals = JsePlatform.standardGlobals();
        globals.load(new BetaMoonModule());
        LuaValue chunk;
        try {
            LuaScriptRegistry.setCurrentScriptFile(scriptFile.getName());
            chunk = globals.load(scriptText, scriptFile.getName());
            chunk.call();
        } catch (LuaError e) {
            ScriptResourceTracker.unload(scriptFile.getName());
            errors.add("Error during Lua mod init: " + scriptFile.getName() + " (" + e.getMessage() + ")");
            LuaScriptErrors.add(scriptFile.getName(), e.getMessage());
            LuaScriptRegistry.markFailedByFile(scriptFile.getName(), e.getMessage());
            return null;
        } catch (Throwable t) {
            ScriptResourceTracker.unload(scriptFile.getName());
            String message = t.toString();
            errors.add("Error while evaluating Lua mod: " + scriptFile.getName() + " (" + message + ")");
            LuaScriptErrors.add(scriptFile.getName(), message);
            LuaScriptRegistry.markFailedByFile(scriptFile.getName(), message);
            return null;
        } finally {
            LuaScriptRegistry.setCurrentScriptFile(null);
        }
        LuaValue nameValue = globals.get("name");
        if (!nameValue.isstring()) {
            errors.add("Lua mod missing name: " + scriptFile.getName());
            LuaScriptErrors.add(scriptFile.getName(), "Missing required mod name.");
            LuaScriptRegistry.markFailedByFile(scriptFile.getName(), "Missing required mod name.");
            return null;
        }
        String modName = nameValue.tojstring();
        if (modName.trim().isEmpty()) {
            errors.add("Lua mod has empty name: " + scriptFile.getName());
            LuaScriptErrors.add(scriptFile.getName(), "Script has empty or whitespace-only name.");
            LuaScriptRegistry.markFailedByFile(scriptFile.getName(), "Script has empty or whitespace-only name.");
            return null;
        }
        if (LuaScriptRegistry.hasScriptName(modName)) {
            String message = "Duplicate Lua mod name field: '" + modName
                + "'. Another script already uses this name.";
            errors.add(message);
            LuaScriptErrors.add(scriptFile.getName(), message);
            LuaScriptRegistry.markFailedByFile(scriptFile.getName(), message);
            return null;
        }
        List deps = new ArrayList();
        LuaValue depsTable = globals.get("dependencies");
        if (depsTable.istable()) {
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs next = depsTable.next(key);
                key = next.arg1();
                if (key.isnil()) {
                    break;
                }
                LuaValue value = next.arg(2);
                if (value.isstring()) {
                    deps.add(value.tojstring());
                }
            }
        }
        String description = null;
        LuaValue descriptionValue = globals.get("description");
        if (descriptionValue.isstring()) {
            description = descriptionValue.tojstring();
        }
        String version = null;
        LuaValue versionValue = globals.get("version");
        if (versionValue.isstring()) {
            version = versionValue.tojstring();
        }
        String imagePath = null;
        LuaValue imageValue = globals.get("image");
        if (imageValue.isstring()) {
            imagePath = imageValue.tojstring();
        }
        LuaValue modInit = globals.get("modInit");
        if (!modInit.isfunction()) {
            LuaScriptRegistry.markFailedByFile(scriptFile.getName(), "Missing modInit function.");
            return null;
        }
        LuaValue modReload = optionalLifecycleFunction(globals, "modReload", scriptFile.getName(), errors);
        LuaValue modUnload = optionalLifecycleFunction(globals, "modUnload", scriptFile.getName(), errors);
        if (modReload == null || modUnload == null) return null;
        return LuaScriptRegistry.updateParsed(scriptFile.getName(), modName, deps, modInit,
            modReload, modUnload, description, version, imagePath);
    }

    /**
     * Builds a name-to-mod map and reports duplicates as errors.
     *
     * @param mods parsed mods to index
     * @param errors collector for duplicate-name errors
     * @return map keyed by mod name
     */
    Map indexModsByName(List mods, List errors) {
        Map modsByName = new HashMap();
        for (int i = 0; i < mods.size(); i++) {
            ScriptMod mod = (ScriptMod) mods.get(i);
            if (modsByName.containsKey(mod.name)) {
                String errMsg = "Duplicate Lua mod name";
                errors.add(errMsg + ": " + mod.name);
                LuaScriptErrors.add(mod.name, errMsg + ".");
                LuaScriptRegistry.markFailedByFile(mod.sourceFileName, errMsg + ".");
                continue;
            }
            modsByName.put(mod.name, mod);
        }
        return modsByName;
    }

    /**
     * Orders mods by dependency using a DFS topological sort.
     *
     * @param modsByName mod lookup table
     * @param errors collector for dependency resolution errors
     * @return ordered list or empty list if a cycle/missing dependency is found
     */
    List orderMods(Map modsByName, List errors) {
        List ordered = new ArrayList();
        Map state = new HashMap();
        java.util.Set failed = new java.util.HashSet();
        for (Object keyObj : modsByName.keySet()) {
            String name = (String) keyObj;
            if (!state.containsKey(name)) {
                visitMod(name, modsByName, state, ordered, new ArrayList(), errors, failed);
            }
        }
        return ordered;
    }

    /**
     * Depth-first visit for dependency ordering with cycle detection.
     *
     * @param name current mod name being visited
     * @param modsByName mod lookup table
     * @param state visit state map (0/1/2)
     * @param ordered output list for topological order
     * @param stack current DFS stack for cycle reporting
     * @param errors collector for cycle/missing dependency errors
     * @return true when successfully ordered, false on error
     */
    boolean visitMod(String name, Map modsByName, Map state, List ordered, List stack, List errors,
        java.util.Set failed) {
        Integer currentState = (Integer) state.get(name);
        if (currentState != null) {
            if (currentState.intValue() == 1) {
                String message = "Circular dependency detected: " + formatCycle(stack, name);
                errors.add(message);
                LuaScriptErrors.add(name, message);
                LuaScriptRegistry.markFailedByName(name, message);
                markCycleFailed(stack, name, failed);
                return true;
            }
            if (currentState.intValue() == 2) {
                return true;
            }
        }
        state.put(name, new Integer(1));
        stack.add(name);
        ScriptMod mod = (ScriptMod) modsByName.get(name);
        if (mod == null) {
            stack.remove(stack.size() - 1);
            return true;
        }
        List missingDeps = new ArrayList();
        List failedDeps = new ArrayList();
        for (int i = 0; i < mod.dependencies.size(); i++) {
            String dep = (String) mod.dependencies.get(i);
            if (!modsByName.containsKey(dep)) {
                missingDeps.add(dep);
                continue;
            }
            visitMod(dep, modsByName, state, ordered, stack, errors, failed);
            if (failed.contains(dep)) {
                failedDeps.add(dep);
            }
        }
        // Stop scheduling this mod when dependencies are missing or failed.
        if (!missingDeps.isEmpty()) {
            mod.missingDependencies = missingDeps;
            String message = "Missing dependencies for '" + name + "': " + formatMissingDeps(missingDeps);
            errors.add(message);
            LuaScriptErrors.add(name, message);
            LuaScriptRegistry.markFailedByName(name, message);
            failed.add(name);
            stack.remove(stack.size() - 1);
            state.put(name, new Integer(2));
            return true;
        }
        if (!failedDeps.isEmpty()) {
            String message = "Dependency failed to load for '" + name + "': " + formatMissingDeps(failedDeps);
            errors.add(message);
            LuaScriptErrors.add(name, message);
            LuaScriptRegistry.markFailedByName(name, message);
            failed.add(name);
            stack.remove(stack.size() - 1);
            state.put(name, new Integer(2));
            return true;
        }
        mod.missingDependencies = null;
        state.put(name, new Integer(2));
        ordered.add(mod);
        stack.remove(stack.size() - 1);
        return true;
    }

    /**
     * Marks every node participating in a cycle as failed.
     */
    private void markCycleFailed(List stack, String name, java.util.Set failed) {
        int start = stack.indexOf(name);
        if (start == -1) {
            failed.add(name);
            return;
        }
        for (int i = start; i < stack.size(); i++) {
            String modName = (String) stack.get(i);
            failed.add(modName);
            LuaScriptRegistry.markFailedByName(modName, "Circular dependency detected.");
        }
        failed.add(name);
    }

    /**
     * Formats dependency names for logging.
     */
    private String formatMissingDeps(List missingDeps) {
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < missingDeps.size(); i++) {
            if (i > 0) {
                buffer.append(", ");
            }
            buffer.append("'");
            buffer.append(missingDeps.get(i));
            buffer.append("'");
        }
        return buffer.toString();
    }

    /**
     * Executes modInit for each mod in order, using an internal failed list.
     *
     * @param ordered ordered mods to execute
     */
    void runModsInOrder(List ordered) {
        runModsInOrder(ordered, new ArrayList());
    }


    private static final String seperator = "-------------------------------------------------------------\n";
    /**
     * Executes modInit for each mod and records failures instead of crashing.
    *
    * @param ordered ordered mods to execute
    * @param failedMods output list for names that fail during modInit
    */
    void runModsInOrder(List ordered, List failedMods) {
        for (int i = 0; i < ordered.size(); i++) {
            ScriptMod mod = (ScriptMod) ordered.get(i);
            try {
                LuaScriptRegistry.setCurrentScriptFile(mod.sourceFileName);
                mod.modInit.call();
                if (hotReload && mod.modReload != null && mod.modReload.isfunction()) {
                    mod.modReload.call();
                }
                LuaScriptRegistry.markLoadedByFile(mod.sourceFileName);
            } catch (LuaError e) {
                ScriptResourceTracker.unload(mod.sourceFileName);
                failedMods.add(mod.name);
                LuaScriptErrors.add(mod.name, e.getMessage());
                LuaScriptRegistry.markFailedByFile(mod.sourceFileName, e.getMessage());
                reportError(SCRIPT_ERROR_PREFIX + mod.name + "\n"
                + seperator
                + e.getMessage() + "\n" 
                + seperator
                );
            } catch (Throwable t) {
                ScriptResourceTracker.unload(mod.sourceFileName);
                failedMods.add(mod.name);
                LuaScriptErrors.add(mod.name, t.toString());
                LuaScriptRegistry.markFailedByFile(mod.sourceFileName, t.toString());
                reportError(SCRIPT_ERROR_PREFIX + mod.name + "\n"
                + seperator
                + t.toString() + "\n" 
                + seperator);
            } finally {
                LuaScriptRegistry.setCurrentScriptFile(null);
            }
        }
    }

    /** Returns an optional lifecycle function, rejecting non-function declarations. */
    private LuaValue optionalLifecycleFunction(Globals globals, String field, String fileName, List errors) {
        LuaValue value = globals.get(field);
        if (value.isnil()) return LuaValue.NIL;
        if (value.isfunction()) return value;
        String message = fileName + ": " + field + " must be a function when declared.";
        errors.add(message);
        LuaScriptErrors.add(fileName, message);
        LuaScriptRegistry.markFailedByFile(fileName, message);
        return null;
    }

    /** Invokes old-generation unload hooks in reverse script order before automatic cleanup. */
    private void invokeUnloadCallbacks() {
        List entries = LuaScriptRegistry.getEntries();
        for (int i = entries.size() - 1; i >= 0; i--) {
            ScriptMod mod = (ScriptMod) entries.get(i);
            if (!mod.isLoaded() || mod.modUnload == null || !mod.modUnload.isfunction()) continue;
            try {
                LuaScriptRegistry.setCurrentScriptFile(mod.sourceFileName);
                mod.modUnload.call();
            } catch (Throwable error) {
                String message = "Unload hook failed: " + error.getMessage();
                LuaScriptErrors.add(mod.getDisplayName(), message);
                reportError("Lua mod unload hook failed: " + mod.getDisplayName() + " ("
                    + error.getMessage() + ")");
            } finally {
                LuaScriptRegistry.setCurrentScriptFile(null);
            }
        }
    }

    /**
     * Formats a readable cycle path for dependency errors.
     *
     * @param stack current DFS stack
     * @param name mod name that closed the cycle
     * @return cycle path string
     */
    String formatCycle(List stack, String name) {
        int start = stack.indexOf(name);
        if (start == -1) {
            return name;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < stack.size(); i++) {
            if (builder.length() > 0) {
                builder.append(" -> ");
            }
            builder.append(stack.get(i));
        }
        builder.append(" -> ").append(name);
        return builder.toString();
    }

    /**
     * Emits each collected error via the logger and in-game chat.
     *
     * @param errors list of error strings to report
     */
    void reportErrors(List errors) {
        for (int i = 0; i < errors.size(); i++) {
            reportError((String) errors.get(i));
        }
    }

    /**
     * Logs a single error and attempts to surface it in the player chat.
     *
     * @param message error message to display
     */
    void reportError(String message) {
        LOGGER.severe(message);
    }

    /** Displays collected issues using the same text and severity colors as the popup. */
    private void reportIssuesInChat() {
        try {
            net.minecraft.client.Minecraft mc = ModLoader.getMinecraftInstance();
            if (mc != null && mc.thePlayer != null) {
                List issues = LuaScriptErrors.getEntries();
                for (int i = 0; i < issues.size(); i++) {
                    LuaScriptErrors.ScriptIssue issue = (LuaScriptErrors.ScriptIssue) issues.get(i);
                    String color = issue.isWarning() ? "\u00a76" : "\u00a7c";
                    addColoredChatMessage(mc, issue.getMessage(), color);
                }
            }
        } catch (Throwable t) {
            // Ignore chat errors and keep logging to stderr.
        }
    }

    /**
     * Wraps a message before vanilla chat processes it and reapplies the color
     * to every line. Minecraft Beta's own wrapper does not preserve formatting
     * codes when it moves overflowing text onto the next line.
     */
    private void addColoredChatMessage(net.minecraft.client.Minecraft mc, String message, String color) {
        String[] logicalLines = message.replace("\r", "").split("\n", -1);
        for (int i = 0; i < logicalLines.length; i++) {
            String remaining = logicalLines[i];
            if (remaining.length() == 0) {
                mc.thePlayer.addChatMessage(color + "\u00a7r");
                continue;
            }
            while (mc.fontRenderer.getStringWidth(remaining) > 320) {
                int end = 1;
                while (end < remaining.length()
                    && mc.fontRenderer.getStringWidth(remaining.substring(0, end + 1)) <= 320) {
                    end++;
                }
                int space = remaining.lastIndexOf(' ', end - 1);
                int split = space > 0 ? space : end;
                mc.thePlayer.addChatMessage(color + remaining.substring(0, split) + "\u00a7r");
                remaining = remaining.substring(split);
                while (remaining.startsWith(" ")) {
                    remaining = remaining.substring(1);
                }
            }
            if (remaining.length() > 0) {
                mc.thePlayer.addChatMessage(color + remaining + "\u00a7r");
            }
        }
    }

    /** Displays the final error count after a reload attempt finishes. */
    private void reportReloadSummaryInChat() {
        try {
            net.minecraft.client.Minecraft mc = ModLoader.getMinecraftInstance();
            if (mc == null || mc.thePlayer == null) {
                return;
            }
            int errorCount = LuaScriptErrors.getErrorCount();
            String countColor = errorCount == 0 ? "\u00a7f" : "\u00a7c";
            mc.thePlayer.addChatMessage("\u00a7fBetaMoon reloaded with: " + countColor
                + errorCount + " Error/s\u00a7r");
        } catch (Throwable t) {
            // Reload completion must not fail when chat is unavailable.
        }
    }

    /**
     * Logs a summary and list of discovered Lua mods.
     *
     * @param mods list of parsed ScriptMod entries
     */
    void reportLoadedMods(List mods) {
        int count = mods.size();
        if (count == 0) {
            LOGGER.info("Found 0 Lua mods.");
            LOGGER.info("Lua mods: (none)");
            return;
        }
        LOGGER.info("Found " + count + " Lua mods:");
        for (int i = 0; i < count; i++) {
            ScriptMod mod = (ScriptMod) mods.get(i);
            LOGGER.info("- " + mod.name);
        }
    }

    /**
     * Logs the list of mods that failed during modInit execution.
     *
     * @param failedMods list of failed mod names
     */
    void reportFailedMods(List failedMods) {
        if (failedMods == null || failedMods.isEmpty()) {
            return;
        }
        LOGGER.warning("Lua mods failed to load:");
        for (int i = 0; i < failedMods.size(); i++) {
            LOGGER.warning("- " + failedMods.get(i));
        }
    }

    /**
     * Logs a summary of mod load results.
     *
     * @param ordered list of mods that were scheduled to run
     * @param failedMods list of failed mod names
     */
    void reportLoadSummary(List ordered, List failedMods) {
        int total = ordered == null ? 0 : ordered.size();
        int failed = failedMods == null ? 0 : failedMods.size();
        int succeeded = total - failed;
        String msg = "Lua mod load summary: " + succeeded + " succeeded, " + failed + " failed.";
        if (failed > 0) {
            LOGGER.warning(msg);
        } else {
            LOGGER.info(msg);
        }
    }
}
