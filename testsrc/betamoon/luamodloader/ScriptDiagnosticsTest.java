package betamoon.luamodloader;

import betamoon.config.BetaMoonConfig;
import betamoon.luamodloader.LuaScriptErrors.Outcome;
import betamoon.luamodloader.LuaScriptErrors.ScriptIssue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Tests occurrence snapshots separately from later, best-effort environment collection. */
final class ScriptDiagnosticsTest {
    private ScriptDiagnosticsTest() {
    }

    static void run() throws Exception {
        Path configuration = Files.createTempDirectory("betamoon-report-config-");
        LuaScriptErrors.clear();
        LuaScriptRegistry.clear();
        String[] dimension = {"example:dimension/flat"};
        ScriptDiagnostics.initializeClient(new ScriptDiagnostics.ClientContext() {
            public Map<String, String> occurrence() {
                return Collections.singletonMap("Dimension", dimension[0]);
            }

            public Map<String, List<String>> environment() {
                return Collections.singletonMap("Game", Collections.singletonList("Dimension: " + dimension[0]));
            }
        });
        try {
            ScriptDiagnostics.configure(new BetaMoonConfig(configuration.toFile()));
            ScriptMod mod = LuaScriptRegistry.updateParsed("pack/main.lua", "Pack", Arrays.asList("Other Pack"),
                    LuaValue.NIL, LuaValue.NIL, LuaValue.NIL, "", "2.3.4", null);
            LuaScriptRegistry.markLoadedByFile(mod.getSourceFileName());
            ScriptIssue issue;
            RuntimeException caught = null;
            try (ScriptDiagnostics.Scope phase = ScriptDiagnostics.scope("runtime", "outer operation")) {
                try {
                    ScriptExecution.invoke(mod.getSourceFileName(), "events.world_leave", new ZeroArgFunction() {
                        @Override
                        public LuaValue call() {
                            throw new IllegalStateException("callback failed", new IOException("underlying cause"));
                        }
                    }, LuaValue.NONE);
                } catch (RuntimeException error) {
                    caught = error;
                }
            }
            dimension[0] = "minecraft:overworld";
            require(caught != null, "Test callback should have failed");
            LuaScriptRegistry.markFailedByFile(mod.getSourceFileName(), "A later failure");
            issue = LuaScriptErrors.add("Pack", "callback failed", Outcome.CALLBACK_DISABLED, caught);
            require(issue.getOccurrence().operation.equals("events.world_leave")
                    && issue.getOccurrence().phase.equals("runtime")
                    && issue.getOccurrence().game.get("Dimension").equals("example:dimension/flat"),
                    "Callback context must survive scope unwinding and a subsequent world change");
            require(issue.getScriptVersion().equals("2.3.4") && issue.getScriptState().equals("loaded")
                    && issue.getStackTrace().contains("underlying cause"),
                    "Issue snapshots must retain script identity, state, and available exception causes");
            require(ScriptIssueReports.outcome(issue).equals("This callback was disabled.")
                    && issue.getScriptState().equals("loaded"), "Later failures must not rewrite an old outcome");
            ScriptIssue warning = LuaScriptErrors.addWarning("Pack", "A useful warning");
            String report = ScriptIssueReports.generate(Arrays.asList(warning, issue), "Test report");
            require(report.contains("Report format: 1") && report.contains("Generated:")
                    && report.contains("Errors: 1") && report.contains("Warnings: 1")
                    && report.indexOf("#1 ERROR") < report.indexOf("#2 WARNING"),
                    "Report header, counts, and stable severity ordering must be explicit");
            require(report.contains("Heap maximum (bytes)") && report.contains("java.version:")
                    && report.contains("Relevant settings") && report.contains("scripts.reloadDebounceMs: 500")
                    && report.contains("Missing dependencies:") && report.contains("Restart required for reload:")
                    && report.contains("Agent status:") && report.contains("Client diagnostics"),
                    "Full reports must include the agreed runtime, script, configuration, and agent diagnostics");
            require(report.contains("Dimension: example:dimension/flat") && report.contains("Dimension: minecraft:overworld"),
                    "Report must distinguish occurrence-time context from the environment at export");
            require(!report.contains(configuration.toString()) && !report.contains("user.home:")
                    && !report.contains("World seed:") && !report.contains("Player coordinates:"),
                    "Metadata collection must avoid local directories and world/player private data");
            lazyWorldContext();
            ScriptDiagnostics.initializeClient(new ScriptDiagnostics.ClientContext() {
                public Map<String, String> occurrence() {
                    throw new IllegalStateException("Context unavailable");
                }

                public Map<String, List<String>> environment() {
                    throw new AssertionError("An optional mod's metadata getter failed");
                }
            });
            ScriptIssue unavailable = LuaScriptErrors.add("Pack", "Still report this error");
            int count = LuaScriptErrors.getEntries().size();
            String partial = ScriptIssueReports.generate(Arrays.asList(unavailable), "Unavailable optional metadata");
            require(partial.contains("Still report this error") && partial.contains("unavailable")
                    && LuaScriptErrors.getEntries().size() == count,
                    "Optional metadata failures must preserve issue output without recursive diagnostics");
            String bounded = ScriptIssueReports.generate(Arrays.asList(issue, warning), "Automatic test", 1);
            require(bounded.contains("1 additional issues omitted") && bounded.contains("Errors: 1")
                    && bounded.contains("Warnings: 1"), "Automatic report limits must clearly disclose omitted issues");
        } finally {
            ScriptDiagnostics.initializeClient(null);
            ScriptDiagnostics.configure(null);
            LuaScriptErrors.clear();
            LuaScriptRegistry.clear();
            try {
                Files.deleteIfExists(configuration.resolve(BetaMoonConfig.FILE_NAME));
                Files.delete(configuration);
            } catch (IOException lockedByLegacyConfiguration) {
                configuration.toFile().deleteOnExit();
                configuration.resolve(BetaMoonConfig.FILE_NAME).toFile().deleteOnExit();
            }
        }
        System.out.println("Script diagnostic reports passed: historical context, causes, full metadata, "
                + "lazy callback world context and optional collector failures.");
    }

    private static void lazyWorldContext() {
        int[] reads = {0};
        try (ScriptDiagnostics.Scope phase = ScriptDiagnostics.scope("reload", "validation");
                ScriptDiagnostics.Scope world = ScriptDiagnostics.gameContext(() -> {
                    reads[0]++;
                    return Collections.singletonMap("Dimension", "departing world");
                })) {
            require(reads[0] == 0, "World diagnostic suppliers must not execute during normal callback dispatch");
            ScriptIssue issue = LuaScriptErrors.add("Pack", "Failure during validation");
            require(reads[0] == 1 && issue.getOccurrence().phase.equals("reload")
                    && issue.getOccurrence().game.get("Dimension").equals("departing world"),
                    "A callback's explicit world context should be captured lazily at failure");
        }
        ScriptIssue after = LuaScriptErrors.add("Pack", "After callback");
        require(after.getOccurrence().phase.equals("runtime")
                && !after.getOccurrence().game.containsValue("departing world"),
                "Diagnostic scopes must restore their prior context");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
