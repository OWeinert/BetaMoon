package betamoon.hooks;

import java.lang.instrument.Instrumentation;

/** Simulates another agent defining a target before BetaMoon premain. */
public final class HookEarlyLoadAgent {
    public static void premain(String options, Instrumentation instrumentation) throws Exception {
        Class.forName("fixture.hooks.HookTargetFixture", false, ClassLoader.getSystemClassLoader());
    }
}
