package betamoon.hooks.live;

import betamoon.BetaMoonClient;
import betamoon.luamodloader.ScriptResourceTracker;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Exercises retained preflight and failed-generation cleanup in the actual
 * running owner lifecycle.
 */
public final class HookLiveClientChecks {
    public static void reloads() throws Exception {
        if (!"passed".equals(System.getProperty("betamoon.hooks.lua"))) {
            throw new AssertionError("Lua hook acceptance did not finish");
        }
        Class<?> fixture = Class.forName("hookfixture.LiveHookTarget", false,
                HookLiveClientChecks.class.getClassLoader());
        if (!fixture.getProtectionDomain().getCodeSource().getLocation().toString()
                .contains("native-hook-fixture.jar")) {
            throw new AssertionError("literal target did not come from the separately installed fixture mod");
        }
        Method sum = fixture.getMethod("sum", int.class, long.class, double.class);
        Path owner = Paths.get(System.getProperty("betamoon.instanceDir"), "betamoon/scripts/native_acceptance");
        Path hooks = owner.resolve("hooks/fixture.lua");
        Path entrypoint = owner.resolve("main.lua");
        byte[] hookBytes = Files.readAllBytes(hooks);
        byte[] mainBytes = Files.readAllBytes(entrypoint);
        long generation = ScriptResourceTracker.ownerGeneration("native_acceptance/main.lua");
        try {
            String extra = "\nr:register({id='unprepared',target=F:method('unprepared','()I'),at='before'},function() end)\n";
            Files.write(hooks,
                    (new String(hookBytes, StandardCharsets.UTF_8) + extra).getBytes(StandardCharsets.UTF_8));
            BetaMoonClient.getInstance().reloadLuaScripts();
            if (ScriptResourceTracker.ownerGeneration("native_acceptance/main.lua") != generation
                    || !Integer.valueOf(28).equals(sum.invoke(null, 1, 2L, 3.0))) {
                throw new AssertionError("incompatible reload discarded the working hook generation");
            }
            Files.write(hooks, hookBytes);
            Files.write(entrypoint,
                    (new String(mainBytes, StandardCharsets.UTF_8)
                            + "\nfunction modInit() error('intentional hook initialization failure') end\n")
                            .getBytes(StandardCharsets.UTF_8));
            BetaMoonClient.getInstance().reloadLuaScripts();
            if (!Integer.valueOf(11).equals(sum.invoke(null, 1, 2L, 3.0))) {
                throw new AssertionError("failed owner activated hooks, or cleanup removed the other owner's hook");
            }
            System.clearProperty("betamoon.hooks.lua");
            System.out.println(
                    "[HOOK-LIVE] retained restart-required generation and discarded failed initialization; partner preserved.");
        } finally {
            Files.write(hooks, hookBytes);
            Files.write(entrypoint, mainBytes);
        }
    }
}
