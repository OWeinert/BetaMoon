package betamoon.luamodloader;

import betamoon.luaapi.BetaMoonModule;
import betamoon.luaapi.module.ModuleRegistry;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Evaluates one Lua source file and extracts its mod declaration. */
final class ScriptModParser {
    ScriptMod parse(File scriptFile, String scriptText, List<String> errors) {
        Globals globals = JsePlatform.standardGlobals();
        globals.load(new BetaMoonModule());
        try (ScriptExecutionScope ignored = ScriptExecutionScope.open(scriptFile.getName())) {
            globals.load(scriptText, scriptFile.getName()).call();
        } catch (LuaError error) {
            errors.add("Error during Lua mod init: " + scriptFile.getName() + " (" + error.getMessage() + ")");
            fail(scriptFile.getName(), error.getMessage());
            return null;
        } catch (Throwable error) {
            String message = error.toString();
            errors.add("Error while evaluating Lua mod: " + scriptFile.getName() + " (" + message + ")");
            fail(scriptFile.getName(), message);
            return null;
        }

        LuaValue nameValue = globals.get("name");
        if (!nameValue.isstring()) {
            errors.add("Lua mod missing name: " + scriptFile.getName());
            fail(scriptFile.getName(), "Missing required mod name.");
            return null;
        }

        String modName = nameValue.tojstring();
        if (modName.trim().isEmpty()) {
            errors.add("Lua mod has empty name: " + scriptFile.getName());
            fail(scriptFile.getName(), "Script has empty or whitespace-only name.");
            return null;
        }
        if (LuaScriptRegistry.hasScriptName(modName)) {
            String message = "Duplicate Lua mod name field: '" + modName + "'. Another script already uses this name.";
            errors.add(message);
            fail(scriptFile.getName(), message);
            return null;
        }

        List<String> dependencies = readDependencies(globals.get("dependencies"));
        String description = optionalString(globals, "description");
        String version = optionalString(globals, "version");
        String imagePath = optionalString(globals, "image");
        LuaValue modInit = globals.get("modInit");
        if (!modInit.isfunction()) {
            fail(scriptFile.getName(), "Missing modInit function.");
            return null;
        }

        LuaValue modReload = optionalLifecycleFunction(globals, "modReload", scriptFile.getName(), errors);
        LuaValue modUnload = optionalLifecycleFunction(globals, "modUnload", scriptFile.getName(), errors);
        if (modReload == null || modUnload == null) {
            return null;
        }
        return LuaScriptRegistry.updateParsed(scriptFile.getName(), modName, dependencies, modInit, modReload,
                modUnload, description, version, imagePath);
    }

    private List<String> readDependencies(LuaValue table) {
        List<String> dependencies = new ArrayList<>();
        if (!table.istable()) {
            return dependencies;
        }

        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) {
                return dependencies;
            }

            LuaValue value = next.arg(2);
            if (value.isstring()) {
                dependencies.add(value.tojstring());
            }
        }
    }

    private String optionalString(Globals globals, String field) {
        LuaValue value = globals.get(field);
        return value.isstring() ? value.tojstring() : null;
    }

    private LuaValue optionalLifecycleFunction(Globals globals, String field, String fileName, List<String> errors) {
        LuaValue value = globals.get(field);
        if (value.isnil()) {
            return LuaValue.NIL;
        }
        if (value.isfunction()) {
            return value;
        }

        String message = fileName + ": " + field + " must be a function when declared.";
        errors.add(message);
        fail(fileName, message);
        return null;
    }

    private void fail(String fileName, String message) {
        ModuleRegistry.discardPending(fileName);
        ScriptResourceTracker.unload(fileName);
        LuaScriptErrors.add(fileName, message);
        LuaScriptRegistry.markFailedByFile(fileName, message);
    }
}
