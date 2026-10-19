package betamoon.gui;

import betamoon.gui.framework.GuiTheme;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptErrors.Outcome;
import betamoon.luamodloader.LuaScriptErrors.ScriptIssue;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptMod;
import java.awt.AWTError;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import org.luaj.vm2.LuaValue;
import net.minecraft.src.GuiMainMenu;
import net.minecraft.src.GuiScreen;

/** Diagnostic association and desktop actions are checked without changing the user's clipboard or opening files. */
final class ScriptIssuesTest {
    static void run() throws Exception {
        LuaScriptErrors.clear();
        LuaScriptRegistry.clear();
        try {
            severityAndText();
            screenNames();
            actions();
        } finally {
            LuaScriptErrors.clear();
            LuaScriptRegistry.clear();
        }
        System.out.println("Script issues passed: runtime severity, compact text, stable ordering, "
                + "full copy/export and failure feedback.");
    }

    private static void severityAndText() {
        ScriptMod script = LuaScriptRegistry.updateParsed("my pack/main.lua", "My Pack", Collections.emptyList(),
                LuaValue.NIL, LuaValue.NIL, LuaValue.NIL, "", "1.0", null);
        LuaScriptRegistry.markLoadedByFile("my pack/main.lua");
        long revision = LuaScriptErrors.getRevision();
        require(GuiPanelScriptList.nameColor(script, GuiTheme.DEFAULT) == GuiTheme.DEFAULT.textPrimary,
                "Healthy script should use its normal name color");
        ScriptIssue warning = LuaScriptErrors.addWarning("My Pack", "A useful warning");
        require(GuiPanelScriptList.nameColor(script, GuiTheme.DEFAULT) == GuiTheme.DEFAULT.textWarning,
                "Warnings should tint the name");
        ScriptIssue runtime = LuaScriptErrors.add("my pack/main.lua",
                "GUI p / element a: @my pack/helpers/visual.lua:42 mutation failed", Outcome.BINDING_SUSPENDED);
        require(!script.isFailed() && GuiPanelScriptList.nameColor(script, GuiTheme.DEFAULT) == GuiTheme.DEFAULT.textError,
                "A runtime error must tint the name red even when the script remains loaded");
        require(runtime.getSourceFile().equals("my pack/helpers/visual.lua") && runtime.getLine() == 42,
                "Module paths containing spaces must retain their full source location");
        require(GuiScriptIssueText.title(runtime).equals("My Pack"), "Runtime issue must keep its owning script");
        require(GuiScriptIssueText.outcome(runtime).contains("suspended"), "Known binding outcome was lost");
        require(GuiScriptIssueText.message(runtime).equals("GUI p / element a: mutation failed"),
                "Engine context should remain while a repeated leading source frame is shown only once");
        LuaScriptErrors.ignore();
        require(GuiPanelScriptList.nameColor(script, GuiTheme.DEFAULT) == GuiTheme.DEFAULT.textError,
                "Dismissing a report must not silently remove its recorded errors");
        ScriptIssue one = LuaScriptErrors.add("My Pack", "@my pack/main.lua:8 first\nsecond\nstack traceback:\n\tframe");
        ScriptIssue two = LuaScriptErrors.add("My Pack", "@my pack/main.lua:9 another", Outcome.CALLBACK_DISABLED);
        require(GuiScriptIssueText.message(one).equals("first\nsecond\nstack traceback:\n\tframe"),
                "Only duplicated leading metadata should be removed from the UI");
        require(GuiScriptIssueText.diagnostic(one).contains(one.getMessage()),
                "Copied text must retain original diagnostics");
        require(GuiScriptIssueText.ordered(Arrays.asList(warning, one, two)).equals(Arrays.asList(one, two, warning)),
                "Errors must precede warnings while preserving order within each severity");
        require(GuiScriptIssueText.outcome(two).equals("This callback was disabled."), "Callback outcome must be explicit");
        LuaScriptRegistry.markFailedByFile("my pack/main.lua", "failed");
        require(GuiScriptIssueText.outcome(one).equals("Script remains loaded."),
                "An old diagnostic's outcome must not change after a later script failure");
        require(LuaScriptErrors.getRevision() > revision, "New diagnostics must invalidate retained views");
        LuaScriptErrors.clear();
        LuaScriptRegistry.markLoadedByFile("my pack/main.lua");
        require(GuiPanelScriptList.nameColor(script, GuiTheme.DEFAULT) == GuiTheme.DEFAULT.textPrimary,
                "Clearing diagnostics after recovery must restore the name color");
    }

    private static void actions() throws Exception {
        Path root = Files.createTempDirectory("betamoon-issue-actions-");
        String[] copied = {null};
        Path[] opened = {null};
        long[] time = {1000};
        GuiScriptIssueActions actions = new GuiScriptIssueActions(value -> copied[0] = value,
                path -> opened[0] = path, () -> root, () -> time[0]);
        ScriptIssue warning = ScriptIssue.describe("warn.lua", "caf\u00e9 warning", true);
        ScriptIssue error = ScriptIssue.describe("bad.lua", "@bad.lua:10 full message\n\tstack frame", false);
        actions.copy(error);
        require(copied[0].contains(error.getMessage()) && copied[0].contains("\tstack frame"),
                "Individual copy must contain the untruncated diagnostic");
        require(actions.feedback().equals("Copied to clipboard"), "Copy feedback missing");
        time[0] += 3000;
        require(actions.feedback().isEmpty(), "Copy confirmation must expire");
        actions.copyAll(Arrays.asList(warning, error));
        String report = copied[0];
        require(report.startsWith("ERROR | ") && report.indexOf("ERROR") < report.indexOf("WARNING")
                && report.contains(warning.getMessage()) && !report.contains("Runtime environment")
                && !report.contains("Version: "), "Copy all must contain only the formatted issue list");
        Path first = actions.export(Arrays.asList(warning, error));
        require(first != null && first.getParent().equals(root) && first.toString().endsWith(".txt"),
                "Manual export must produce a report in the selected destination");
        String saved = new String(Files.readAllBytes(first), StandardCharsets.UTF_8);
        require(saved.contains("Runtime environment") && saved.contains("Discovered scripts")
                && saved.contains("Heap maximum (bytes)") && saved.contains(warning.getMessage())
                && saved.contains(error.getMessage()) && !saved.equals(report),
                "Saved UTF-8 reports must include diagnostics in addition to the complete issue messages");
        actions.openReport();
        require(first.equals(opened[0]), "Open report must target the actual exported file");
        actions.export(Arrays.asList(warning, error));
        Path second = actions.report();
        require(!first.equals(second) && Files.exists(first), "Export must never overwrite an earlier report");
        require(actions.reportLinkVisible(), "A successful export should offer the report link");
        actions.copy(error);
        require(!actions.reportLinkVisible() && actions.feedback().equals("Copied to clipboard"),
                "A saved report must not obscure a later copy confirmation");
        time[0] += 3000;
        require(actions.reportLinkVisible(), "Report access should return after transient copy feedback");

        GuiScriptIssueActions unavailable = new GuiScriptIssueActions(value -> { throw new IOException("Clipboard busy"); },
                path -> { throw new IOException("No desktop"); }, () -> first, () -> time[0]);
        unavailable.copy(error);
        require(unavailable.failed() && unavailable.feedback().equals("Copy failed"), "Clipboard failure was hidden");
        require(unavailable.export(Collections.singletonList(error)) == null,
                "A failed export must not offer a successful result popup");
        require(unavailable.failed() && unavailable.report() == null && unavailable.feedback().equals("Export failed"),
                "Export failure must retain the popup and report its cause");
        require(LuaScriptErrors.getErrorCount() == 0, "Desktop failures must not create recursive script errors");
        GuiScriptIssueActions noDesktop = new GuiScriptIssueActions(value -> { throw new AWTError("No display"); },
                path -> { throw new IOException("No desktop"); }, () -> root, () -> time[0]);
        noDesktop.copy(error);
        require(noDesktop.failed(), "Native clipboard initialization failures must not escape into the GUI");
        noDesktop.export(Collections.singletonList(error));
        Path third = noDesktop.report();
        noDesktop.openReport();
        require(noDesktop.failed() && noDesktop.feedback().equals("Could not open report") && Files.exists(third),
                "An unavailable desktop must keep the exported report and provide feedback");
        Files.delete(third);
        Files.delete(second);
        Files.delete(first);
        Files.delete(root);
    }

    private static void screenNames() throws Exception {
        TinyMappingResolver mappings;
        try (InputStream input = Files.newInputStream(Paths.get("tools/rmcp/conf/mappings.tiny"))) {
            mappings = TinyMappingResolver.read(input);
        }
        require(GuiScreenNames.namedClassName("fu", mappings).equals("net.minecraft.src.GuiMainMenu"),
                "Report screen identity must resolve fu to GuiMainMenu");
        require(GuiScreenNames.namedClassName("net.minecraft.src.GuiMainMenu", mappings)
                .equals("net.minecraft.src.GuiMainMenu"), "Development screen names must remain readable");
        require(GuiScreenNames.namedClassName("custom.mod.Screen", mappings).equals("custom.mod.Screen"),
                "A mod's own screen class must not be mistaken for a native class");
        require(GuiScreenNames.namedClassName("fu", null).equals("fu"),
                "Unavailable mapping data must preserve the runtime identity instead of breaking report creation");
        require(GuiScreenNames.name(null).equals("none"), "Gameplay without an open screen must remain explicit");
        require(GuiScreenNames.name(new GuiMainMenu()).equals("GuiMainMenu"),
                "The report must use the canonical vanilla screen name");
        GuiScreen anonymous = new GuiScreen() { };
        require(!GuiScreenNames.name(anonymous).isEmpty(), "Anonymous custom screens must keep an identifiable name");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
