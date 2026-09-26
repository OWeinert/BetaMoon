package betamoon.luamodloader;

import betamoon.luaapi.module.ModuleRegistry;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Evaluates one Lua mod entrypoint and resolves its declared metadata. */
final class ScriptModParser {
    ScriptMod parse(File scriptFile, String scriptText, List<String> errors) {
        try {
            File canonical = scriptFile.getCanonicalFile();
            File root = canonical.getParentFile();
            String fileName = canonical.getName();
            String owner = fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".lua")
                    ? fileName.substring(0, fileName.length() - 4) : fileName;
            LuaModSource source = new LuaModSource(root, canonical, null, owner, fileName, owner + "/",
                    Collections.singletonList(canonical), LuaModSource.Layout.SINGLE_FILE, null);
            return parse(source, scriptText, errors);
        } catch (IOException error) {
            String message = "Could not resolve Lua mod source: " + error.getMessage();
            errors.add(message);
            fail(scriptFile.getName(), message);
            return null;
        }
    }

    ScriptMod parse(LuaModSource source, String scriptText, List<String> errors) {
        String sourceName = source.entrypointRelative();
        final Globals globals;
        try {
            globals = LuaModEnvironment.create(source);
        } catch (IOException error) {
            String message = "Could not prepare Lua mod environment: " + error.getMessage();
            errors.add(sourceName + ": " + message);
            fail(sourceName, message);
            return null;
        }

        try (ScriptExecutionScope ignored = ScriptExecutionScope.open(sourceName)) {
            globals.load(scriptText, "@" + sourceName).call();
        } catch (LuaError error) {
            errors.add("Error during Lua mod init: " + sourceName + " (" + error.getMessage() + ")");
            fail(sourceName, error.getMessage());
            return null;
        } catch (Throwable error) {
            String message = error.toString();
            errors.add("Error while evaluating Lua mod: " + sourceName + " (" + message + ")");
            fail(sourceName, message);
            return null;
        }

        LuaModManifest manifest = source.manifest();
        String modName = resolveString(globals, "name", manifest == null ? null : manifest.name(), true, false,
                sourceName, errors);
        if (modName == null) {
            return null;
        }
        if (LuaScriptRegistry.hasScriptName(modName)) {
            String message = "Duplicate Lua mod name field: '" + modName + "'. Another script already uses this name.";
            errors.add(message);
            fail(sourceName, message);
            return null;
        }

        List<String> dependencies = resolveDependencies(globals, manifest, sourceName, errors);
        if (dependencies == null) {
            return null;
        }
        String description = resolveString(globals, "description", manifest == null ? null : manifest.description(),
                false, true, sourceName, errors);
        String version = resolveString(globals, "version", manifest == null ? null : manifest.version(), false,
                false, sourceName, errors);
        String imagePath = resolveString(globals, "image", manifest == null ? null : manifest.image(), false, false,
                sourceName, errors);
        if (hasNewError(sourceName)) {
            return null;
        }

        LuaValue modInit = globals.get("modInit");
        if (!modInit.isfunction()) {
            String message = "Missing modInit function.";
            errors.add(sourceName + ": " + message);
            fail(sourceName, message);
            return null;
        }

        LuaValue modReload = optionalLifecycleFunction(globals, "modReload", sourceName, errors);
        LuaValue modUnload = optionalLifecycleFunction(globals, "modUnload", sourceName, errors);
        if (modReload == null || modUnload == null) {
            return null;
        }
        return LuaScriptRegistry.updateParsed(source, modName, dependencies, modInit, modReload, modUnload,
                description, version, imagePath);
    }

    private String resolveString(Globals globals, String field, String manifestValue, boolean required,
            boolean allowEmpty, String sourceName, List<String> errors) {
        LuaValue luaValue = globals.get(field);
        String luaString = null;
        if (!luaValue.isnil()) {
            if (!luaValue.isstring()) {
                metadataError(sourceName, field + " must be a string when declared.", errors);
                return null;
            }
            luaString = luaValue.tojstring();
            if ((!allowEmpty && luaString.trim().isEmpty()) || !luaString.equals(luaString.trim())) {
                metadataError(sourceName, field + " must be "
                        + (allowEmpty ? "trimmed." : "a non-empty trimmed string."), errors);
                return null;
            }
        }
        if (manifestValue != null && luaString != null && !manifestValue.equals(luaString)) {
            metadataError(sourceName, "Metadata conflict for '" + field + "': manifest declares '" + manifestValue
                    + "' but the entrypoint declares '" + luaString + "'.", errors);
            return null;
        }
        String resolved = manifestValue != null ? manifestValue : luaString;
        if (required && resolved == null) {
            metadataError(sourceName, "Missing required mod " + field + ".", errors);
        }
        return resolved;
    }

    private List<String> resolveDependencies(Globals globals, LuaModManifest manifest, String sourceName,
            List<String> errors) {
        LuaValue value = globals.get("dependencies");
        List<String> luaDependencies = null;
        if (!value.isnil()) {
            if (!value.istable()) {
                metadataError(sourceName, "dependencies must be a table when declared.", errors);
                return null;
            }
            luaDependencies = readDependencies(value, sourceName, errors);
            if (luaDependencies == null) {
                return null;
            }
        }
        if (manifest != null && manifest.hasDependencies()) {
            if (luaDependencies != null && !manifest.dependencies().equals(luaDependencies)) {
                metadataError(sourceName,
                        "Metadata conflict for 'dependencies': manifest and entrypoint lists differ.", errors);
                return null;
            }
            return manifest.dependencies();
        }
        return luaDependencies == null ? new ArrayList<String>() : luaDependencies;
    }

    private List<String> readDependencies(LuaValue table, String sourceName, List<String> errors) {
        List<String> dependencies = new ArrayList<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) {
                return dependencies;
            }
            LuaValue value = next.arg(2);
            if (!value.isstring() || value.tojstring().trim().isEmpty()) {
                metadataError(sourceName, "dependencies must contain only non-empty strings.", errors);
                return null;
            }
            dependencies.add(value.tojstring());
        }
    }

    private LuaValue optionalLifecycleFunction(Globals globals, String field, String fileName, List<String> errors) {
        LuaValue value = globals.get(field);
        if (value.isnil()) {
            return LuaValue.NIL;
        }
        if (value.isfunction()) {
            return value;
        }
        String message = field + " must be a function when declared.";
        errors.add(fileName + ": " + message);
        fail(fileName, message);
        return null;
    }

    private void metadataError(String sourceName, String message, List<String> errors) {
        errors.add(sourceName + ": " + message);
        fail(sourceName, message);
    }

    private boolean hasNewError(String sourceName) {
        ScriptMod entry = LuaScriptRegistry.findByFile(sourceName);
        return entry != null && entry.failed;
    }

    private void fail(String fileName, String message) {
        ModuleRegistry.discardPending(fileName);
        ScriptResourceTracker.unload(fileName);
        LuaScriptErrors.add(fileName, message);
        LuaScriptRegistry.markFailedByFile(fileName, message);
    }
}
