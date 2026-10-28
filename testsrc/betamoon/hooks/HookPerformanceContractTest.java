package betamoon.hooks;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Status snapshots must reuse structure without freezing changing counters or
 * callback order.
 */
final class HookPerformanceContractTest {
    static int verify() throws Exception {
        String source = "local r=betamoon.hooks:createRegistry(); local T=betamoon.native:type('fixture.hooks.HookTargetFixture'); "
                + "for i=1,64 do r:register({id='h'..i,target=T:method('longValue','(J)J'),"
                + "at='after',priority=i},function() end) end";
        try (HookTestHarness fixture = new HookTestHarness("fixture.hooks.HookTargetFixture", source, bytes -> bytes)) {
            for (int index = 1; index <= 64; index++) {
                fixture.subscribe("h" + index, call -> {
                });
            }
            List<Map<String, Object>> before = fixture.preparation.status();
            Map<String, Object> site = before.stream().filter(row -> "HOOK_SITE".equals(row.get("kind"))).findFirst()
                    .get();
            Method target = fixture.load().getMethod("longValue", long.class);
            for (int iteration = 0; iteration < 100; iteration++) {
                if (!target.invoke(null, 17L).equals(17L)) {
                    throw new AssertionError("observers changed the native value");
                }
            }
            List<Map<String, Object>> after = fixture.preparation.status();
            if (after.stream().filter(row -> "HOOK_SITE".equals(row.get("kind"))).findFirst().get() != site) {
                throw new AssertionError("unchanged hook structure was rebuilt");
            }
            int subscriptions = 0;
            for (Map<String, Object> row : after) {
                if ("HOOK_SUBSCRIPTION".equals(row.get("kind"))) {
                    if (((Number) row.get("calls")).longValue() != 100
                            || ((Number) row.get("order")).intValue() != 63 - subscriptions++) {
                        throw new AssertionError("fresh counters or reverse-return order became stale");
                    }
                }
            }
            if (subscriptions != 64) {
                throw new AssertionError("subscription status disappeared");
            }
            fixture.preparation.diagnose("operations", "status", "fixture_change", "structural invalidation");
            if (fixture.preparation.status().stream().filter(row -> "HOOK_SITE".equals(row.get("kind"))).findFirst()
                    .get() == site) {
                throw new AssertionError("status revision did not invalidate cached structure");
            }
        }
        return 4;
    }
}
