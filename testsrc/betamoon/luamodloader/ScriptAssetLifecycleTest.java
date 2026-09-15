package betamoon.luamodloader;

import betamoon.assets.AssetDefinition;
import betamoon.assets.AssetId;
import betamoon.assets.AssetKey;
import betamoon.assets.AssetKind;
import betamoon.assets.AssetPath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

/**
 * Exercises the actual loader publication and cleanup boundaries for asset
 * metadata.
 */
public final class ScriptAssetLifecycleTest {
    private ScriptAssetLifecycleTest() {
    }

    public static void main(String[] arguments) {
        try {
            verifyPublicationAndUnload();
            verifyFailureAndScopeCleanup();
            verifyReloadAndOwnership();
            verifyReloadFailure();
            System.out.println("Script asset lifecycle passed: publication, failure, unload, reload and ownership.");
        } finally {
            NonReloadableScriptRegistry.unmark("asset_owner.lua");
            ScriptResourceTracker.unloadAll();
            LuaScriptRegistry.setCurrentScriptFile(null);
            LuaScriptRegistry.clear();
            LuaScriptErrors.clear();
        }
    }

    private static void verifyPublicationAndUnload() {
        AssetDefinition definition = texture("mymod:successful");
        expectScopeFailure(() -> ScriptAssetScope.stage(definition));
        ScriptMod mod = script("asset_success.lua", () -> {
            ScriptAssetScope.stage(definition);
            require(ScriptAssetScope.find(definition.getId()) == null, "Assets stay private during init");
        });
        require(run(mod, false).isEmpty(), "Successful init must publish");
        require(ScriptAssetScope.find(definition.getId()) != null, "Assets publish after init");
        expectScopeFailure(() -> ScriptAssetScope.stage(definition));
        ScriptResourceTracker.unload(mod.sourceFileName);
        require(ScriptAssetScope.find(definition.getId()) == null, "Unload must remove owned assets");
    }

    private static void verifyFailureAndScopeCleanup() {
        AssetDefinition definition = texture("mymod:failed");
        ScriptMod mod = script("asset_failure.lua", () -> {
            ScriptAssetScope.stage(definition);
            throw new LuaError("Expected init failure");
        });
        require(run(mod, false).size() == 1, "Initialization failure must be reported");
        require(ScriptAssetScope.find(definition.getId()) == null, "Failure must discard all staged assets");
        require(LuaScriptRegistry.getCurrentScriptFile() == null, "Failure restores script context");
        expectScopeFailure(() -> ScriptAssetScope.stage(definition));
        ScriptMod corrected = script("asset_corrected.lua", () -> ScriptAssetScope.stage(definition));
        require(run(corrected, false).isEmpty(), "A discarded declaration must not reserve its key");
        ScriptResourceTracker.unload(corrected.sourceFileName);
    }

    private static void verifyReloadAndOwnership() {
        AssetDefinition original = texture("mymod:retained");
        ScriptMod owner = script("asset_owner.lua", () -> ScriptAssetScope.stage(original));
        require(run(owner, false).isEmpty(), "Owner must load");
        ScriptMod intruder = script("asset_intruder.lua", () -> ScriptAssetScope.stage(original));
        require(run(intruder, false).size() == 1, "Another script cannot claim an owned key");
        require(ScriptAssetScope.find(original.getId()).getOwner().equals(owner.sourceFileName),
                "Failed intruder cleanup must preserve the owner");

        NonReloadableScriptRegistry.mark(owner.sourceFileName, "test structural content");
        ScriptResourceTracker.unloadReloadable();
        require(ScriptAssetScope.find(original.getId()) != null, "Retained scripts retain their assets");
        NonReloadableScriptRegistry.unmark(owner.sourceFileName);
        ScriptResourceTracker.unloadReloadable();
        require(ScriptAssetScope.find(original.getId()) == null, "Reloadable cleanup removes assets");

        AssetDefinition reloaded = texture("mymod:reloaded");
        owner.modReload = callback(() -> ScriptAssetScope.stage(reloaded));
        require(run(owner, true).isEmpty(), "modReload participates in the same publication");
        require(ScriptAssetScope.find(original.getId()) != null, "modInit assets publish after reload");
        require(ScriptAssetScope.find(reloaded.getId()) != null, "modReload assets publish together");
        ScriptResourceTracker.unload(owner.sourceFileName);
    }

    private static void verifyReloadFailure() {
        AssetDefinition first = texture("mymod:reload_first");
        AssetDefinition second = texture("mymod:reload_second");
        ScriptMod mod = script("asset_reload_failure.lua", () -> ScriptAssetScope.stage(first));
        mod.modReload = callback(() -> {
            ScriptAssetScope.stage(second);
            throw new LuaError("Expected reload failure");
        });
        require(run(mod, true).size() == 1, "modReload failure must be reported");
        require(ScriptAssetScope.find(first.getId()) == null && ScriptAssetScope.find(second.getId()) == null,
                "modReload failure must discard both initialization and reload declarations");
        expectScopeFailure(() -> ScriptAssetScope.stage(first));
    }

    private static List<String> run(ScriptMod mod, boolean reload) {
        List<String> failures = new ArrayList<>();
        ScriptLifecycleRunner runner = new ScriptLifecycleRunner(new RetainedScriptCatalog(), message -> {
        });
        runner.run(Collections.singletonList(mod), failures, reload);
        return failures;
    }

    private static ScriptMod script(String file, Runnable init) {
        return new ScriptMod(file, Collections.emptyList(), callback(init), file, "", "1.0.0", null);
    }

    private static LuaValue callback(Runnable action) {
        return new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                action.run();
                return NIL;
            }
        };
    }

    private static AssetDefinition texture(String key) {
        return new AssetDefinition(new AssetId(AssetKind.TEXTURE, AssetKey.parse(key)), AssetPath.parse("guard.png"),
                "png");
    }

    private static void expectScopeFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Expected missing asset initialization scope");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
