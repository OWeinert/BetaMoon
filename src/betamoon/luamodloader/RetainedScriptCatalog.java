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

    boolean appendActiveSource(LuaModSource source, List<ScriptMod> mods) {
        ScriptMod active = activeScripts.get(source.ownerId());
        if (active == null || !NonReloadableScriptRegistry.contains(active.sourceFileName)) {
            return false;
        }
        restoreRegistryEntry(active.sourceFileName, active);
        mods.add(active);
        LuaScriptErrors.addWarning(active.sourceFileName, "Skipped '" + source.entrypointRelative()
                + "': this mod registers " + NonReloadableScriptRegistry.reason(active.sourceFileName)
                + ". Restart Minecraft to reload it.");
        return true;
    }

    void appendMissingScripts(Set<String> seenFiles, List<ScriptMod> mods) {
        for (Map.Entry<String, ScriptMod> entry : activeScripts.entrySet()) {
            String ownerId = entry.getKey();
            if (seenFiles.contains(ownerId)) {
                continue;
            }

            ScriptMod active = entry.getValue();
            restoreRegistryEntry(active.sourceFileName, active);
            mods.add(active);
            LuaScriptErrors.addWarning(active.sourceFileName, "Kept '" + active.sourceFileName
                    + "' active because structural content cannot be unloaded. Restart Minecraft to remove it.");
        }
    }

    void rememberIfStructural(ScriptMod mod) {
        if (NonReloadableScriptRegistry.contains(mod.sourceFileName)) {
            activeScripts.put(ownerId(mod), mod);
        }
    }

    private String ownerId(ScriptMod mod) {
        return mod.source == null ? mod.sourceFileName : mod.source.ownerId();
    }

    private void restoreRegistryEntry(String fileName, ScriptMod active) {
        LuaScriptRegistry.updateParsed(fileName, active.name, active.dependencies, active.modInit, active.modReload,
                active.modUnload, active.description, active.version, active.imagePath);
        ScriptMod restored = LuaScriptRegistry.findByFile(fileName);
        if (restored != null) {
            restored.source = active.source;
        }
        LuaScriptRegistry.markLoadedByFile(fileName);
    }
}
