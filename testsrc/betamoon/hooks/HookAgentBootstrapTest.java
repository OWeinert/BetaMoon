package betamoon.hooks;

import betamoon.instrumentation.agent.AgentLoadedTypes;
import betamoon.instrumentation.agent.BetaMoonAgent;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs with only the actual agent JAR and test classes, without a game or
 * separate dependencies.
 */
public final class HookAgentBootstrapTest {
    public static void main(String[] args) throws Exception {
        if (!BetaMoonAgent.isRegistered() || HookPreparation.current() == null) {
            throw new AssertionError("packaged premain preparation unavailable");
        }
        List<String> forbidden = new ArrayList<>();
        AgentLoadedTypes.visit(type -> {
            String name = type.getName();
            if (name.startsWith("net.minecraft.") || name.startsWith("betamoon.luaapi.")
                    || name.equals("betamoon.luamodloader.HookLifecycle")
                    || name.equals("betamoon.interop.discovery.NativeDiscoveryRuntime")) {
                forbidden.add(name);
            }
        });
        if (!forbidden.isEmpty()) {
            throw new AssertionError("premain loaded normal game/API services: " + forbidden);
        }
        if (System.getProperty("betamoon.hookFixture.initialized") != null) {
            throw new AssertionError("premain initialized the target");
        }
        if (args.length == 1 && args[0].equals("early")) {
            if (HookPreparation.current().sites("fixture/hooks/HookTargetFixture").stream()
                    .anyMatch(site -> !site.status.equals("target_loaded_too_early"))) {
                throw new AssertionError("another agent's early definition was not diagnosed");
            }
            System.out.println("Early target definition rejected without initialization or retransformation.");
            return;
        }
        Class<?> target = Class.forName("fixture.hooks.HookTargetFixture", false,
                HookAgentBootstrapTest.class.getClassLoader());
        if (System.getProperty("betamoon.hookFixture.initialized") != null) {
            throw new AssertionError("target lookup initialized the target");
        }
        if (HookPreparation.current().sites("fixture/hooks/HookTargetFixture").stream()
                .anyMatch(site -> !site.status.equals("applied"))) {
            throw new AssertionError(
                    "packaged agent did not install the dormant envelope: " + HookPreparation.current().status());
        }
        Object receiver = target.newInstance();
        Object result = target.getMethod("calculate", int.class, long.class, double.class, Object.class)
                .invoke(receiver, 1, 2L, 3.0, null);
        if (!Integer.valueOf(3).equals(result)) {
            throw new AssertionError("dormant packaged method changed native behavior");
        }
        System.out.println(
                "Packaged hook premain passed: ASM/LuaJ bundled; no Minecraft, ordinary Lua API, entrypoint or callback execution.");
        verifyIsolatedLoader();
        verifyChildRuntime();
    }

    private static void verifyIsolatedLoader() throws Exception {
        URL tests = HookAgentBootstrapTest.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader isolated = new URLClassLoader(new URL[]{tests}, null)) {
            Class<?> target = Class.forName("fixture.hooks.InvisibleHookFixture", false, isolated);
            if (!target.getMethod("value").invoke(null).equals(9)
                    || HookPreparation.current().sites("fixture/hooks/InvisibleHookFixture").stream()
                            .anyMatch(site -> !site.status.equals("dispatcher_not_visible"))) {
                throw new AssertionError("isolated loader must keep native code and diagnose unavailable dispatcher");
            }
        }
    }

    private static void verifyChildRuntime() throws Exception {
        URL[] roots = {BetaMoonAgent.class.getProtectionDomain().getCodeSource().getLocation(),
                HookAgentBootstrapTest.class.getProtectionDomain().getCodeSource().getLocation()};
        try (URLClassLoader child = new URLClassLoader(roots, HookAgentBootstrapTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    if (name.startsWith("betamoon.") || name.startsWith("org.luaj.")
                            || name.startsWith("fixture.hooks.")) {
                        Class<?> found = findLoadedClass(name);
                        if (found == null) {
                            found = findClass(name);
                        }
                        if (resolve) {
                            resolveClass(found);
                        }
                        return found;
                    }
                    return super.loadClass(name, resolve);
                }
            }
        }) {
            Class<?> runtime = Class.forName("betamoon.hooks.HookChildRuntime", true, child);
            runtime.getMethod("verify").invoke(null);
        }
    }
}
