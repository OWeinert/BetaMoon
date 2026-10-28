package betamoon.hooks;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Reached native sites with real Lua counters; excludes HookLifecycle
 * observation frames (measured separately).
 */
final class HookScaleProfile {
    static int verify() throws Exception {
        ThreadMXBean memory = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        memory.setThreadAllocatedMemoryEnabled(true);
        int checks = 0;
        for (int count : new int[]{1, 16, 128, 512}) {
            int perOwner = Math.min(count, 64);
            String source = "local r=betamoon.hooks:createRegistry(); local T=betamoon.native:type('fixture.hooks.HookTargetFixture'); "
                    + "for i=1," + perOwner + " do r:register({id='h'..i,target=T:method('longValue','(J)J'),"
                    + "at='after',priority=i},function() end) end";
            Globals lua = JsePlatform.standardGlobals();
            LuaValue counter = lua.load("local total=0; return function() total=total+1; return total end").call();
            try (HookTestHarness fixture = new HookTestHarness("fixture.hooks.HookTargetFixture", source,
                    bytes -> bytes, (count + 63) / 64)) {
                for (int index = 0; index < count; index++) {
                    int owner = index / 64;
                    fixture.subscribe(owner == 0 ? "operations" : "operations" + owner, "h" + (index % 64 + 1),
                            call -> counter.call());
                }
                Method method = fixture.load().getMethod("longValue", long.class);
                long[] times = new long[600];
                for (int iteration = 0; iteration < 200; iteration++) {
                    method.invoke(null, 17L);
                }
                long thread = Thread.currentThread().getId();
                long allocated = memory.getThreadAllocatedBytes(thread);
                for (int iteration = 0; iteration < times.length; iteration++) {
                    long started = System.nanoTime();
                    if (!method.invoke(null, 17L).equals(17L)) {
                        throw new AssertionError("native value changed");
                    }
                    times[iteration] = System.nanoTime() - started;
                }
                allocated = memory.getThreadAllocatedBytes(thread) - allocated;
                Arrays.sort(times);
                long calls = (long) counter.call().checkdouble();
                if (calls != 800L * count + 1) {
                    throw new AssertionError("scaling profile lost callbacks: " + calls);
                }
                System.out.println("Hook Lua-counter scale subscribers=" + count + ": p50=" + times[300] + "ns p95="
                        + times[570] + "ns p99=" + times[594] + "ns bytes/native-call=" + allocated / times.length
                        + "; local dispatcher plus Lua counter, no native observation facade");
                checks++;
            }
        }
        return checks;
    }
}
