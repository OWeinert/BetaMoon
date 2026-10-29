package betamoon.hooks;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Timing modes never change callback execution, return values or continuation
 * semantics.
 */
final class HookTimingContractTest {
    static int verify() throws Exception {
        String type = "fixture.hooks.HookTargetFixture";
        String source = "local r=betamoon.hooks:createRegistry(); local T=betamoon.native:type('" + type + "'); "
                + "for _,at in ipairs({'before','after','around'}) do "
                + "r:register({id=at,target=T:method('longValue','(J)J'),at=at,effects='edit'},function() end) end; "
                + "r:register({id='point',target=T:method('voidValue','()V'),at={kind='field_read'," + "field={class='"
                + type + "',field='bodyCalls',descriptor='I'},ordinal=0,matches={exact=1}}},function() end); "
                + "r:register({id='ctor',target={class='" + type
                + "',constructor='()V'},at='constructed'},function() end)";
        try (HookTestHarness fixture = new HookTestHarness(type, source, bytes -> bytes)) {
            HookDispatcher.configureTiming("counts", 64);
            HookDispatcher.Subscription before = fixture.subscribe("before", call -> {
            });
            HookDispatcher.Subscription after = fixture.subscribe("after", call -> {
            });
            HookDispatcher.Subscription around = fixture.subscribe("around",
                    call -> call.result(call.proceed(new Object[]{call.argument(0)})));
            HookDispatcher.Subscription point = fixture.subscribe("point", call -> {
            });
            HookDispatcher.Subscription ctor = fixture.subscribe("ctor", call -> {
            });
            Method method = fixture.load().getMethod("longValue", long.class);
            for (int index = 0; index < 128; index++) {
                require(method.invoke(null, 42L).equals(42L), "counts return value");
            }
            require(before.calls == 128 && after.calls == 128 && around.calls == 128, "counts callbacks");
            require(before.attempts == 128 && before.nanos == 0 && before.timedCalls == 0 && before.timingAttempts == 0,
                    "counts has no timing");
            HookDispatcher.configureTiming("sampled", 64);
            for (int index = 0; index < 128; index++) {
                method.invoke(null, 42L);
            }
            require(before.timingAttempts == 128 && before.timedCalls == 2 && around.timedCalls == 2,
                    "deterministic per-subscription samples");
            require(around.nanos == around.ownNanos + around.continuationNanos && around.continuationNanos > 0,
                    "around continuation separated from inclusive timing");
            HookDispatcher.configureTiming("sampled", 64);
            require(before.timedCalls == 2, "unchanged mode retains samples");
            HookDispatcher.configureTiming("full", 64);
            require(before.calls == 256 && before.timedCalls == 0, "mode change retains lifetime counts only");
            method.invoke(null, 42L);
            fixture.load().getMethod("voidValue").invoke(null);
            fixture.load().newInstance();
            require(before.timedCalls == 1 && after.timedCalls == 1 && around.timedCalls == 1 && point.timedCalls == 1
                    && ctor.timedCalls == 1, "every envelope timed in full mode");
            require(point.nanos == point.ownNanos && point.continuationNanos == 0, "ordinary timing attribution");
            Map<String, Object> status = fixture.preparation.status().stream()
                    .filter(row -> "HOOK_SUBSCRIPTION".equals(row.get("kind")) && "before".equals(row.get("hook")))
                    .findFirst().orElse(null);
            if (status == null) {
                status = fixture.preparation.status().stream()
                        .filter(row -> "HOOK_SUBSCRIPTION".equals(row.get("kind"))).findFirst().get();
            }
            require("full".equals(status.get("timingMode"))
                    && ((Number) status.get("timingSampleEvery")).intValue() == 1, "detached timing configuration");
            HookTiming.Token previous = HookTiming.begin(before);
            HookDispatcher.configureTiming("counts", 64);
            HookTiming.finish(before, previous);
            require(before.timedCalls == 0 && before.nanos == 0, "in-flight old epoch ignored");
            require(HookTiming.begin(before) == null, "counts creates no timing token");
            try {
                HookDispatcher.configureTiming("unknown", 64);
                throw new AssertionError("invalid mode accepted");
            } catch (IllegalArgumentException expected) {
            }
        } finally {
            HookDispatcher.configureTiming("counts", 64);
        }
        return 15;
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
