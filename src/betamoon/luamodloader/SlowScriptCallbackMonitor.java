package betamoon.luamodloader;

import betamoon.BetaMoonCommon;
import java.util.HashMap;
import java.util.Map;

/** Rate-limited diagnostics for callbacks routed through BetaMoon dispatchers. */
public final class SlowScriptCallbackMonitor {
    private static final long WARNING_INTERVAL_NANOS = 60L * 1000000000L;
    private static final Map<String, Sample> SAMPLES = new HashMap<String, Sample>();
    private static volatile int thresholdMs;

    private SlowScriptCallbackMonitor() {
    }

    public static synchronized void configure(int milliseconds) {
        thresholdMs = Math.max(0, milliseconds);
        SAMPLES.clear();
    }

    public static void record(String owner, String callback, long startedNanos) {
        int threshold = thresholdMs;
        if (threshold == 0) {
            return;
        }
        long elapsed = System.nanoTime() - startedNanos;
        String key = owner + " / " + callback;
        synchronized (SlowScriptCallbackMonitor.class) {
            Sample sample = SAMPLES.get(key);
            if (sample == null) {
                if (SAMPLES.size() >= 4096) {
                    SAMPLES.clear();
                }
                sample = new Sample();
                SAMPLES.put(key, sample);
            }
            if (elapsed < threshold * 1000000L) {
                sample.consecutiveSlowCalls = 0;
                return;
            }
            sample.consecutiveSlowCalls++;
            long now = System.nanoTime();
            if (sample.consecutiveSlowCalls >= 3
                    && (sample.lastWarningNanos == 0L || now - sample.lastWarningNanos >= WARNING_INTERVAL_NANOS)) {
                sample.lastWarningNanos = now;
                BetaMoonCommon.LOGGER.warning("Slow Lua callback " + key + " took " + (elapsed / 1000000L)
                        + " ms (threshold " + threshold + " ms, at least 3 consecutive slow calls).");
            }
        }
    }

    private static final class Sample {
        private int consecutiveSlowCalls;
        private long lastWarningNanos;
    }
}
