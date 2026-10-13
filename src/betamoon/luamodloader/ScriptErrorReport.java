package betamoon.luamodloader;

import betamoon.BetaMoonCommon;
import betamoon.io.BetaMoonDirectories;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Small, side-neutral report for one failed script load attempt. */
final class ScriptErrorReport {
    private static final DateTimeFormatter NAME_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");
    private static final String FILE_PREFIX = "script_err_";
    private static final String FILE_EXTENSION = ".log";
    private static final int MAX_REPORTS = 10;
    private static final int MAX_ISSUES = 1000;

    private ScriptErrorReport() {
    }

    static void write(File scriptsDirectory, String attempt) {
        File instance = instanceDirectory(scriptsDirectory);
        if (instance == null) {
            BetaMoonCommon.LOGGER.warning("Cannot locate the instance directory for script diagnostics.");
            return;
        }
        Path root = BetaMoonDirectories.logsDirectoryForInstance(instance).toPath();
        Path temporary = null;
        try {
            Files.createDirectories(root);
            temporary = Files.createTempFile(root, ".script_err_", ".tmp");
            StringBuilder report = new StringBuilder();
            report.append("BetaMoon script diagnostics\n");
            report.append("Version: ").append(BetaMoonCommon.VERSION).append('\n');
            report.append("Attempt: ").append(attempt).append('\n');
            report.append("Generated: ").append(LocalDateTime.now()).append("\n\n");
            report.append("Script errors and warnings\n");
            List<LuaScriptErrors.ScriptIssue> issues = LuaScriptErrors.getEntries();
            for (int index = 0; index < Math.min(issues.size(), MAX_ISSUES); index++) {
                LuaScriptErrors.ScriptIssue issue = issues.get(index);
                report.append(issue.isWarning() ? "WARNING " : "ERROR ")
                        .append(issue.getMessage()).append('\n');
            }
            if (issues.size() > MAX_ISSUES) {
                report.append("... ").append(issues.size() - MAX_ISSUES).append(" more issues omitted\n");
            }
            report.append("\nDiscovered scripts\n");
            for (ScriptMod mod : LuaScriptRegistry.getEntries()) {
                report.append(mod.getSourceFileName()).append(" | ").append(mod.getDisplayName()).append(" | ")
                        .append(mod.isFailed() ? "failed" : mod.isLoaded() ? "loaded" : "pending").append('\n');
            }
            Files.write(temporary, report.toString().getBytes(StandardCharsets.UTF_8));
            Path destination = unique(root, FILE_PREFIX + NAME_TIME.format(LocalDateTime.now()) + FILE_EXTENSION);
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
            trim(root);
            BetaMoonCommon.LOGGER.info("Script error log written to " + destination.toAbsolutePath());
        } catch (IOException | SecurityException error) {
            BetaMoonCommon.LOGGER.warning("Could not export script diagnostics: " + error.getMessage());
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static File instanceDirectory(File scriptsDirectory) {
        if (scriptsDirectory == null) {
            return null;
        }
        File parent = scriptsDirectory.getParentFile();
        if ("lua_scripts".equals(scriptsDirectory.getName())) {
            return parent;
        }
        if ("scripts".equals(scriptsDirectory.getName()) && parent != null
                && "betamoon".equals(parent.getName())) {
            return parent.getParentFile();
        }
        return null;
    }

    private static Path unique(Path root, String name) {
        Path candidate = root.resolve(name);
        for (int suffix = 2; Files.exists(candidate); suffix++) {
            candidate = root.resolve(name.substring(0, name.length() - FILE_EXTENSION.length()) + "-"
                    + String.format(Locale.ROOT, "%04d", suffix) + FILE_EXTENSION);
        }
        return candidate;
    }

    private static void trim(Path root) throws IOException {
        List<Path> reports = new ArrayList<Path>();
        try (Stream<Path> files = Files.list(root)) {
            files.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().startsWith(FILE_PREFIX)
                    && path.getFileName().toString().endsWith(FILE_EXTENSION)).forEach(reports::add);
        }
        reports.sort(Comparator.comparing(path -> path.getFileName().toString()));
        for (int index = 0; index < reports.size() - MAX_REPORTS; index++) {
            Files.deleteIfExists(reports.get(index));
        }
    }
}
