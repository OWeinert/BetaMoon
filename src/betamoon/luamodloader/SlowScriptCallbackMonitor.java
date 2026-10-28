package betamoon.luamodloader;

import betamoon.BetaMoonCommon;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * Rate-limited diagnostics for callbacks routed through BetaMoon dispatchers.
 */
public final class SlowScriptCallbackMonitor {
    private static final long WARNING_INTERVAL_NANOS = 60L * 1000000000L;
    private static final Map<String, Map<String, Sample>> SAMPLES = new HashMap<>();
    private static final Deque<Sample> ORDER = new ArrayDeque<>();
    private static volatile int thresholdMs;

    private SlowScriptCallbackMonitor() {
    }

    public static synchronized void configure(int milliseconds) {
        thresholdMs = Math.max(0, milliseconds);
        SAMPLES.clear();
        ORDER.clear();
    }

    public static long start() {
        return thresholdMs == 0 ? 0L : System.nanoTime();
    }

    public static void record(String owner, String callback, long startedNanos) {
        int threshold = thresholdMs;
        if (threshold == 0 || startedNanos == 0) {
            return;
        }
        long elapsed = System.nanoTime() - startedNanos;
        synchronized (SlowScriptCallbackMonitor.class) {
            Map<String, Sample> callbacks = SAMPLES.get(owner);
            Sample sample = callbacks == null ? null : callbacks.get(callback);
            if (sample == null) {
                if (ORDER.size() >= 4096) {
                    Sample oldest = ORDER.removeFirst();
                    Map<String, Sample> previous = SAMPLES.get(oldest.owner);
                    previous.remove(oldest.callback);
                    if (previous.isEmpty()) {
                        SAMPLES.remove(oldest.owner);
                    }
                    callbacks = SAMPLES.get(owner);
                }
                if (callbacks == null) {
                    callbacks = new HashMap<>();
                    SAMPLES.put(owner, callbacks);
                }
                sample = new Sample(owner, callback);
                callbacks.put(callback, sample);
                ORDER.addLast(sample);
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
                BetaMoonCommon.LOGGER.warning("Slow Lua callback " + sample.label + " took " + (elapsed / 1000000L)
                        + " ms (threshold " + threshold + " ms, at least 3 consecutive slow calls).");
            }
        }
    }

    private static final class Sample {
        private final String owner;
        private final String callback;
        private final String label;
        private int consecutiveSlowCalls;
        private long lastWarningNanos;

        private Sample(String owner, String callback) {
            this.owner = owner;
            this.callback = callback;
            label = owner + " / " + callback;
        }
    }
}
