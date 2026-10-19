package betamoon.luamodloader;

import betamoon.BetaMoonCommon;
import betamoon.logging.RuntimeLogs;
import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.instrumentation.diagnostics.HookDiagnostic;
import betamoon.instrumentation.diagnostics.HookStatus;
import betamoon.luamodloader.LuaScriptErrors.ScriptIssue;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Shared report content for explicit saves and automatic load-failure diagnostics. */
public final class ScriptIssueReports {
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_SUPPORTING_ENTRIES = 1000;

    private ScriptIssueReports() {
    }

    public static String generate(List<ScriptIssue> issues, String scope) {
        return generate(issues, scope, Integer.MAX_VALUE);
    }

    static String generate(List<ScriptIssue> issues, String scope, int maximumIssues) {
        List<ScriptIssue> ordered = new ArrayList<>(issues);
        ordered.sort(Comparator.comparing(ScriptIssue::isWarning));
        long warnings = ordered.stream().filter(ScriptIssue::isWarning).count();
        StringBuilder text = new StringBuilder("BetaMoon script issue report\n");
        field(text, "Report format", FORMAT_VERSION);
        field(text, "Generated", timestamp(System.currentTimeMillis()));
        field(text, "Scope", scope);
        field(text, "Errors", ordered.size() - warnings);
        field(text, "Warnings", warnings);
        field(text, "Version", BetaMoonCommon.VERSION);

        section(text, "Issues", () -> {
            int count = Math.min(ordered.size(), maximumIssues);
            for (int index = 0; index < count; index++) {
                appendIssue(text, ordered.get(index), index + 1);
            }
            if (count < ordered.size()) {
                text.append(ordered.size() - count).append(" additional issues omitted by automatic report limit.\n");
            }
            if (ordered.isEmpty()) {
                text.append("None recorded.\n");
            }
        });
        section(text, "Runtime environment", () -> environment(text));
        section(text, "Discovered scripts", () -> scripts(text));
        section(text, "Relevant settings", () -> fields(text, ScriptDiagnostics.settings()));
        section(text, "Instrumentation", () -> instrumentation(text));
        section(text, "Client diagnostics", () -> {
            for (Map.Entry<String, List<String>> entry : ScriptDiagnostics.clientEnvironment().entrySet()) {
                text.append(entry.getKey()).append('\n');
                int count = Math.min(entry.getValue().size(), MAX_SUPPORTING_ENTRIES);
                for (int index = 0; index < count; index++) {
                    text.append("  ").append(entry.getValue().get(index)).append('\n');
                }
                omitted(text, entry.getValue().size(), count);
            }
        });
        section(text, "Recent log entries", () -> {
            List<String> entries = RuntimeLogs.recent();
            if (entries.isEmpty()) {
                text.append("None recorded at the configured log levels.\n");
            }
            for (String entry : entries) {
                text.append(entry);
            }
        });
        return text.toString();
    }

    private static void appendIssue(StringBuilder text, ScriptIssue issue, int number) {
        text.append('#').append(number).append(' ').append(issue.isWarning() ? "WARNING" : "ERROR")
                .append(" | ").append(issue.getScriptDisplayName()).append('\n');
        ScriptDiagnostics.Occurrence occurrence = issue.getOccurrence();
        field(text, "Occurred", timestamp(occurrence.timestampMillis));
        field(text, "JVM uptime at occurrence (ms)", occurrence.uptimeMillis);
        field(text, "Phase", occurrence.phase);
        field(text, "Operation / callback", occurrence.operation);
        field(text, "Thread", occurrence.thread);
        field(text, "Owner entrypoint", issue.getScriptSource());
        field(text, "Script version at occurrence", issue.getScriptVersion());
        field(text, "Script state at occurrence", issue.getScriptState());
        field(text, "Source", issue.getSourceFile());
        if (issue.getLine() > 0) {
            field(text, "Line", issue.getLine());
        }
        field(text, "Recovery outcome", outcome(issue));
        fields(text, occurrence.game);
        text.append("Message:\n").append(issue.getMessage()).append('\n');
        if (!issue.getStackTrace().isEmpty()) {
            text.append("Exception and causes:\n").append(issue.getStackTrace());
        }
        text.append('\n');
    }

    /** Occurrence-time outcome; never recomputed from a later registry state. */
    public static String outcome(ScriptIssue issue) {
        switch (issue.getOutcome()) {
            case CALLBACK_DISABLED:
                return "This callback was disabled.";
            case BINDING_SUSPENDED:
                return "This binding was suspended; other elements remain active.";
            case APPEARANCE_RETAINED:
                return "The last valid appearance was retained.";
            case LOAD_FAILED:
                return "Script failed to load.";
            case ACTIVE_SCRIPTS_RETAINED:
                return "Reload was rejected; the active scripts were retained.";
            default:
                return "failed".equals(issue.getScriptState()) ? "Script failed to load."
                        : "loaded".equals(issue.getScriptState()) ? "Script remains loaded." : "unavailable";
        }
    }

    private static void environment(StringBuilder text) {
        field(text, "Minecraft", "Beta 1.7.3");
        field(text, "BetaMoon", BetaMoonCommon.VERSION);
        field(text, "BetaMoon build", ScriptIssueReports.class.getPackage().getImplementationVersion());
        field(text, "JVM uptime (ms)", ScriptDiagnostics.uptime());
        String[] properties = {"java.version", "java.vendor", "java.vm.name", "java.vm.version",
                "os.name", "os.version", "os.arch", "sun.arch.data.model"};
        for (String property : properties) {
            try {
                field(text, property, System.getProperty(property));
            } catch (SecurityException failure) {
                field(text, property, "unavailable");
            }
        }
        Runtime runtime = Runtime.getRuntime();
        long allocated = runtime.totalMemory();
        field(text, "Heap used (bytes)", allocated - runtime.freeMemory());
        field(text, "Heap allocated (bytes)", allocated);
        field(text, "Heap maximum (bytes)", runtime.maxMemory());
    }

    private static void scripts(StringBuilder text) {
        List<LuaScriptRegistry.Description> scripts = new ArrayList<>(LuaScriptRegistry.snapshot());
        scripts.sort(Comparator.comparing(value -> value.sourceFileName));
        field(text, "Discovered scripts", scripts.size());
        int count = Math.min(scripts.size(), MAX_SUPPORTING_ENTRIES);
        for (int index = 0; index < count; index++) {
            LuaScriptRegistry.Description script = scripts.get(index);
            text.append('\n').append(script.displayName).append('\n');
            field(text, "Version", script.version);
            field(text, "Source", script.sourceFileName);
            field(text, "Entrypoint", script.entrypointPath);
            field(text, "Package format", script.packageLayout);
            field(text, "State", script.failed ? "failed" : script.loaded ? "loaded" : "pending");
            field(text, "Dependencies", script.dependencies.isEmpty() ? "none" : script.dependencies);
            field(text, "Missing dependencies",
                    script.missingDependencies.isEmpty() ? "none" : script.missingDependencies);
            field(text, "Reload callback", script.supportsReload);
            field(text, "Unload callback", script.supportsUnload);
            String restart = NonReloadableScriptRegistry.reason(script.sourceFileName);
            field(text, "Restart required for reload", restart == null ? "no" : restart);
            if (script.failureReason != null) {
                field(text, "Load failure", script.failureReason);
            }
        }
        omitted(text, scripts.size(), count);
    }

    private static void instrumentation(StringBuilder text) {
        field(text, "Agent status", AgentRuntime.getStatus());
        if (AgentRuntime.getFailureMessage() != null) {
            field(text, "Agent failure", AgentRuntime.getFailureMessage());
        }
        List<HookDiagnostic> hooks = AgentRuntime.getReport().snapshot();
        int failures = 0;
        int requiredFailures = 0;
        for (HookDiagnostic hook : hooks) {
            if (hook.getStatus() != HookStatus.FAILED && hook.getStatus() != HookStatus.NO_MATCH) {
                continue;
            }
            failures++;
            if (hook.isRequired()) {
                requiredFailures++;
            }
            if (failures <= MAX_SUPPORTING_ENTRIES) {
                text.append("Hook: ").append(hook.getHookId()).append(" | ")
                        .append(hook.isRequired() ? "required" : "optional").append(" | ")
                        .append(hook.getStatus()).append('\n');
                field(text, "Target", hook.getTarget());
                field(text, "Match requirement", hook.getMatchRequirement());
                field(text, "Failure", hook.getMessage());
            }
        }
        field(text, "Failed hooks", failures);
        field(text, "Failed required hooks", requiredFailures);
        omitted(text, failures, Math.min(failures, MAX_SUPPORTING_ENTRIES));
    }

    private static void section(StringBuilder text, String title, Runnable append) {
        text.append("\n=== ").append(title).append(" ===\n");
        try {
            append.run();
        } catch (Throwable failure) {
            ScriptDiagnostics.rethrowFatal(failure);
            text.append("Additional diagnostics unavailable (")
                    .append(failure.getClass().getSimpleName()).append(").\n");
        }
    }

    private static void fields(StringBuilder text, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            field(text, entry.getKey(), entry.getValue());
        }
    }

    private static void field(StringBuilder text, String label, Object value) {
        text.append(label).append(": ").append(value == null ? "unavailable" : value).append('\n');
    }

    private static void omitted(StringBuilder text, int total, int shown) {
        if (shown < total) {
            text.append(total - shown).append(" additional supporting entries omitted.\n");
        }
    }

    private static String timestamp(long millis) {
        return Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toString();
    }
}
