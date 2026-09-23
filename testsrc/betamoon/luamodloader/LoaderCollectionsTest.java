package betamoon.luamodloader;

import betamoon.luaapi.BetaMoonModule;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Exercises ownership, cleanup, dependency ordering, and registry snapshot
 * contracts.
 */
public final class LoaderCollectionsTest {
    private LoaderCollectionsTest() {
    }

    public static void main(String[] arguments) {
        try {
            verifyRegistrySnapshots();
            verifyCleanupOrderAndFailures();
            verifyIdentityOwnership();
            verifyRetainedContent();
            verifyDependencyOrdering();
            verifyDependencyFailures();
            verifyScriptParsing();
            verifyLifecycleOwnership();
            verifyModuleLifecycle();
            verifyUnloadOwnership();
            verifyExecutionScopeRestoration();
            verifySourcePreflight();
            verifyRetainedScripts();
            verifyLoaderPhases();
            System.out.println("Loader collections passed: snapshots, cleanup, dependencies, lifecycle and sources.");
        } finally {
            ScriptResourceTracker.unloadAll();
            LuaScriptRegistry.setCurrentScriptFile(null);
            LuaScriptRegistry.clear();
            LuaScriptErrors.clear();
        }
    }

    private static void verifyRegistrySnapshots() {
        LuaScriptRegistry.clear();
        ScriptMod original = script("snapshot", Collections.emptyList());
        require(LuaScriptRegistry.registerFile(original.getSourceFileName()) == original,
                "Registering an existing file must retain its script identity");

        List<ScriptMod> scripts = LuaScriptRegistry.getEntries();
        scripts.clear();
        require(LuaScriptRegistry.getEntries().size() == 1, "Changing a snapshot must not clear the registry");

        LuaScriptErrors.clear();
        LuaScriptErrors.add("snapshot.lua", "snapshot.lua:17: invalid declaration");
        LuaScriptErrors.addWarning("snapshot.lua", "Retained registration");
        List<LuaScriptErrors.ScriptIssue> issues = LuaScriptErrors.getIssuesFor("snapshot", "snapshot.lua");
        require(issues.size() == 2 && issues.get(0).getLine() == 17,
                "Issue snapshots must retain source locations and warnings");
        issues.clear();
        LuaScriptErrors.getEntries().clear();
        require(LuaScriptErrors.getErrorCount() == 1, "Snapshot changes must not remove recorded errors");
        require(LuaScriptErrors.hasWarningFor("snapshot", "snapshot.lua"), "Warnings must remain recorded");
    }

    private static void verifyCleanupOrderAndFailures() {
        List<String> cleaned = new ArrayList<>();
        LuaScriptRegistry.setCurrentScriptFile("cleanup_a.lua");
        ScriptResourceTracker.track(() -> cleaned.add("a:first"));
        ScriptResourceTracker.track(() -> {
            cleaned.add("a:failure");
            throw new IllegalStateException("Expected cleanup failure");
        });
        ScriptResourceTracker.track(() -> cleaned.add("a:last"));

        LuaScriptRegistry.setCurrentScriptFile("cleanup_b.lua");
        ScriptResourceTracker.track(() -> cleaned.add("b"));

        LuaScriptRegistry.setCurrentScriptFile("cleanup_structural.lua");
        NonReloadableScriptRegistry.mark("cleanup_structural.lua", "test structural content");
        ScriptResourceTracker.track(() -> cleaned.add("structural"));
        LuaScriptRegistry.setCurrentScriptFile(null);

        try {
            ScriptResourceTracker.unloadReloadable();
            require(cleaned.equals(Arrays.asList("b", "a:last", "a:failure", "a:first")),
                    "Cleanup must retain reverse owner/registration order and continue after a failure");
            ScriptResourceTracker.unloadReloadable();
            require(cleaned.size() == 4, "Already removed resources must not be cleaned twice");
            ScriptResourceTracker.unloadAll();
            require(cleaned.equals(Arrays.asList("b", "a:last", "a:failure", "a:first", "structural")),
                    "Full unload must also clean retained structural resources");
        } finally {
            NonReloadableScriptRegistry.unmark("cleanup_structural.lua");
        }
    }

    private static void verifyIdentityOwnership() {
        String first = new String("equal values");
        String second = new String("equal values");
        LuaScriptRegistry.setCurrentScriptFile("identity_first.lua");
        ScriptResourceTracker.trackOwned(first);
        LuaScriptRegistry.setCurrentScriptFile("identity_second.lua");
        ScriptResourceTracker.trackOwned(second);
        LuaScriptRegistry.setCurrentScriptFile(null);

        require("identity_first.lua".equals(ScriptResourceTracker.findOwner(first)),
                "Equal objects must retain separate owners by identity");
        require("identity_second.lua".equals(ScriptResourceTracker.findOwner(second)),
                "A second equal object must not overwrite the first object's ownership");
        ScriptResourceTracker.unload("identity_first.lua");
        require(ScriptResourceTracker.findOwner(first) == null, "Unload must remove the owner's association");
        require("identity_second.lua".equals(ScriptResourceTracker.findOwner(second)),
                "Unload must preserve another owner's equal object");
        ScriptResourceTracker.unload("identity_second.lua");
    }

    private static void verifyRetainedContent() {
        Object value = new Object();
        LuaScriptRegistry.setCurrentScriptFile("retained.lua");
        LuaContentRegistry.Entry entry = LuaContentRegistry.remember("test", 1, value, "retained");
        LuaContentRegistry.beginLoadPass();
        require(LuaContentRegistry.find("test", 1) == entry, "Reload lookup must retain content identity");
        require(LuaContentRegistry.finishLoadPass().isEmpty(), "Seen content must not produce a retained warning");

        LuaContentRegistry.beginLoadPass();
        List<String> warnings = LuaContentRegistry.finishLoadPass();
        require(warnings.size() == 1 && warnings.get(0).contains("retained.lua"),
                "An omitted declaration must produce an owner-specific retained warning");
        require("retained.lua".equals(LuaContentRegistry.findOwner(value)),
                "An omitted declaration must retain ownership of its original content");
        LuaScriptRegistry.setCurrentScriptFile(null);
    }

    private static void verifyDependencyOrdering() {
        LuaScriptRegistry.clear();
        LuaScriptErrors.clear();
        ScriptMod base = script("base", Collections.emptyList());
        ScriptMod left = script("left", Collections.singletonList("base"));
        ScriptMod right = script("right", Collections.singletonList("base"));
        ScriptMod top = script("top", Arrays.asList("left", "right"));
        LuaModLoader loader = new LuaModLoader();
        List<String> errors = new ArrayList<>();
        Map<String, ScriptMod> indexed = loader.indexModsByName(Arrays.asList(top, right, left, base), errors);
        List<ScriptMod> ordered = loader.orderMods(indexed, errors);

        require(errors.isEmpty() && ordered.size() == 4, "A diamond dependency graph must schedule each mod once");
        require(ordered.indexOf(base) < ordered.indexOf(left) && ordered.indexOf(base) < ordered.indexOf(right),
                "Shared dependencies must precede both dependents");
        require(ordered.indexOf(left) < ordered.indexOf(top) && ordered.indexOf(right) < ordered.indexOf(top),
                "Both branches must precede their dependent");
    }

    private static void verifyDependencyFailures() {
        LuaScriptRegistry.clear();
        LuaScriptErrors.clear();
        ScriptMod first = script("cycle_first", Collections.singletonList("cycle_second"));
        ScriptMod second = script("cycle_second", Collections.singletonList("cycle_first"));
        ScriptMod missing = script("missing", Collections.singletonList("absent"));
        ScriptMod dependent = script("dependent", Collections.singletonList("missing"));
        ScriptMod independent = script("independent", Collections.emptyList());
        LuaModLoader loader = new LuaModLoader();
        List<String> errors = new ArrayList<>();
        Map<String, ScriptMod> indexed = loader
                .indexModsByName(Arrays.asList(first, second, missing, dependent, independent), errors);
        List<ScriptMod> ordered = loader.orderMods(indexed, errors);

        require(ordered.equals(Collections.singletonList(independent)),
                "Dependency failures must not prevent an independent mod from being scheduled");
        require(first.isFailed() && second.isFailed(), "Every cycle participant must be marked failed");
        require(missing.isFailed() && dependent.isFailed(), "Missing dependencies must fail their dependents");
        require(missing.getMissingDependencies().equals(Collections.singletonList("absent")),
                "Missing dependency names must remain available to the GUI");
        require(!errors.isEmpty(), "Dependency failures must produce diagnostics");
    }

    private static void verifyLifecycleOwnership() {
        LuaScriptRegistry.clear();
        List<String> observations = new ArrayList<>();
        ScriptMod mod = LuaScriptRegistry.updateParsed("lifecycle.lua", "lifecycle", Collections.emptyList(),
                new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        observations.add(LuaScriptRegistry.getCurrentScriptFile());
                        ScriptResourceTracker.track(() -> observations.add("cleaned"));
                        return NIL;
                    }
                }, LuaValue.NIL, LuaValue.NIL, null, null, null);

        new LuaModLoader().runModsInOrder(Collections.singletonList(mod));
        require(mod.isLoaded(), "Successful initialization must mark the script loaded");
        require(observations.equals(Collections.singletonList("lifecycle.lua")),
                "Initialization must execute with the declaring owner");
        require(LuaScriptRegistry.getCurrentScriptFile() == null, "Initialization must clear the execution owner");
        ScriptResourceTracker.unload("lifecycle.lua");
        require(observations.equals(Arrays.asList("lifecycle.lua", "cleaned")),
                "Initialization resources must be associated with their declaring script");
    }

    private static void verifyScriptParsing() {
        LuaScriptRegistry.clear();
        LuaScriptErrors.clear();
        String source = "name = 'parsed'\n" + "dependencies = {'base'}\n" + "description = 'description'\n"
                + "version = '1.2.3'\n" + "image = 'icon.png'\n" + "modInit = function() end\n"
                + "modReload = function() end\n" + "modUnload = function() end\n";
        List<String> errors = new ArrayList<>();
        ScriptMod parsed = new ScriptModParser().parse(new File("parsed.lua"), source, errors);
        require(errors.isEmpty() && parsed != null, "A complete script declaration must parse successfully");
        require("parsed".equals(parsed.getDisplayName())
                && parsed.getDependencies().equals(Collections.singletonList("base")),
                "Parsing must retain the declared name and dependencies");
        require("description".equals(parsed.getDescription()) && "1.2.3".equals(parsed.getVersion())
                && "icon.png".equals(parsed.getImagePath()), "Parsing must retain optional metadata");
        require(parsed.modReload.isfunction() && parsed.modUnload.isfunction(),
                "Parsing must retain optional lifecycle functions");

        LuaScriptRegistry.clear();
        LuaScriptErrors.clear();
        errors.clear();
        ScriptMod invalid = new ScriptModParser().parse(new File("invalid_lifecycle.lua"),
                "name = 'invalid'\nmodInit = function() end\nmodReload = true", errors);
        require(invalid == null && errors.size() == 1 && errors.get(0).contains("modReload must be a function"),
                "A declared lifecycle value must be a function");
        require(LuaScriptRegistry.getCurrentScriptFile() == null,
                "Parsing must release execution ownership after validation failures");
    }

    private static void verifyModuleLifecycle() {
        LuaScriptRegistry.clear();
        LuaScriptErrors.clear();
        List<String> errors = new ArrayList<>();
        String exportingSource = "local public = { answer = 1 }\n"
                + "assert(select('#', betamoon.modules:export('lifecycle_export', public)) == 0)\n"
                + "assert(not pcall(function() betamoon.modules:export('missing_table') end))\n"
                + "name = 'module exporter'\n"
                + "function modInit() assert(package.loaded.lifecycle_export == nil) public.answer = 42 end\n";
        ScriptMod exporter = new ScriptModParser().parse(new File("module_exporter.lua"), exportingSource, errors);
        require(exporter != null && errors.isEmpty(), "A valid module export must parse as pending");

        Globals consumer = JsePlatform.standardGlobals();
        consumer.load(new BetaMoonModule());
        require(!consumer.load("return pcall(function() betamoon.modules:import('lifecycle_export') end)").call()
                .toboolean(), "A pending export must remain private until initialization succeeds");

        List<String> failedMods = new ArrayList<>();
        ScriptLifecycleRunner runner = new ScriptLifecycleRunner(new RetainedScriptCatalog(), errors::add);
        runner.run(Collections.singletonList(exporter), failedMods, false);
        require(failedMods.isEmpty() && exporter.isLoaded(), "A successful exporter must complete its lifecycle");
        require(consumer.load("return betamoon.modules:import('lifecycle_export').answer").call().checkint() == 42,
                "Publishing must expose mutations made during modInit");

        String duplicateSource = "local public = {}\n" + "betamoon.modules:export('lifecycle_export', public)\n"
                + "name = 'duplicate exporter'\n" + "function modInit() end\n";
        ScriptMod duplicate = new ScriptModParser().parse(new File("duplicate_exporter.lua"), duplicateSource, errors);
        require(duplicate == null && contains(errors, "already exported by script 'module_exporter.lua'"),
                "A second script must not replace another script's published export");

        String failingSource = "local public = { visible = true }\n"
                + "betamoon.modules:export('failed_export', public)\n" + "name = 'failing exporter'\n"
                + "function modInit() error('expected failure') end\n";
        ScriptMod failing = new ScriptModParser().parse(new File("failing_exporter.lua"), failingSource, errors);
        require(failing != null, "An exporter with a runtime failure must still parse");
        failedMods.clear();
        runner.run(Collections.singletonList(failing), failedMods, false);
        require(failedMods.equals(Collections.singletonList("failing exporter")),
                "A failed exporter must be reported by lifecycle execution");
        require(!consumer.load("return pcall(function() betamoon.modules:import('failed_export') end)").call()
                .toboolean(), "A failed initialization must discard its pending export");

        ScriptResourceTracker.unload("module_exporter.lua");
        require(!consumer.load("return pcall(function() betamoon.modules:import('lifecycle_export') end)").call()
                .toboolean(), "Unloading a script must remove its published exports");

        verifyInvalidModuleDeclarationCleanup(runner, consumer);
        verifyFailedModuleDependencyCleanup(consumer);
    }

    private static void verifyInvalidModuleDeclarationCleanup(ScriptLifecycleRunner runner, Globals consumer) {
        List<String> errors = new ArrayList<>();
        String invalidSource = "local public = {}\n" + "betamoon.modules:export('reusable_export', public)\n"
                + "name = 'invalid module declaration'\n";
        ScriptMod invalid = new ScriptModParser().parse(new File("invalid_module.lua"), invalidSource, errors);
        require(invalid == null, "A module script without modInit must fail declaration validation");

        String correctedSource = "local public = { corrected = true }\n"
                + "betamoon.modules:export('reusable_export', public)\n" + "name = 'corrected module declaration'\n"
                + "function modInit() end\n";
        ScriptMod corrected = new ScriptModParser().parse(new File("corrected_module.lua"), correctedSource, errors);
        require(corrected != null, "Declaration failure must release its pending module key");
        runner.run(Collections.singletonList(corrected), new ArrayList<>(), false);
        require(consumer.load("return betamoon.modules:import('reusable_export').corrected").call().toboolean(),
                "The corrected module must publish normally");
        ScriptResourceTracker.unload("corrected_module.lua");
    }

    private static void verifyFailedModuleDependencyCleanup(Globals consumer) {
        List<String> errors = new ArrayList<>();
        String blockedSource = "local public = {}\n" + "betamoon.modules:export('dependency_export', public)\n"
                + "name = 'blocked module exporter'\n" + "dependencies = { 'missing module dependency' }\n"
                + "function modInit() end\n";
        ScriptMod blocked = new ScriptModParser().parse(new File("blocked_module.lua"), blockedSource, errors);
        require(blocked != null, "A module with a missing dependency must parse before dependency resolution");

        LuaModLoader loader = new LuaModLoader();
        Map<String, ScriptMod> indexed = loader.indexModsByName(Collections.singletonList(blocked), errors);
        require(loader.orderMods(indexed, errors).isEmpty(), "A missing dependency must prevent initialization");
        loader.discardFailedScriptResources(Collections.singletonList(blocked));

        String replacementSource = "local public = { replacement = true }\n"
                + "betamoon.modules:export('dependency_export', public)\n" + "name = 'replacement module exporter'\n"
                + "function modInit() end\n";
        ScriptMod replacement = new ScriptModParser().parse(new File("replacement_module.lua"), replacementSource,
                errors);
        require(replacement != null, "Dependency failure must release its pending module key");
        loader.runModsInOrder(Collections.singletonList(replacement));
        require(consumer.load("return betamoon.modules:import('dependency_export').replacement").call().toboolean(),
                "A replacement module must publish after failed-dependency cleanup");
        ScriptResourceTracker.unload("replacement_module.lua");
    }

    private static boolean contains(List<String> values, String fragment) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static void verifyUnloadOwnership() {
        LuaScriptRegistry.clear();
        List<String> observations = new ArrayList<>();
        ScriptMod first = scriptWithUnload("unload_first", observations);
        ScriptMod second = scriptWithUnload("unload_second", observations);
        LuaScriptRegistry.markLoadedByFile(first.getSourceFileName());
        LuaScriptRegistry.markLoadedByFile(second.getSourceFileName());

        ScriptLifecycleRunner runner = new ScriptLifecycleRunner(new RetainedScriptCatalog(), observations::add);
        runner.unloadReloadableScripts();
        require(observations.equals(Arrays.asList("unload_second.lua", "unload_first.lua")),
                "Unload callbacks must run in reverse registry order with their declaring owner");
        require(LuaScriptRegistry.getCurrentScriptFile() == null,
                "Unload callbacks must release their execution owner");
    }

    private static void verifyExecutionScopeRestoration() {
        LuaScriptRegistry.setCurrentScriptFile("outer.lua");
        try (ScriptExecutionScope ignored = ScriptExecutionScope.open("inner.lua")) {
            require("inner.lua".equals(LuaScriptRegistry.getCurrentScriptFile()),
                    "An execution scope must install its script owner");
        }
        require("outer.lua".equals(LuaScriptRegistry.getCurrentScriptFile()),
                "Closing a nested execution scope must restore its previous owner");

        try {
            try (ScriptExecutionScope ignored = ScriptExecutionScope.open("failing.lua")) {
                throw new IllegalStateException("expected");
            }
        } catch (IllegalStateException expected) {
            require("expected".equals(expected.getMessage()), "The scope must not replace callback failures");
        }
        require("outer.lua".equals(LuaScriptRegistry.getCurrentScriptFile()),
                "An exceptional exit must restore the previous execution owner");
        LuaScriptRegistry.setCurrentScriptFile(null);
    }

    private static void verifySourcePreflight() {
        File directory = null;
        try {
            directory = Files.createTempDirectory("betamoon-loader-test").toFile();
            File valid = new File(directory, "valid.lua");
            File invalid = new File(directory, "invalid.lua");
            File ignored = new File(directory, "ignored.txt");
            Files.write(valid.toPath(), Collections.singletonList("return true"), StandardCharsets.UTF_8);
            Files.write(invalid.toPath(), Collections.singletonList("function("), StandardCharsets.UTF_8);
            Files.write(ignored.toPath(), Collections.singletonList("function("), StandardCharsets.UTF_8);

            LuaScriptFiles sources = new LuaScriptFiles();
            List<LuaScriptFiles.PreflightFailure> failures = sources.preflight(directory);
            require(failures.size() == 1 && "invalid.lua".equals(failures.get(0).getFileName()),
                    "Preflight must compile every Lua file and ignore unrelated files");

            long fingerprint = sources.fingerprint(directory);
            Files.write(valid.toPath(), Collections.singletonList("return false -- changed"), StandardCharsets.UTF_8);
            require(fingerprint != sources.fingerprint(directory),
                    "A source content metadata change must alter the reload fingerprint");
        } catch (IOException error) {
            throw new AssertionError("Could not create loader source fixtures", error);
        } finally {
            deleteFlatDirectory(directory);
        }
    }

    private static void verifyRetainedScripts() {
        LuaScriptRegistry.clear();
        LuaScriptErrors.clear();
        ScriptMod active = script("retained_script", Collections.emptyList());
        RetainedScriptCatalog retained = new RetainedScriptCatalog();
        NonReloadableScriptRegistry.mark(active.getSourceFileName(), "test structural content");
        try {
            LuaScriptRegistry.markLoadedByFile(active.getSourceFileName());
            retained.rememberIfStructural(active);
            LuaScriptRegistry.clear();
            List<ScriptMod> mods = new ArrayList<>();
            require(retained.appendActiveScript(active.getSourceFileName(), mods),
                    "An existing structural script must be restored during reload");
            require(mods.equals(Collections.singletonList(active)) && active.isLoaded(),
                    "A restored structural script must keep its identity and loaded status");
            require(LuaScriptErrors.hasWarningFor(active.getDisplayName(), active.getSourceFileName()),
                    "A restored structural script must explain why its source was skipped");

            LuaScriptRegistry.clear();
            LuaScriptErrors.clear();
            mods.clear();
            retained.appendMissingScripts(Collections.emptySet(), mods);
            require(mods.equals(Collections.singletonList(active)) && active.isLoaded(),
                    "A deleted structural script must remain active until restart");
            require(LuaScriptErrors.hasWarningFor(active.getDisplayName(), active.getSourceFileName()),
                    "A deleted structural script must report its retained state");
        } finally {
            NonReloadableScriptRegistry.unmark(active.getSourceFileName());
        }
    }

    private static void verifyLoaderPhases() {
        require(!LoaderPhase.IDLE.isBusy() && !LoaderPhase.IDLE.isHotReload(),
                "The idle loader phase must permit new work");
        require(LoaderPhase.INITIAL_LOAD.isBusy() && !LoaderPhase.INITIAL_LOAD.isHotReload(),
                "Initial loading must be busy without reload behavior");
        require(LoaderPhase.RELOAD_PREFLIGHT.isBusy() && !LoaderPhase.RELOAD_PREFLIGHT.isHotReload(),
                "Reload preflight must retain active scripts without enabling reload execution");
        require(LoaderPhase.HOT_RELOAD.isBusy() && LoaderPhase.HOT_RELOAD.isHotReload(),
                "Hot reload execution must enable retained-script behavior");
    }

    private static void deleteFlatDirectory(File directory) {
        if (directory == null) {
            return;
        }
        File[] files = directory.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                try {
                    Files.deleteIfExists(files[i].toPath());
                } catch (IOException ignored) {
                    // Best-effort cleanup of test fixtures.
                }
            }
        }
        try {
            Files.deleteIfExists(directory.toPath());
        } catch (IOException ignored) {
            // Best-effort cleanup of test fixtures.
        }
    }

    private static ScriptMod script(String name, List<String> dependencies) {
        return LuaScriptRegistry.updateParsed(name + ".lua", name, dependencies, LuaValue.NIL, LuaValue.NIL,
                LuaValue.NIL, null, null, null);
    }

    private static ScriptMod scriptWithUnload(String name, List<String> observations) {
        return LuaScriptRegistry.updateParsed(name + ".lua", name, Collections.emptyList(), LuaValue.NIL, LuaValue.NIL,
                new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        observations.add(LuaScriptRegistry.getCurrentScriptFile());
                        return NIL;
                    }
                }, null, null, null);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
