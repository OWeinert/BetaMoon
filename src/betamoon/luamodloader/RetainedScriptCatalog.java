package betamoon.luamodloader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Retains scripts whose structural registrations cannot be replaced at runtime.
 */
final class RetainedScriptCatalog {
    private final Map<String, ScriptMod> activeScripts = new HashMap<>();

    boolean appendActiveScript(String fileName, List<ScriptMod> mods) {
        if (!NonReloadableScriptRegistry.contains(fileName)) {
            return false;
        }

        ScriptMod active = activeScripts.get(fileName);
        if (active == null) {
            return false;
        }

        restoreRegistryEntry(fileName, active);
        mods.add(active);
        String warning = "Skipped '" + fileName + "': this script registers "
                + NonReloadableScriptRegistry.reason(fileName) + ". Restart Minecraft to reload it.";
        LuaScriptErrors.addWarning(fileName, warning);
        return true;
    }

    void appendMissingScripts(Set<String> seenFiles, List<ScriptMod> mods) {
        for (Map.Entry<String, ScriptMod> entry : activeScripts.entrySet()) {
            String fileName = entry.getKey();
            if (seenFiles.contains(fileName)) {
                continue;
            }

            restoreRegistryEntry(fileName, entry.getValue());
            mods.add(entry.getValue());
            LuaScriptErrors.addWarning(fileName, "Kept '" + fileName
                    + "' active because structural content cannot be unloaded. Restart Minecraft to remove it.");
        }
    }

    void rememberIfStructural(ScriptMod mod) {
        if (NonReloadableScriptRegistry.contains(mod.sourceFileName)) {
            activeScripts.put(mod.sourceFileName, mod);
        }
    }

    private void restoreRegistryEntry(String fileName, ScriptMod active) {
        LuaScriptRegistry.updateParsed(fileName, active.name, active.dependencies, active.modInit, active.modReload,
                active.modUnload, active.description, active.version, active.imagePath);
        LuaScriptRegistry.markLoadedByFile(fileName);
    }
}
