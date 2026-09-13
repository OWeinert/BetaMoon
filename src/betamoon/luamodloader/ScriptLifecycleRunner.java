package betamoon.luamodloader;

import betamoon.luaapi.module.ModuleRegistry;
import betamoon.tileentity.TileEntityRegistry;
import java.util.List;
import org.luaj.vm2.LuaError;

/**
 * Executes script lifecycle callbacks with consistent owner and failure
 * handling.
 */
final class ScriptLifecycleRunner {
    private static final String SCRIPT_ERROR_PREFIX = "Lua mod failed to load: ";
    private static final String SEPARATOR = "-------------------------------------------------------------\n";

    private final RetainedScriptCatalog retainedScripts;
    private final ErrorReporter errorReporter;

    ScriptLifecycleRunner(RetainedScriptCatalog retainedScripts, ErrorReporter errorReporter) {
        this.retainedScripts = retainedScripts;
        this.errorReporter = errorReporter;
    }

    void run(List<ScriptMod> ordered, List<String> failedMods, boolean hotReload) {
        for (int i = 0; i < ordered.size(); i++) {
            ScriptMod mod = ordered.get(i);
            if (hotReload && NonReloadableScriptRegistry.contains(mod.sourceFileName)) {
                continue;
            }

            try (ScriptExecutionScope ignored = ScriptExecutionScope.open(mod.sourceFileName)) {
                mod.modInit.call();
                if (hotReload && mod.modReload != null && mod.modReload.isfunction()) {
                    mod.modReload.call();
                }
                ModuleRegistry.publish(mod.sourceFileName);
                LuaScriptRegistry.markLoadedByFile(mod.sourceFileName);
                retainedScripts.rememberIfStructural(mod);
            } catch (LuaError error) {
                recordLoadFailure(mod, failedMods, error.getMessage());
            } catch (Throwable error) {
                recordLoadFailure(mod, failedMods, error.toString());
            }
        }
    }

    void unloadReloadableScripts() {
        List<ScriptMod> entries = LuaScriptRegistry.getEntries();
        for (int i = entries.size() - 1; i >= 0; i--) {
            ScriptMod mod = entries.get(i);
            if (NonReloadableScriptRegistry.contains(mod.sourceFileName) || !mod.isLoaded() || mod.modUnload == null
                    || !mod.modUnload.isfunction()) {
                continue;
            }

            try (ScriptExecutionScope ignored = ScriptExecutionScope.open(mod.sourceFileName)) {
                mod.modUnload.call();
            } catch (Throwable error) {
                String message = "Unload hook failed: " + error.getMessage();
                LuaScriptErrors.add(mod.getDisplayName(), message);
                errorReporter.report(
                        "Lua mod unload hook failed: " + mod.getDisplayName() + " (" + error.getMessage() + ")");
            }
        }
    }

    private void recordLoadFailure(ScriptMod mod, List<String> failedMods, String message) {
        ModuleRegistry.discardPending(mod.sourceFileName);
        TileEntityRegistry.removeOwned(mod.sourceFileName);
        ScriptResourceTracker.unload(mod.sourceFileName);
        failedMods.add(mod.name);
        LuaScriptErrors.add(mod.name, message);
        LuaScriptRegistry.markFailedByFile(mod.sourceFileName, message);
        errorReporter.report(SCRIPT_ERROR_PREFIX + mod.name + "\n" + SEPARATOR + message + "\n" + SEPARATOR);
    }

    @FunctionalInterface
    interface ErrorReporter {
        void report(String message);
    }
}
