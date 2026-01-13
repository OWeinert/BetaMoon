package betamoon.scriptloader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import betamoon.BetaMoonConstants;
import betamoon.luaapi.BetaMoonModule;
import betamoon.worldgen.BiomeGenRegistry;
import net.minecraft.src.ModLoader;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.JsePlatform;

public final class LuaModLoader {
    private static final Logger LOGGER = Logger.getLogger("BetaMoon");
    private static final String SCRIPT_ERROR_PREFIX = "Lua mod failed to load: ";

    /**
     * Loads Lua mods, validates dependencies, and executes each modInit in order.
     */
    public void loadAndRun() {
        LuaScriptRegistry.clear();
        LuaScriptErrors.clear();
        List errors = new ArrayList();
        List mods = loadLuaMods(errors);
        reportLoadedMods(mods);
        Map modsByName = indexModsByName(mods, errors);
        List ordered = orderMods(modsByName, errors);
        if (!errors.isEmpty()) {
            reportErrors(errors);
            return;
        }
        List failedMods = new ArrayList();
        runModsInOrder(ordered, failedMods);
        reportFailedMods(failedMods);
        reportLoadSummary(ordered, failedMods);
        BiomeGenRegistry.applyBiomeGenerators();
        List dropErrors = new ArrayList();
        betamoon.wrappers.BlockWrapper.validatePendingDrops(dropErrors);
        if (!dropErrors.isEmpty()) {
            reportErrors(dropErrors);
        }
    }

    /**
     * Resolves the .minecraft/luamods directory and creates it if missing.
     *
     * @return the luamods directory or null if it cannot be resolved
     */
    File getOrCreateLuaModsDir() {
        try {
            File modLocation = new File(LuaModLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File modsDir = modLocation.getParentFile();
            if (modsDir == null) {
                return null;
            }
            File minecraftDir = modsDir.getParentFile();
            if (minecraftDir == null) {
                return null;
            }
            File luaModsDir = new File(minecraftDir, BetaMoonConstants.LUA_SCRIPTS_DIR);
            if (!luaModsDir.isDirectory()) {
                luaModsDir.mkdirs();
            }
            return luaModsDir;
        } catch (URISyntaxException e) {
            return null;
        }
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
                String scriptText = readFileToString(scriptFile);
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
            chunk = globals.load(scriptText, scriptFile.getName());
            chunk.call();
        } catch (LuaError e) {
            errors.add("Error during Lua mod init: " + scriptFile.getName() + " (" + e.getMessage() + ")");
            LuaScriptErrors.add(scriptFile.getName(), e.getMessage());
            LuaScriptRegistry.markFailedByFile(scriptFile.getName(), e.getMessage());
            return null;
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
        LuaValue modInit = globals.get("modInit");
        if (!modInit.isfunction()) {
            LuaScriptRegistry.markFailedByFile(scriptFile.getName(), "Missing modInit function.");
            return null;
        }
        return LuaScriptRegistry.updateParsed(scriptFile.getName(), modName, deps, modInit, description, version);
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
                errors.add("Duplicate Lua mod name: " + mod.name);
                LuaScriptErrors.add(mod.name, "Duplicate Lua mod name.");
                LuaScriptRegistry.markFailedByFile(mod.sourceFileName, "Duplicate Lua mod name.");
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
        for (Object keyObj : modsByName.keySet()) {
            String name = (String) keyObj;
            if (!state.containsKey(name)) {
                if (!visitMod(name, modsByName, state, ordered, new ArrayList(), errors)) {
                    return new ArrayList();
                }
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
    boolean visitMod(String name, Map modsByName, Map state, List ordered, List stack, List errors) {
        Integer currentState = (Integer) state.get(name);
        if (currentState != null) {
            if (currentState.intValue() == 1) {
                String message = "Circular dependency detected: " + formatCycle(stack, name);
                errors.add(message);
                LuaScriptErrors.add(name, message);
                LuaScriptRegistry.markFailedByName(name, message);
                return false;
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
            return false;
        }
        for (int i = 0; i < mod.dependencies.size(); i++) {
            String dep = (String) mod.dependencies.get(i);
            if (!modsByName.containsKey(dep)) {
                String message = "Missing dependency '" + dep + "' required by '" + name + "'";
                errors.add(message);
                LuaScriptErrors.add(name, message);
                LuaScriptRegistry.markFailedByName(name, message);
                stack.remove(stack.size() - 1);
                return false;
            }
            if (!visitMod(dep, modsByName, state, ordered, stack, errors)) {
                stack.remove(stack.size() - 1);
                return false;
            }
        }
        state.put(name, new Integer(2));
        ordered.add(mod);
        stack.remove(stack.size() - 1);
        return true;
    }

    /**
     * Executes modInit for each mod in order, using an internal failed list.
     *
     * @param ordered ordered mods to execute
     */
    void runModsInOrder(List ordered) {
        runModsInOrder(ordered, new ArrayList());
    }

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
                mod.modInit.call();
                LuaScriptRegistry.markLoadedByFile(mod.sourceFileName);
            } catch (LuaError e) {
                failedMods.add(mod.name);
                LuaScriptErrors.add(mod.name, e.getMessage());
                LuaScriptRegistry.markFailedByFile(mod.sourceFileName, e.getMessage());
                reportError(SCRIPT_ERROR_PREFIX + mod.name + "\n"
                + "-------------------------------------------------------------\n"
                + e.getMessage() + "\n" 
                + "-------------------------------------------------------------\n"
                );
            } catch (Throwable t) {
                failedMods.add(mod.name);
                LuaScriptErrors.add(mod.name, t.toString());
                LuaScriptRegistry.markFailedByFile(mod.sourceFileName, t.toString());
                reportError(SCRIPT_ERROR_PREFIX + mod.name + "\n"
                + "-------------------------------------------------------------\n"
                + t.toString() + "\n" 
                + "-------------------------------------------------------------\n");
            }
        }
    }

    /**
     * Reads a file into a UTF-8 string.
     *
     * @param file file to read
     * @return file contents as a string
     * @throws IOException when the file cannot be read
     */
    String readFileToString(File file) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        FileInputStream input = new FileInputStream(file);
        try {
            byte[] data = new byte[4096];
            int count;
            while ((count = input.read(data)) != -1) {
                buffer.write(data, 0, count);
            }
        } finally {
            input.close();
        }
        String text = buffer.toString(StandardCharsets.UTF_8.name());
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        // Normalize line endings so LuaJ line numbers match editors on all platforms.
        text = text.replace("\r\n", "\n").replace("\r", "\n");
        return text;
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
        try {
            net.minecraft.client.Minecraft mc = ModLoader.getMinecraftInstance();
            if (mc != null && mc.thePlayer != null) {
                mc.thePlayer.addChatMessage("[BetaMoon] " + message);
            }
        } catch (Throwable t) {
            // Ignore chat errors and keep logging to stderr.
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
        if (failed > 0) {
            LOGGER.warning("Lua mod load summary: " + succeeded + " succeeded, " + failed + " failed.");
        } else {
            LOGGER.info("Lua mod load summary: " + succeeded + " succeeded, " + failed + " failed.");
        }
    }
}
