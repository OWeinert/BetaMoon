package betamoon.debug;

import betamoon.luaapi.module.ModuleRegistry;
import betamoon.luamodloader.LuaModLoader;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptRegistry;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Exports package-safe script, private module, and load-issue descriptions. */
final class DebugScriptExporter implements DebugExporter {
    @Override
    public void export(DebugExportSession session) throws Exception {
        exportScripts(session);
        exportModules(session);
        exportIssues(session);
    }

    private static void exportScripts(DebugExportSession session) throws Exception {
        final List<LuaScriptRegistry.Description> scripts = scripts();
        session.writeTextFile("scripts.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                for (int index = 0; index < scripts.size(); index++) {
                    if (index > 0) {
                        writer.newLine();
                    }
                    LuaScriptRegistry.Description script = scripts.get(index);
                    writer.write("name: " + safe(script.displayName) + " | version: "
                            + safe(script.version) + " | layout: " + script.packageLayout);
                    writer.newLine();
                    writer.write("entrypoint: " + safe(script.entrypointPath) + " | source: "
                            + safe(script.sourceFileName));
                    writer.newLine();
                    writer.write("description: " + safe(script.description) + " | image: " + safe(script.imagePath));
                    writer.newLine();
                    writer.write("state: " + (script.loaded ? "loaded" : script.failed ? "failed" : "pending")
                            + " | reload callback: " + script.supportsReload + " | unload callback: "
                            + script.supportsUnload);
                    writer.newLine();
                    List<String> dependencies = script.dependencies;
                    writer.write("dependencies: " + (dependencies == null || dependencies.isEmpty() ? "none"
                            : safe(String.join(", ", dependencies))));
                    writer.newLine();
                    writer.write("missing dependencies: " + (script.missingDependencies.isEmpty() ? "none"
                            : safe(String.join(", ", script.missingDependencies))));
                    writer.newLine();
                    if (script.failureReason != null) {
                        writer.write("failure: " + safeDiagnostic(script.failureReason));
                        writer.newLine();
                    }
                }
                return scripts.size();
            }
        });
    }

    private static void exportModules(DebugExportSession session) throws Exception {
        final List<String> rows = new ArrayList<String>();
        for (LuaScriptRegistry.Description script : scripts()) {
            String entrypoint = script.entrypointPath;
            for (String source : script.sourcePaths) {
                rows.add("kind: private source module | name: " + moduleName(source) + " | owner: "
                        + safe(script.displayName) + " | relative path: " + safe(source) + " | entrypoint: "
                        + entrypoint.equals(source) + " | owner loaded: " + script.loaded);
            }
        }
        for (ModuleRegistry.Description module : ModuleRegistry.snapshot()) {
            rows.add("kind: cross-mod export | name: " + safe(module.name) + " | owner: " + safe(module.owner)
                    + " | relative path: unavailable | owner loaded: true");
        }
        Collections.sort(rows);
        session.writeTextFile("modules.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                for (String row : rows) {
                    writer.write(row);
                    writer.newLine();
                }
                return rows.size();
            }
        });
    }

    private static void exportIssues(DebugExportSession session) throws Exception {
        final List<LuaScriptErrors.ScriptIssue> issues = new ArrayList<LuaScriptErrors.ScriptIssue>(
                LuaScriptErrors.getEntries());
        Collections.sort(issues, new Comparator<LuaScriptErrors.ScriptIssue>() {
            @Override
            public int compare(LuaScriptErrors.ScriptIssue left, LuaScriptErrors.ScriptIssue right) {
                int script = safe(left.getScriptName()).compareTo(safe(right.getScriptName()));
                if (script != 0) {
                    return script;
                }
                return safe(left.getMessage()).compareTo(safe(right.getMessage()));
            }
        });
        session.writeTextFile("script_issues.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                for (LuaScriptErrors.ScriptIssue issue : issues) {
                    writer.write("severity: " + (issue.isWarning() ? "warning" : "error") + " | script: "
                            + safeDiagnostic(issue.getScriptName()) + " | source: "
                            + safeDiagnostic(issue.getSourceFile()) + " | line: "
                            + (issue.getLine() < 0 ? "unavailable" : Integer.toString(issue.getLine())));
                    writer.newLine();
                    writer.write("    message: " + safeDiagnostic(issue.getMessage()));
                    writer.newLine();
                }
                return issues.size();
            }
        });
    }

    private static List<LuaScriptRegistry.Description> scripts() {
        List<LuaScriptRegistry.Description> scripts =
                new ArrayList<LuaScriptRegistry.Description>(LuaScriptRegistry.snapshot());
        Collections.sort(scripts, new Comparator<LuaScriptRegistry.Description>() {
            @Override
            public int compare(LuaScriptRegistry.Description left, LuaScriptRegistry.Description right) {
                return left.sourceFileName.compareTo(right.sourceFileName);
            }
        });
        return scripts;
    }

    private static String moduleName(String path) {
        String value = path.endsWith(".lua") ? path.substring(0, path.length() - 4) : path;
        if (value.endsWith("/init")) {
            value = value.substring(0, value.length() - 5);
        }
        return safe(value.replace('/', '.'));
    }

    private static String safe(String value) {
        return DebugExportNames.safeString(value == null || value.isEmpty() ? "unavailable" : value);
    }

    private static String safeDiagnostic(String value) {
        if (value == null || value.isEmpty()) {
            return "unavailable";
        }
        File scriptsDirectory = LuaModLoader.getLuaModsDir();
        if (scriptsDirectory == null) {
            return DebugExportNames.safeString(value);
        }
        String redacted = replacePath(value, scriptsDirectory.getAbsolutePath());
        try {
            redacted = replacePath(redacted, scriptsDirectory.getCanonicalPath());
        } catch (IOException ignored) {
            // The absolute path was already removed; canonicalization is best effort.
        }
        return DebugExportNames.safeString(redacted);
    }

    private static String replacePath(String value, String path) {
        if (path == null || path.isEmpty()) {
            return value;
        }
        String result = value.replace(path, "<lua_scripts>");
        result = result.replace(path.replace('\\', '/'), "<lua_scripts>");
        return result.replace(path.replace('/', '\\'), "<lua_scripts>");
    }
}
