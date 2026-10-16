package betamoon.client.tick;

import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.instrumentation.diagnostics.HookDiagnostic;
import betamoon.instrumentation.diagnostics.HookStatus;

/**
 * Runs in a separate VM with the packaged agent and the named Minecraft client
 * on its classpath.
 */
public final class TickAgentStartupTest {
    public static void main(String[] arguments) throws Exception {
        Class.forName("net.minecraft.client.Minecraft", false, TickAgentStartupTest.class.getClassLoader());
        int applied = 0;
        for (HookDiagnostic diagnostic : AgentRuntime.getReport().snapshot()) {
            if (diagnostic.getHookId().startsWith("betamoon:tick_events")) {
                if (diagnostic.getStatus() != HookStatus.APPLIED) {
                    throw new AssertionError("Tick hook was not installed during class loading: "
                            + diagnostic.getHookId() + "=" + diagnostic.getStatus());
                }
                applied++;
            }
        }
        if (applied != 2) {
            throw new AssertionError("Expected both client and world tick bridges, found " + applied);
        }
        System.out.println("Packaged tick agent installed both native boundaries before Minecraft class definition.");
    }
}
