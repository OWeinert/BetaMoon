package betamoon.luamodloader;

import betamoon.hooks.HookFailure;
import betamoon.hooks.HookPreparation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Real loader preflight/staging without a Java agent, Minecraft, or a normal
 * mod entrypoint.
 */
public final class NativeHooksWithoutAgentTest {
    public static void main(String[] args) throws Exception {
        if (HookPreparation.current() != null) {
            throw new AssertionError("missing-agent acceptance must run without an agent plan");
        }
        Path work = Files.createTempDirectory(Paths.get("build").toAbsolutePath(), "native-n7-no-agent-");
        Path scripts = Files.createDirectories(work.resolve("betamoon/scripts"));
        Path owner = Files.createDirectories(scripts.resolve("optional"));
        Files.write(owner.resolve("betamoon.mod.json"),
                "{\"name\":\"Optional\",\"entrypoint\":\"main.lua\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(owner.resolve("main.lua"),
                "error('must not run during preflight')".getBytes(StandardCharsets.UTF_8));
        Files.write(owner.resolve("betamoon.hooks.json"), "[\"hooks.lua\"]".getBytes(StandardCharsets.UTF_8));
        String code = "local r=betamoon.hooks:createRegistry(); r:register({id='optional',"
                + "target={class='fixture.hooks.ChildHookFixture',method='call',descriptor='(I)I'},at='before',required=%s},"
                + "function() error('must remain inactive') end)";
        Files.write(owner.resolve("hooks.lua"), String.format(code, "false").getBytes(StandardCharsets.UTF_8));
        List<LuaScriptFiles.PreflightFailure> failures = new ArrayList<>();
        List<LuaModSource> sources = new LuaScriptFiles().discover(scripts.toFile(), failures);
        if (!failures.isEmpty() || sources.size() != 1) {
            throw new AssertionError("optional package discovery failed");
        }
        Globals globals = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        module.set("native", new LuaTable());
        globals.set("betamoon", module);
        HookLifecycle.beginAttempt();
        try {
            HookLifecycle.preflight(sources.get(0));
            HookLifecycle.Stage stage = HookLifecycle.stage(sources.get(0), globals);
            stage.publish();
            stage.run();
        } finally {
            HookLifecycle.endAttempt();
        }
        Files.write(owner.resolve("hooks.lua"), String.format(code, "true").getBytes(StandardCharsets.UTF_8));
        HookLifecycle.beginAttempt();
        try {
            HookLifecycle.preflight(sources.get(0));
            throw new AssertionError("required hooks activated without an agent");
        } catch (HookFailure expected) {
            if (!expected.reason.equals("agent_unavailable")) {
                throw new AssertionError("wrong missing-agent reason: " + expected.reason);
            }
        } finally {
            HookLifecycle.endAttempt();
        }
        System.out
                .println("Missing-agent hooks passed: optional hooks inactive; required hooks fail before entrypoint.");
    }
}
