package betamoon.hooks;

/**
 * JDK-only optional timing. Counts mode starts no clocks and creates no tokens.
 */
final class HookTiming {
    private static volatile Mode mode = new Mode("counts", 0);

    private HookTiming() {
    }

    static boolean configure(String name, int every) {
        if (!("counts".equals(name) || "sampled".equals(name) || "full".equals(name)) || every < 1 || every > 65536) {
            throw new IllegalArgumentException("Hook timing: counts, sampled or full; sample interval 1..65536");
        }
        int interval = name.equals("counts") ? 0 : name.equals("full") ? 1 : every;
        Mode previous = mode;
        if (previous.name.equals(name) && previous.every == interval) {
            return false;
        }
        mode = new Mode(name, interval);
        return true;
    }

    static String name() {
        return mode.name;
    }

    static int every() {
        return mode.every;
    }

    static Token begin(HookDispatcher.Subscription subscription) {
        subscription.attempts++;
        Mode current = mode;
        if (current.every == 0) {
            return null;
        }
        long attempt = ++subscription.timingAttempts;
        if (current.every > 1 && attempt % current.every != 0) {
            return null;
        }
        return new Token(current);
    }

    static void finish(HookDispatcher.Subscription subscription, Token token) {
        if (token == null || token.mode != mode) {
            return;
        }
        long elapsed = System.nanoTime() - token.started;
        subscription.timedCalls++;
        subscription.nanos += elapsed;
        subscription.continuationNanos += token.continuationNanos;
        subscription.ownNanos += Math.max(0, elapsed - token.continuationNanos);
    }

    private static final class Mode {
        private final String name;
        private final int every;
        private Mode(String name, int every) {
            this.name = name;
            this.every = every;
        }
    }

    static final class Token {
        private final Mode mode;
        private final long started = System.nanoTime();
        private long continuationNanos;
        private Token(Mode mode) {
            this.mode = mode;
        }

        long startContinuation() {
            return System.nanoTime();
        }

        void finishContinuation(long started) {
            continuationNanos += System.nanoTime() - started;
        }
    }
}
