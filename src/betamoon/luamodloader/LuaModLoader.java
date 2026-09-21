package betamoon.luamodloader;

import betamoon.BetaMoonCommon;

import betamoon.io.FileIo;
import betamoon.io.IoUtils;
import betamoon.luaapi.module.ModuleRegistry;
import betamoon.recipes.RecipeModificationHandler;
import betamoon.tileentity.TileEntityRegistry;
import betamoon.entity.EntityTypeRegistry;
import betamoon.entity.EntityPresentationResources;
import betamoon.wrappers.BlockWrapper;
import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenRegistry;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class LuaModLoader {
    private static final Logger LOGGER = BetaMoonCommon.LOGGER;
    private final LuaScriptFiles scriptFiles = new LuaScriptFiles();
    private final ScriptModParser scriptParser = new ScriptModParser();
    private final ScriptDependencyResolver dependencyResolver = new ScriptDependencyResolver();
    private final RetainedScriptCatalog retainedScripts = new RetainedScriptCatalog();
    private final ScriptLifecycleRunner lifecycleRunner = new ScriptLifecycleRunner(retainedScripts, this::reportError);
    private final File scriptsDirectory;
    private long lastScanTime;
    private long knownFingerprint = Long.MIN_VALUE;
    private long pendingFingerprint = Long.MIN_VALUE;
    private long pendingSince;
    private LoaderPhase phase = LoaderPhase.IDLE;

    public LuaModLoader() {
        this(resolveLuaModsDir(true));
    }

    public LuaModLoader(File scriptsDirectory) {
        this.scriptsDirectory = IoUtils.ensureDirectory(scriptsDirectory);
    }

    /**
     * Loads Lua mods, validates dependencies, and executes each modInit in order.
     */
    public synchronized void loadAndRun() {
        if (phase.isBusy()) {
            return;
        }
        phase = LoaderPhase.INITIAL_LOAD;
        try {
            LuaScriptErrors.clear();
            loadAndRunInternal();
            knownFingerprint = calculateFingerprint();
        } finally {
            phase = LoaderPhase.IDLE;
        }
    }

    private void loadAndRunInternal() {
        LuaContentRegistry.beginLoadPass();
        LuaScriptRegistry.clear();
        List<String> errors = new ArrayList<>();
        List<ScriptMod> mods = loadLuaMods(errors);
        reportLoadedMods(mods);
        Map<String, ScriptMod> modsByName = indexModsByName(mods, errors);
        List<ScriptMod> ordered = orderMods(modsByName, errors);
        discardFailedScriptResources(mods);
        if (!errors.isEmpty()) {
            reportErrors(errors);
        }
        List<String> failedMods = new ArrayList<>();
        runModsInOrder(ordered, failedMods);
        Set<String> presentScripts = new HashSet<>();
        for (ScriptMod mod : mods) {
            presentScripts.add(mod.sourceFileName);
        }
        EntityTypeRegistry.retainOwners(presentScripts);
        EntityPresentationResources.prune();
        reportFailedMods(failedMods);
        reportLoadSummary(ordered, failedMods);
        BiomeGenRegistry.applyBiomeGenerators();
        List<String> dropErrors = new ArrayList<>();
        BlockWrapper.validatePendingDrops(dropErrors);
        EntityTypeRegistry.validateDrops(dropErrors);
        if (!dropErrors.isEmpty()) {
            for (int i = 0; i < dropErrors.size(); i++) {
                LuaScriptErrors.add("Block drops", dropErrors.get(i));
            }
            reportErrors(dropErrors);
        }
        List<String> retainedWarnings = LuaContentRegistry.finishLoadPass();
        for (int i = 0; i < retainedWarnings.size(); i++) {
            String warning = retainedWarnings.get(i);
            LOGGER.warning(warning);
            LuaScriptErrors.addWarning("Hot reload", warning);
        }
        reportIssuesInChat();
    }

    void discardFailedScriptResources(List<ScriptMod> mods) {
        for (int i = 0; i < mods.size(); i++) {
            ScriptMod mod = mods.get(i);
            if (!mod.isFailed()) {
                continue;
            }

            ModuleRegistry.discardPending(mod.sourceFileName);
            TileEntityRegistry.removeOwned(mod.sourceFileName);
            ScriptResourceTracker.unload(mod.sourceFileName);
        }
    }

    /**
     * Removes reversible script effects and performs a complete dependency-ordered
     * reload.
     */
    public synchronized void reloadAll() {
        if (phase.isBusy()) {
            return;
        }
        phase = LoaderPhase.RELOAD_PREFLIGHT;
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
            lifecycleRunner.unloadReloadableScripts();
            ScriptResourceTracker.unloadReloadable();
            WorldGenRegistry.clear();
            BiomeGenRegistry.clear();
            RecipeModificationHandler.createRecipeMap();
            phase = LoaderPhase.HOT_RELOAD;
            loadAndRunInternal();
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
            phase = LoaderPhase.IDLE;
            int errorCount = LuaScriptErrors.getErrorCount();
            ScriptReloadStatus.complete(!reloadFinished && errorCount == 0 ? 1 : errorCount);
        }
    }

    /**
     * Polls cheaply for script changes. Must be called from Minecraft's main
     * thread.
     */
    public synchronized void pollForChanges() {
        long now = System.currentTimeMillis();
        if (phase.isBusy() || now - lastScanTime < 500L) {
            return;
        }
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
        List<LuaScriptFiles.PreflightFailure> failures = scriptFiles.preflight(scriptsDirectory);
        for (int i = 0; i < failures.size(); i++) {
            LuaScriptFiles.PreflightFailure failure = failures.get(i);
            String message = "Reload kept the active scripts because " + failure.getFileName() + " did not compile: "
                    + failure.getCause();
            reportError(message);
            LuaScriptErrors.add(failure.getFileName(), message);
        }
        return failures.isEmpty();
    }

    private long calculateFingerprint() {
        return scriptFiles.fingerprint(scriptsDirectory);
    }

    /**
     * Resolves the .minecraft/luamods directory and creates it if missing.
     *
     * @return the luamods directory or null if it cannot be resolved
     */
    File getOrCreateLuaModsDir() {
        return scriptsDirectory;
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
     * Reads all .lua files from the Lua mods directory and parses them into
     * ScriptMod entries.
     *
     * @param errors
     *            collector for human-readable load errors
     * @return list of successfully parsed mods
     */
    List<ScriptMod> loadLuaMods(List<String> errors) {
        List<ScriptMod> mods = new ArrayList<>();
        Set<String> seenFiles = new HashSet<>();
        File scriptsDir = scriptsDirectory;
        if (scriptsDir == null || !scriptsDir.isDirectory()) {
            return mods;
        }
        List<File> discoveredScripts = scriptFiles.list(scriptsDir);
        for (int i = 0; i < discoveredScripts.size(); i++) {
            File scriptFile = discoveredScripts.get(i);
            String name = scriptFile.getName();
            seenFiles.add(name);
            LuaScriptRegistry.registerFile(name);
            if (phase.isHotReload() && retainedScripts.appendActiveScript(name, mods)) {
                continue;
            }
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
        if (phase.isHotReload()) {
            retainedScripts.appendMissingScripts(seenFiles, mods);
        }
        return mods;
    }

    /**
     * Executes a Lua script to extract name, dependencies, and modInit into a
     * ScriptMod.
     *
     * @param scriptFile
     *            source file used for error context
     * @param scriptText
     *            Lua script contents
     * @param errors
     *            collector for human-readable load errors
     * @return ScriptMod or null if required fields are missing or script errors
     *         occur
     */
    ScriptMod parseLuaMod(File scriptFile, String scriptText, List<String> errors) {
        return scriptParser.parse(scriptFile, scriptText, errors);
    }

    /**
     * Builds a name-to-mod map and reports duplicates as errors.
     *
     * @param mods
     *            parsed mods to index
     * @param errors
     *            collector for duplicate-name errors
     * @return map keyed by mod name
     */
    Map<String, ScriptMod> indexModsByName(List<ScriptMod> mods, List<String> errors) {
        return dependencyResolver.indexByName(mods, errors);
    }

    /**
     * Orders mods by dependency using a DFS topological sort.
     *
     * @param modsByName
     *            mod lookup table
     * @param errors
     *            collector for dependency resolution errors
     * @return ordered list or empty list if a cycle/missing dependency is found
     */
    List<ScriptMod> orderMods(Map<String, ScriptMod> modsByName, List<String> errors) {
        return dependencyResolver.order(modsByName, errors);
    }

    /**
     * Executes modInit for each mod in order, using an internal failed list.
     *
     * @param ordered
     *            ordered mods to execute
     */
    void runModsInOrder(List<ScriptMod> ordered) {
        runModsInOrder(ordered, new ArrayList<>());
    }

    /**
     * Executes modInit for each mod and records failures instead of crashing.
     *
     * @param ordered
     *            ordered mods to execute
     * @param failedMods
     *            output list for names that fail during modInit
     */
    void runModsInOrder(List<ScriptMod> ordered, List<String> failedMods) {
        lifecycleRunner.run(ordered, failedMods, phase.isHotReload());
    }

    /**
     * Formats a readable cycle path for dependency errors.
     *
     * @param stack
     *            current DFS stack
     * @param name
     *            mod name that closed the cycle
     * @return cycle path string
     */
    String formatCycle(List<String> stack, String name) {
        return dependencyResolver.formatCycle(stack, name);
    }

    /**
     * Emits each collected error via the logger and in-game chat.
     *
     * @param errors
     *            list of error strings to report
     */
    void reportErrors(List<String> errors) {
        for (int i = 0; i < errors.size(); i++) {
            reportError(errors.get(i));
        }
    }

    /**
     * Logs a single error and attempts to surface it in the player chat.
     *
     * @param message
     *            error message to display
     */
    void reportError(String message) {
        LOGGER.severe(message);
    }

    /**
     * Displays collected issues using the same text and severity colors as the
     * popup.
     */
    private void reportIssuesInChat() {
        LuaLoaderFeedback.reportIssues();
    }

    /** Displays the final error count after a reload attempt finishes. */
    private void reportReloadSummaryInChat() {
        LuaLoaderFeedback.reportReloadSummary();
    }

    /**
     * Logs a summary and list of discovered Lua mods.
     *
     * @param mods
     *            list of parsed ScriptMod entries
     */
    void reportLoadedMods(List<ScriptMod> mods) {
        int count = mods.size();
        if (count == 0) {
            LOGGER.info("Found 0 Lua mods.");
            LOGGER.info("Lua mods: (none)");
            return;
        }
        LOGGER.info("Found " + count + " Lua mods:");
        for (int i = 0; i < count; i++) {
            ScriptMod mod = mods.get(i);
            LOGGER.info("- " + mod.name);
        }
    }

    /**
     * Logs the list of mods that failed during modInit execution.
     *
     * @param failedMods
     *            list of failed mod names
     */
    void reportFailedMods(List<String> failedMods) {
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
     * @param ordered
     *            list of mods that were scheduled to run
     * @param failedMods
     *            list of failed mod names
     */
    void reportLoadSummary(List<ScriptMod> ordered, List<String> failedMods) {
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
